// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ImageLoader.Dimension2DDouble
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import javax.imageio.ImageIO

private const val MAX_IMAGE_SIZE = 1024L * 1024L
private const val headerSize = 2 * Long.SIZE_BYTES

object SVGLoaderCache : SVGLoaderCacheBasics() {
  override val cachesHome: File
    get() = File(PathManager.getSystemPath(), "icons")

  override fun forkIOTask(action: () -> Unit) {
    AppExecutorUtil.getAppExecutorService().submit {
      action()
    }
  }
}

abstract class SVGLoaderCacheBasics {
  protected abstract val cachesHome: File
  protected abstract fun forkIOTask(action: () -> Unit)

  private fun cacheFile(theme: String, url: URL, scale: Double): File {
    val d = MessageDigest.getInstance("SHA1")
    //caches version
    d.update(0x0)
    d.update(theme.toByteArray())
    d.update(scale.toString().toByteArray())
    d.update(url.toString().toByteArray())
    d.update(url.toString().toByteArray())

    val hex = Base64.getEncoder().encodeToString(d.digest()).replace("=", "").replace("/", "ZZ").replace("+", "PP")
    return File(cachesHome, "$hex.bin")
  }

  private fun readDouble(b: ByteArray, off: Int): Double {
    var l = 0L
    repeat(Long.SIZE_BYTES) {
      l += (b[off + it].toLong() shl Byte.SIZE_BITS * it)
    }
    return Double.fromBits(l)
  }


  private fun writeDouble(d: Double, dest: ByteArray, off: Int) {
    val l = d.toBits()
    repeat(Long.SIZE_BYTES) {
      dest[off + it] = ((l shr Byte.SIZE_BITS * it) and 0xff).toByte()
    }
  }

  @Throws(IOException::class)
  fun loadFromCache(theme: String, url: URL, scale: Double, docSize: Dimension2DDouble?  /*OUT*/): BufferedImage? {
    val file = cacheFile(theme, url, scale)
    if (!file.isFile) return null

    //let's avoid OOM if an image is too big
    if (file.length() > MAX_IMAGE_SIZE) {
      forkIOTask {
        FileUtil.delete(file)
      }
      return null
    }

    try {
      val data = file.readBytes()
      val w = readDouble(data, 0)
      val h = readDouble(data, Long.SIZE_BYTES)

      val image = ImageIO.read(ByteArrayInputStream(data, headerSize, data.size - headerSize))
      docSize?.setSize(w, h)
      return image
    }
    catch (e: Exception) {
      forkIOTask {
        FileUtil.delete(file)
      }
      //it is OK if we failed to load an icon
      return null
    }
  }

  fun storeLoadedImage(theme: String, url: URL, scale: Double, image: RenderedImage, size: Dimension2DDouble) = forkIOTask {
    val file = cacheFile(theme, url, scale)

    val header = ByteArray(headerSize)
    writeDouble(size.width, header, 0)
    writeDouble(size.height, header, Long.SIZE_BYTES)

    val data = ByteArrayOutputStream().use { out ->
      ImageIO.write(image, "png", out)
      out.toByteArray()
    }

    file.parentFile?.mkdirs()
    file.writeBytes(header + data)
  }
}
