package com.intellij.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ImageLoader
import com.intellij.util.RetinaImage
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.ImageUtil
import java.awt.Image
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.round

class SplashSlideLoader {
  private val logger = logger<SplashSlideLoader>()

  private val executor by lazy { AppExecutorUtil.createBoundedApplicationPoolExecutor("SplashSlideWriter", 1) }

  fun loadImage(url: String, cacheAsync: Boolean = true): Image? = logger.runAndLogException {
    val resourceUrl = Splash::class.java.getResource(url) ?: return null
    if (!JreHiDpiUtil.isJreHiDPIEnabled()) return ImageIO.read(resourceUrl)

    val scale = JBUIScale.sysScale()
    val name = FileUtilRt.getNameWithoutExtension(resourceUrl.path)
    val extension = FileUtilRt.getExtension(resourceUrl.path)
    val file = File("$name@${scaleToString(scale)}x.$extension")

    // fast
    val image = readImageSafe(file, scale.toDouble())
    if (image != null) return image

    // slow
    val scaledImage = ImageLoader.loadFromUrl(
      url,
      Splash::class.java,
      ImageLoader.ALLOW_FLOAT_SCALING,
      null,
      ScaleContext.create()
    ) ?: return null

    saveImage(file, scaledImage, cacheAsync)
    return scaledImage
  }

  private fun saveImage(file: File, image: Image, async: Boolean = true) {
    if (async) executor.execute { saveImage(file, image) }
    else saveImage(file, image)
  }

  private fun saveImage(file: File, image: Image) {
    if (file.createNewFile())
      ImageIO.write(ImageUtil.toBufferedImage(image), file.extension, file)
  }

  private fun readImageSafe(file: File, scale: Double): Image? {
    return try {
      val image = ImageIO.read(file)
      RetinaImage.createFrom(image, scale, ImageLoader.ourComponent)
    }
    catch (e: Throwable) {
      null
    }
  }

  private fun scaleToString(scale: Float): String {
    return when (scale) {
        2f -> "2"
        1f -> ""
      else -> (round(scale * 100) / 100).toString()
    }
  }

}