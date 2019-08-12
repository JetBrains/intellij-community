// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ImageLoader.Dimension2DDouble
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.UnsyncByteArrayInputStream
import com.intellij.util.io.UnsyncByteArrayOutputStream
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest

private const val MAX_IMAGE_SIZE = 16 * 1024L * 1024L
private val imagePixelFormat = BufferedImage.TYPE_INT_ARGB

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
    //TODO: include IntelliJ version (or JAR files signature)
    val d = MessageDigest.getInstance("SHA1")
    //caches version
    d.update(0x0)
    d.update(theme.toByteArray())
    d.update(scale.toString().toByteArray())
    d.update(url.toString().toByteArray())
    d.update(url.toString().toByteArray())

    val hex = StringUtil.toHexString(d.digest())
    return File(cachesHome, "$hex.12")
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
      val data = UnsyncByteArrayInputStream(file.readBytes())
      val buff = DataInputStream(data)

      val width = buff.readDouble()
      val height = buff.readDouble()
      val w = buff.readInt()
      val h = buff.readInt()
      val sz = buff.readInt()
      val img = IntArray(sz)
      for(i in 0 until sz) {
        img[i] = buff.readInt()
      }

      //TODO: what image to create?
      val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
      image.raster.setPixels(0, 0, w, h, img)
      docSize?.setSize(width, height)
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

  fun storeLoadedImage(theme: String, url: URL, scale: Double, image: BufferedImage, size: Dimension2DDouble) {
    require(image.type == imagePixelFormat) { "image type must be $imagePixelFormat but was ${image.type}"}

    forkIOTask {
      val file = cacheFile(theme, url, scale)

      val intData = image.raster.getPixels(0, 0, image.width, image.height, null as IntArray?)
      val output = UnsyncByteArrayOutputStream(intData.size * Int.SIZE_BYTES + 2 * 64 + 2 * 32 + 32)
      val buff = DataOutputStream(output)

      buff.writeDouble(size.width)
      buff.writeDouble(size.height)

      buff.writeInt(image.width)
      buff.writeInt(image.height)

      buff.writeInt(intData.size)
      for (i in intData) {
        buff.writeInt(i)
      }

      file.parentFile?.mkdirs()
      file.writeBytes(output.toByteArray())
    }
  }
}
