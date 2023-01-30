package com.udemy.kidsdrawing

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null

    private var mImageButtonCurrentPaint: ImageButton? = null

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val imageBackground: ImageView = findViewById(R.id.iv_background)
                imageBackground.setImageURI(result.data?.data)
            }
        }

    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if (isGranted) {
                    Toast.makeText(
                        applicationContext,
                        "Permission granted now you can read the storage files",
                        Toast.LENGTH_SHORT
                    ).show()

                    val pickIntent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if (permissionName == android.Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(
                            applicationContext,
                            "Oops you just denied the permission",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        val brush: ImageButton = findViewById(R.id.ib_brush)
        brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val undo: ImageButton = findViewById(R.id.ib_undo)
        undo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val save: ImageButton = findViewById(R.id.ib_save)
        save.setOnClickListener {
            if (isReadStorageAllowed()) {
                lifecycleScope.launch {
                    val flDrawingView: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }

        val gallery: ImageButton = findViewById(R.id.ib_gallery)
        gallery.setOnClickListener {
            requestStoragePermission()
        }
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")

        val smallBrush: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBrush.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }

        val mediumBrush: ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBrush.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBrush: ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBrush.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view !== mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(view.context, R.drawable.pallet_pressed)
            )

            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(view.context, R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint = view
        }
    }

    private fun isReadStorageAllowed(): Boolean {
        val result = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            showRationaleDialog(
                "Kids Drawing", "Kids Drawing" + "need to Access Your External Storage"
            )
        } else {
            requestPermission.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun showRationaleDialog(title: String, message: String) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message).setPositiveButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnBitmap)
        val backgroundDrawable = view.background

        if (backgroundDrawable != null) backgroundDrawable.draw(canvas)
        else canvas.drawColor(Color.WHITE)

        view.draw(canvas)
        return returnBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f =
                        File(externalCacheDir?.absoluteFile.toString() + File.separator + "Kids Drawing" + System.currentTimeMillis() / 1000 + ".png")
                    val fo = FileOutputStream(f)

                    fo.write(bytes.toByteArray())
                    fo.close()
                    result = f.absolutePath

                    runOnUiThread {
                        if (result.isNotEmpty()) {
                            Toast.makeText(
                                applicationContext,
                                "File saved successfully :$result",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                applicationContext,
                                "Something went wrong while saving the file",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

}