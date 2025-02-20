package com.example.prediction

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.prediction.databinding.ActivityMainBinding
import com.example.prediction.ml.MushroomModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var buttonCapture: Button
    private lateinit var buttonLoad: Button
    private lateinit var tvOutput: TextView
    private val GALLERY_REQUEST_CODE = 123

    private val THRESHOLD = 0.4f
    @SuppressLint("IntentReset")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageView = binding.imageView
        buttonCapture = binding.btnCaptureImage
        tvOutput = binding.tvOutput
        buttonLoad = binding.btnLoadImage

        buttonCapture.setOnClickListener{
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            {
                takePicturePreview.launch(null)
            }
            else{
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }

        buttonLoad.setOnClickListener{
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED){
                val intent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeType = arrayOf("image/jpeg","image/png","image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES,mimeType)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onResult.launch(intent)
            }else{
                requestPermission.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        }

    }

    private val requestPermissionLaunch = registerForActivityResult(ActivityResultContracts.RequestPermission()){isGranted:Boolean ->
        if (isGranted){
            AlertDialog.Builder(this).setTitle("Download Image?")
                .setMessage("Do you want to download this image to your device?")
                .setPositiveButton("Yes"){_, _ ->
                    val drawable:BitmapDrawable = imageView.drawable as BitmapDrawable
                    val bitmap = drawable.bitmap
                    downloadImage(bitmap)
                }
                .setNegativeButton("No"){dialog, _ ->
                    dialog.dismiss()
                }.show()
        }else{
            Toast.makeText(this,"Please give permission to download image", Toast.LENGTH_LONG).show()
        }
    }

    // Fun that takes a bitmap and store
    private fun downloadImage(mBitmap: Bitmap):Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"Mushroom_Images" + System.currentTimeMillis()/1000)
            put(MediaStore.Images.Media.MIME_TYPE,"Image/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (uri != null){
            contentResolver.insert(uri, contentValues)?.also {
                contentResolver.openOutputStream(it).use {outputStream ->
                    if (!mBitmap.compress(Bitmap.CompressFormat.PNG,100, outputStream!!)){
                        throw  IOException("Couldn't save the bitmap")
                    }else{
                        Toast.makeText(applicationContext,"Image Saved", Toast.LENGTH_LONG).show()
                    }
                }
                return it
            }
        }
        return null
    }

    //Request Camera permission
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if (granted){
            takePicturePreview.launch(null)
        }else{
            Toast.makeText(this, "Permission Denied!!! Try again",Toast.LENGTH_SHORT).show()
        }
    }

    //Active Camera And Take a shot
    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->
        if (bitmap!=null){
            imageView.setImageBitmap(bitmap)
            outputGenerator(bitmap)
        }
    }

    //take image from storage
    private val onResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        Log.i("TAG","This is the result: ${result.data} ${result.resultCode}")
        onResultReceived(GALLERY_REQUEST_CODE,result)
    }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?){
        when(requestCode){
            GALLERY_REQUEST_CODE->{
                if (result?.resultCode == Activity.RESULT_OK){
                    result.data?.data?.let{uri ->
                        Log.i("TAG","onResultReceived: $uri")
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                }else{
                    Log.e("Tag","onActivityResult: error in selecting image")
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun outputGenerator(bitmap: Bitmap) {
        // Tensorflow lite model variable
        val mushroomModel = MushroomModel.newInstance(this)

        // Converting bitmap into tensor flow image
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val tfImage = TensorImage.fromBitmap(newBitmap)

        // Runs model inference and gets result.
        val outputs = mushroomModel.process(tfImage).probabilityAsCategoryList.apply {
            sortByDescending { it.score }
        }

        // Memastikan ada setidaknya satu output
        if (outputs.isNotEmpty()) {
            // Mengambil output dengan probabilitas tertinggi
            val firstOutput = outputs[0]

            // Memeriksa jika probabilitas lebih besar dari threshold
            if (firstOutput.score >= THRESHOLD) {
                // Menetapkan teks output
                tvOutput.text = firstOutput.label
                Log.i("TAG", "outputGenerator: $firstOutput")
            } else {
                tvOutput.text = "Object not recognized"
                Log.i("TAG", "outputGenerator: Object not recognized")
            }
        } else {
            tvOutput.text = "No output from model"
            Log.e("TAG", "outputGenerator: No output from model")
        }

    }
}