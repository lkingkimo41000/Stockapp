package com.stock.ledger;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.*;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_REQ = 7;

    private static final String HOOK =
        "(function(){if(window.__hooked)return;window.__hooked=true;" +
        "document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a[download]');" +
        "if(!a||!a.href||a.href.indexOf('blob:')!==0)return;e.preventDefault();e.stopPropagation();" +
        "var n=a.getAttribute('download')||'file';" +
        "fetch(a.href).then(function(r){return r.blob()}).then(function(b){var fr=new FileReader();" +
        "fr.onloadend=function(){Android.saveFile(n,b.type||'application/octet-stream',String(fr.result).split(',')[1]);};" +
        "fr.readAsDataURL(b);});},true);})();";

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        web = new WebView(this);
        setContentView(web);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setDatabaseEnabled(true);
        st.setAllowFileAccess(true);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void saveFile(String name, String mime, String b64) { save(name, mime, b64); }
        }, "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { v.evaluateJavascript(HOOK, null); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try { startActivityForResult(p.createIntent(), FILE_REQ); }
                catch (Exception e) { filePathCallback = null; return false; }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    private void save(final String name, final String mime, final String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                OutputStream o = getContentResolver().openOutputStream(uri);
                o.write(data); o.close();
            } else {
                File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                d.mkdirs();
                FileOutputStream o = new FileOutputStream(new File(d, name));
                o.write(data); o.close();
            }
            toast("تم الحفظ في مجلد التنزيلات: " + name);
        } catch (Exception e) { toast("تعذر حفظ الملف"); }
    }

    private void toast(final String m) {
        runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show(); } });
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        if (req == FILE_REQ && filePathCallback != null) {
            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, d));
            filePathCallback = null;
        }
    }

    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
