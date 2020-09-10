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

class SplashSlideLoader {
  companion object {
    private val logger = logger<SplashSlideLoader>()
  }

  private val executor by lazy { AppExecutorUtil.createBoundedApplicationPoolExecutor("SplashSlideWriter", 1) }

  fun loadImage(url: String, cacheAsync: Boolean = true): Image? = logger.runAndLogException {
    val resourceUrl = Splash::class.java.getResource(url) ?: return null
    if (!JreHiDpiUtil.isJreHiDPIEnabled()) return ImageIO.read(resourceUrl)

    val scale = JBUIScale.sysScale()
    val name = FileUtilRt.getNameWithoutExtension(resourceUrl.path)
    val extension = FileUtilRt.getExtension(resourceUrl.path)
    val file = File("$name${scaleToString(scale)}.$extension")

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
    try {
      if (file.createNewFile())
        ImageIO.write(ImageUtil.toBufferedImage(image), file.extension, file)
    } catch (e: Throwable) {
      logger.warn("Cannot save scaled slide. Message: ${e.message}")
    }
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
      2f -> "@2x"
      1f -> ""
      else -> "@${String.format("%.2f", scale)}x"
    }
  }
}