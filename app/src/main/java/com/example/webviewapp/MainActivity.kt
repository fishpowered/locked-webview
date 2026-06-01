package com.example.webviewapp

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.webviewapp.ui.theme.WebviewAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebviewAppTheme {
                LockedWebView(initialUrl = "go.nepton.com")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LockedWebView(initialUrl: String) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var showAllowedSites by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    val allowedDomains = listOf(
        "example.com",
        "accounts.google.com",
        "login.microsoftonline.com",
        "login.live.com",
    )

    fun isDomainAllowed(url: String?): Boolean {
        if (url == null) return false

        val uri = Uri.parse(url)

        val host = uri.host?.removePrefix("www.") ?: return false
        val path = uri.path ?: ""

        return allowedDomains.any { rule ->
            val normalizedRule = rule.removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")

            val parts = normalizedRule.split("/", limit = 2)

            val ruleHost = parts[0]
            val rulePath = if (parts.size > 1) "/${parts[1]}" else null

            // host must match (or subdomain match if you want it)
            val hostMatches = host == ruleHost || host.endsWith(".$ruleHost")

            if (!hostMatches) return@any false

            // no path rule → allow whole domain
            if (rulePath == null) return@any true

            // path must match prefix
            path.startsWith(rulePath)
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(
                        onClick = { webView?.reload() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(
                        onClick = { showAllowedSites = true }
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Allowed Sites")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        // cookieManager.setAcceptThirdPartyCookies(webView, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.displayZoomControls = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.userAgentString = WebSettings.getDefaultUserAgent(context)
                        // settings.allowContentAccess = true
                        // settings.allowFileAccessFromFileURLs = true
                        // settings.allowUniversalAccessFromFileURLs = true

                        // settings.isAlgorithmicDarkeningAllowed todo test
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString()
                                return !isDomainAllowed(url)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                progress = 0
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                return true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                callback: ValueCallback<Array<Uri>>?,
                                params: FileChooserParams?
                            ): Boolean {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = callback
                                filePickerLauncher.launch("*/*")
                                return true
                            }
                        }

                        loadUrl(initialUrl)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    webView = it
                }
            )

            if (progress < 100) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showAllowedSites) {
        AlertDialog(
            onDismissRequest = { showAllowedSites = false },
            title = { Text("Allowed Sites") },
            text = {
                LazyColumn {
                    items(allowedDomains) { domain ->
                        TextButton(
                            onClick = {
                                webView?.loadUrl("https://$domain")
                                showAllowedSites = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(domain)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllowedSites = false }) {
                    Text("Close")
                }
            }
        )
    }
}
