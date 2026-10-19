package com.faculink.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String HOME = "https://faculink.xo.je/";
    private static final String HOST = "faculink.xo.je";
    private static final int REQ_FILE = 1;
    private static final int REQ_STORAGE = 2;

    private static final String OFFLINE_HTML =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>"
            + "<body style='font-family:sans-serif;text-align:center;padding:18vh 24px;color:#102A43'>"
            + "<h2>Connexion impossible</h2>"
            + "<p>V\u00e9rifie ta connexion Internet puis r\u00e9essaie.</p>"
            + "<p><a href='" + HOME + "' style='display:inline-block;background:#2563EB;color:#fff;"
            + "padding:12px 24px;border-radius:10px;text-decoration:none'>R\u00e9essayer</a></p>"
            + "</body></html>";

    private WebView web;
    private ProgressBar bar;
    private ValueCallback<Uri[]> filePathCallback;
    private String[] pendingDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(0xFF2563EB));
        bar.setVisibility(View.GONE);
        root.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(false);

        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                boolean isWeb = "http".equals(scheme) || "https".equals(scheme);
                if (isWeb && HOST.equalsIgnoreCase(u.getHost())) return false;
                openExternal(u);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                bar.setProgress(0);
                bar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                bar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    view.loadDataWithBaseURL(null, OFFLINE_HTML, "text/html", "utf-8", null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                bar.setProgress(newProgress);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                startDownload(url, userAgent, contentDisposition, mimeType));

        if (savedInstanceState == null) web.loadUrl(HOME);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private String fileName(String url, String contentDisposition, String mimeType) {
        if (contentDisposition != null) {
            Matcher m = Pattern.compile("filename\\*=UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE)
                    .matcher(contentDisposition);
            if (m.find()) {
                try {
                    String n = URLDecoder.decode(m.group(1), "UTF-8").replace("/", "_");
                    if (!n.isEmpty()) return n;
                } catch (Exception ignored) {
                }
            }
        }
        return URLUtil.guessFileName(url, contentDisposition, mimeType);
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = new String[]{url, userAgent, contentDisposition, mimeType};
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        String name = fileName(url, contentDisposition, mimeType);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null) req.addRequestHeader("Cookie", cookie);
        if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
        if (mimeType != null) req.setMimeType(mimeType);
        req.setTitle(name);
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
        try {
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
            Toast.makeText(this, "T\u00e9l\u00e9chargement en cours\u2026", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "T\u00e9l\u00e9chargement impossible.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && pendingDownload != null) {
            String[] p = pendingDownload;
            pendingDownload = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDownload(p[0], p[1], p[2], p[3]);
            } else {
                Toast.makeText(this, "Autorisation de stockage refus\u00e9e.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && filePathCallback != null) {
            filePathCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
