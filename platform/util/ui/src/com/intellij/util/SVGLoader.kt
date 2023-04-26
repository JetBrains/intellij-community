// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.util

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.*
import com.intellij.util.ui.ImageUtil
import org.jetbrains.annotations.ApiStatus
import org.w3c.dom.Element
import java.awt.*
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.swing.Icon
import kotlin.math.max

private var selectionColorPatcher: SVGLoader.SvgElementColorPatcherProvider? = null

private val iconMaxSize: Float by lazy {
  var maxSize = Integer.MAX_VALUE.toFloat()
  if (!GraphicsEnvironment.isHeadless()) {
    val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val bounds = device.defaultConfiguration.bounds
    val tx = device.defaultConfiguration.defaultTransform
    maxSize = max(bounds.width * tx.scaleX, bounds.height * tx.scaleY).toInt().toFloat()
  }
  maxSize
}

/**
 * Plugins should use [ImageLoader.loadFromResource].
 */
@ApiStatus.Internal
object SVGLoader {
  const val ICON_DEFAULT_SIZE = 16

  @Throws(IOException::class)
  @JvmStatic
  fun load(url: URL, scale: Float): Image {
    return loadSvg(path = url.path, stream = url.openStream(), scale = scale, colorPatcherProvider = null)
  }

  @Throws(IOException::class)
  @JvmStatic
  fun load(stream: InputStream, scale: Float): Image {
    return loadSvg(path = null, stream = stream, scale = scale, colorPatcherProvider = null)
  }

  @Throws(IOException::class)
  fun load(url: URL?, stream: InputStream, scale: Float): BufferedImage {
    return loadSvg(path = url?.path, stream = stream, scale = scale, colorPatcherProvider = null)
  }

  /**
   * Loads an image with the specified `width` and `height` (in user space). Size specified in svg file is ignored.
   * Note: always pass `url` when it is available.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun load(@Suppress("UNUSED_PARAMETER") url: URL?, stream: InputStream, scaleContext: ScaleContext, width: Double, height: Double): Image {
    val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE)
    return renderSvgWithSize(document = createJSvgDocument(stream),
                             width = (width * scale).toFloat(),
                             height = (height * scale).toFloat())
  }

  /**
   * Loads a HiDPI-aware image of the size specified in the svg file.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun loadHiDPI(url: URL?, stream: InputStream, context: ScaleContext): Image {
    val scale = context.getScale(DerivedScaleType.PIX_SCALE).toFloat()
    val image = loadSvg(path = url?.path, stream = stream, scale = scale, colorPatcherProvider = null)
    return ImageUtil.ensureHiDPI(image, context)
  }

  @Suppress("unused")
  @Throws(IOException::class)
  @JvmStatic
  fun getMaxZoomFactor(@Suppress("UNUSED_PARAMETER") path: String?, stream: InputStream, scaleContext: ScaleContext): Double {
    return getMaxZoomFactor(stream.readAllBytes(), scaleContext)
  }

  fun getMaxZoomFactor(data: ByteArray, scaleContext: ScaleContext): Double {
    val size = getSvgDocumentSize(data)
    val iconMaxSize = iconMaxSize
    val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE)
    return (iconMaxSize / (size.width * scale)).coerceAtMost(iconMaxSize / (size.height * scale))
  }

  @JvmStatic
  var colorPatcherProvider: SvgElementColorPatcherProvider? = null
    set(colorPatcher) {
      field = colorPatcher
      IconLoader.clearCache()
    }

  @JvmStatic
  fun setSelectionColorPatcherProvider(colorPatcher: SvgElementColorPatcherProvider?) {
    selectionColorPatcher = colorPatcher
    IconLoader.clearCache()
  }

  @JvmStatic
  fun paintIconWithSelection(icon: Icon, c: Component?, g: Graphics?, x: Int, y: Int) {
    val patcher = selectionColorPatcher
    if (patcher == null || !Registry.`is`("ide.patch.icons.on.selection", false)) {
      icon.paintIcon(c, g, x, y)
    }
    else {
      IconLoader.colorPatchedIcon(icon, patcher).paintIcon(c, g, x, y)
    }
  }

  interface SvgElementColorPatcher {
    fun patchColors(svg: Element) {
    }

    /**
     * @return hash code of the current SVG color patcher or null to disable rendered SVG images caching
     */
    fun digest(): ByteArray?
  }

  interface SvgElementColorPatcherProvider {
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Implement attributeForPath")
    fun forPath(path: String?): SvgElementColorPatcher? {
      return null
    }

    fun attributeForPath(path: String?): SvgAttributePatcher? {
      return null
    }

    fun digest(): LongArray? = null

    @Deprecated("Implement digest", ReplaceWith("digest"))
    fun wholeDigest(): ByteArray? = null
  }
}
