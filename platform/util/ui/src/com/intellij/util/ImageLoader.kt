// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "LiftReturnOrAssignment")

package com.intellij.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.icons.*
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.*
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import org.imgscalr.Scalr
import org.jetbrains.annotations.NonNls
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.*

object ImageLoader {
  @Suppress("unused")
  const val ALLOW_FLOAT_SCALING = 0x01

  @Suppress("unused")
  const val USE_CACHE = 0x02

  @Suppress("unused")
  const val USE_DARK = 0x04

  @Suppress("unused")
  const val USE_SVG = 0x08

  @JvmStatic
  fun loadFromUrl(url: URL): Image? {
    val path = url.toString()
    return loadImage(path = path, classLoader = null)
  }

  @JvmStatic
  fun scaleImage(image: Image, scale: Double): Image {
    return doScaleImage(image = image, scale = scale)
  }

  @JvmStatic
  fun scaleImage(image: Image, targetSize: Int): Image = scaleImage(image = image, targetWidth = targetSize, targetHeight = targetSize)

  @JvmStatic
  fun scaleImage(image: Image, targetWidth: Int, targetHeight: Int): Image {
    if (image is JBHiDPIScaledImage) {
      return image.scale(targetWidth, targetHeight)
    }

    val w = image.getWidth(null)
    val h = image.getHeight(null)
    if (w <= 0 || h <= 0 || w == targetWidth && h == targetHeight) {
      return image
    }
    else {
      return Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, targetWidth, targetHeight, null)
    }
  }

  @JvmStatic
  @Deprecated("Use {@link #loadFromResource(String, Class)}")
  fun loadFromResource(s: @NonNls String): Image? {
    val callerClass = ReflectionUtil.getGrandCallerClass()
    return loadFromResource(path = s, aClass = callerClass ?: return null)
  }

  @JvmStatic
  fun loadFromResource(path: @NonNls String, aClass: Class<*>): Image? {
    return loadImage(path = path, resourceClass = aClass, classLoader = null)
  }

  @JvmStatic
  fun loadFromBytes(bytes: ByteArray): Image? {
    return loadFromStream(ByteArrayInputStream(bytes))
  }

  @JvmStatic
  fun loadFromStream(inputStream: InputStream): Image? {
    try {
      inputStream.use {
        // for backward compatibility assume the image is hidpi-aware (includes default SYS_SCALE)
        val scaleContext = ScaleContext.create()
        val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
        val image = loadPng(inputStream)
        if (StartupUiUtil.isJreHiDPI(scaleContext)) {
          val userScale = scaleContext.getScale(DerivedScaleType.EFF_USR_SCALE)
          return HiDPIImage(image = image,
                            width = (image.width / scale) * userScale,
                            height = (image.height / scale) * userScale,
                            type = BufferedImage.TYPE_INT_ARGB)
        }
        return image
      }
    }
    catch (e: IOException) {
      logger<ImageLoader>().error(e)
    }
    return null
  }

  @Suppress("unused")
  @Throws(IOException::class)
  @JvmStatic
  fun loadCustomIcon(file: File): Image? {
    return loadCustomIcon(url = file.toURI().toURL())
  }

  class Dimension2DDouble(var width: Double, var height: Double) {
    fun setSize(size: Dimension2DDouble) {
      width = size.width
      height = size.height
    }

    fun setSize(width: Double, height: Double) {
      this.width = width
      this.height = height
    }
  }
}