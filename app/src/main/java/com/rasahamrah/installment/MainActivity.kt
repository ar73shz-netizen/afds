package com.rasahamrah.installment

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingImage: ByteArray? = null

    /** window.AndroidSave.saveImage(dataUrl) — ذخیره عکس در گالری */
    inner class SaveBridge {
        @JavascriptInterface
        fun saveImage(dataUrl: String) {
            val bytes = decodeDataUrl(dataUrl)
            runOnUiThread {
                if (bytes == null) toast("خطا در ذخیره تصویر") else requestSave(bytes)
            }
        }
    }

    /** window.AndroidShare.shareText(text) / shareImage(dataUrl) — منوی اشتراک‌گذاری اندروید */
    inner class ShareBridge {
        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, "اشتراک‌گذاری محاسبه"))
            }
        }

        @JavascriptInterface
        fun shareImage(dataUrl: String) {
            val bytes = decodeDataUrl(dataUrl)
            runOnUiThread {
                if (bytes == null) {
                    toast("خطا در اشتراک‌گذاری تصویر")
                    return@runOnUiThread
                }
                try {
                    val dir = File(cacheDir, "shared").apply { mkdirs() }
                    val file = File(dir, "RasaHamrah_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { it.write(bytes) }
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity, "$packageName.fileprovider", file
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, "اشتراک‌گذاری تصویر"))
                } catch (e: Exception) {
                    toast("خطا در اشتراک‌گذاری تصویر")
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.addJavascriptInterface(SaveBridge(), "AndroidSave")
        webView.addJavascriptInterface(ShareBridge(), "AndroidShare")
        webView.webViewClient = WebViewClient()

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun decodeDataUrl(dataUrl: String): ByteArray? = try {
        val b64 = if (dataUrl.contains("base64,")) dataUrl.substringAfter("base64,") else dataUrl
        Base64.decode(b64, Base64.DEFAULT)
    } catch (e: Exception) {
        null
    }

    private fun requestSave(bytes: ByteArray) {
        // Android 9 و قدیمی‌تر به اجازهٔ حافظه نیاز دارند؛ Android 10+ نه.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingImage = bytes
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001
            )
            return
        }
        saveToGallery(bytes)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val bytes = pendingImage
            pendingImage = null
            if (bytes != null && grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                saveToGallery(bytes)
            } else {
                toast("برای ذخیره عکس، دسترسی به حافظه لازم است")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToGallery(bytes: ByteArray) {
        val name = "RasaHamrah_${System.currentTimeMillis()}.png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/RasaHamrah"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("insert failed")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("no output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "RasaHamrah"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                FileOutputStream(file).use { it.write(bytes) }
                MediaScannerConnection.scanFile(
                    this, arrayOf(file.absolutePath), arrayOf("image/png"), null
                )
            }
            toast("تصویر در گالری ذخیره شد")
        } catch (e: Exception) {
            toast("خطا در ذخیره تصویر")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
