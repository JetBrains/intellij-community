package com.intellij.ui

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ImageLoader
import com.intellij.util.RetinaImage
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.ImageUtil
import org.jetbrains.annotations.Nullable
import java.awt.Image
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.imageio.ImageIO

class SplashSlideLoader {
  companion object {
    private val logger = logger<SplashSlideLoader>()
  }

  private val executor by lazy { AppExecutorUtil.createBoundedApplicationPoolExecutor("SplashSlideWriter", 1) }
  private val cacheHome = Paths.get(PathManager.getSystemPath(), "splashSlides")

  fun loadImage(url: String, cacheAsync: Boolean = true): Image? = runSafe {
    val scale = JBUIScale.sysScale().toDouble()
    if (!JreHiDpiUtil.isJreHiDPIEnabled() || scale == 1.0) {
      return@runSafe ImageIO.read(Splash::class.java.getResourceAsStream(url)).toRetina(scale)
    }

    val extension = FileUtilRt.getExtension(url)
    val url2x = "${FileUtilRt.getNameWithoutExtension(url)}@2x.$extension"
    val stream2x = Splash::class.java.getResourceAsStream(url2x) ?: return@runSafe null

    if (scale == 2.0) return@runSafe ImageIO.read(stream2x).toRetina(scale)

    val bytes = FileUtilRt.loadBytes(stream2x)
    val cachedImage = loadFromCache(bytes, scale, extension)
    if (cachedImage != null) return@runSafe cachedImage

    // slow
    val scaledImage = loadSlow(url) ?: return null

    saveImage(bytes, scaledImage, scale, extension, cacheAsync)
    return scaledImage
  } ?: loadSlow(url)

  private fun loadSlow(url: String): @Nullable Image? {
    return ImageLoader.loadFromUrl(
      url,
      Splash::class.java,
      ImageLoader.ALLOW_FLOAT_SCALING,
      null,
      ScaleContext.create()
    )
  }

  private fun saveImage(imageBytes: ByteArray, image: Image, scale: Double, extension: String, async: Boolean) {
    if (async) executor.execute { saveImage(imageBytes, scale, extension, image) }
    saveImage(imageBytes, scale, extension, image)
  }

  private fun saveImage(imageBytes: ByteArray, scale: Double, extension: String, image: Image) {
    try {
      val file: Path = getCacheFile(imageBytes, scale, extension)

      val tmp = file.resolve(file.toString() + ".tmp" + System.currentTimeMillis())
      val tmpFile = tmp.toFile()

      if (!tmpFile.createNewFile()) return
      try {

        ImageIO.write(ImageUtil.toBufferedImage(image), extension, tmpFile)

        try {
          Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
          Files.move(tmp, file)
        }
      } finally {
        tmpFile.delete()
      }
    } catch (e: Throwable) {
      logger.warn("Failed to save image. ${e.message}")
    }
  }

  private inline fun <T> runSafe(action: () -> T): T? {
    return try {
      action()
    } catch (e: Throwable) {
      logger.warn("Failed to load image: ${e.message}", e)
      null
    }
  }

  private fun loadFromCache(
    imageBytes: ByteArray,
    scale: Double,
    extension: String
  ): Image? {
    val file: Path = getCacheFile(imageBytes, scale, extension)
    val fileAttributes: BasicFileAttributes
    fileAttributes = try {
      Files.readAttributes(file, BasicFileAttributes::class.java)
    } catch (ignore: IOException) {
      return null
    }
    if (!fileAttributes.isRegularFile) {
      return null
    }

    return ImageIO.read(file.toFile()).toRetina(scale)
  }

  private fun getCacheFile(imageBytes: ByteArray, scale: Double, extension: String): Path {
    return try {
      val d = MessageDigest.getInstance("SHA-256")
      //caches version
      d.update(imageBytes)
      val hex = StringUtil.toHexString(d.digest())
      cacheHome.resolve("$hex.x$scale.$extension")
    } catch (e: NoSuchAlgorithmException) {
      throw RuntimeException("SHA1 is not supported!", e)
    }
  }

  private fun Image.toRetina(scale: Double) = RetinaImage.createFrom(this, scale, ImageLoader.ourComponent)
}