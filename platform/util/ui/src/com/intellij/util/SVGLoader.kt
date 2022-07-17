// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.Strings
import com.intellij.ui.ColorUtil
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.ui.svg.SvgPrebuiltCacheManager
import com.intellij.ui.svg.SvgTranscoder.Companion.createImage
import com.intellij.ui.svg.SvgTranscoder.Companion.getDocumentSize
import com.intellij.ui.svg.SvgTranscoder.Companion.iconMaxSize
import com.intellij.ui.svg.createSvgDocument
import com.intellij.util.ui.EDT
import com.intellij.util.ui.ImageUtil
import org.apache.batik.transcoder.TranscoderException
import org.jetbrains.annotations.ApiStatus
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import kotlin.math.ceil

private val DEFAULT_THEME = ArrayUtilRt.EMPTY_BYTE_ARRAY
private val USE_CACHE = java.lang.Boolean.parseBoolean(System.getProperty("idea.ui.icons.svg.disk.cache", "true"))
private var ourColorPatcher: SVGLoader.SvgElementColorPatcherProvider? = null
private var selectionColorPatcher: SVGLoader.SvgElementColorPatcherProvider? = null
private var contextColorPatcher: SVGLoader.SvgElementColorPatcherProvider? = null

/**
 * Plugins should use [ImageLoader.loadFromResource].
 */
@ApiStatus.Internal
object SVGLoader {
  @Volatile
  @JvmStatic
  var isColorRedefinitionContext: Boolean = false
    get() {
      return (contextColorPatcher != null && field
              && EDT.isCurrentThreadEdt()
              && Registry.`is`("ide.patch.icons.on.selection", false))
    }

  const val ICON_DEFAULT_SIZE = 16

  @Throws(IOException::class)
  @JvmStatic
  fun load(url: URL, scale: Float): Image {
    return load(path = url.path, stream = url.openStream(), scale = scale, isDark = false, docSize = null)
  }

  @Throws(IOException::class)
  @JvmStatic
  fun load(stream: InputStream, scale: Float): Image {
    return load(path = null, stream = stream, scale = scale, isDark = false, docSize = null)
  }

  @Throws(IOException::class)
  @JvmStatic
  fun load(url: URL?, stream: InputStream, scale: Float): Image {
    return load(path = url?.path, stream = stream, scale = scale, isDark = false, docSize = null)
  }

  @ApiStatus.Internal
  @Throws(IOException::class)
  @JvmStatic
  fun loadFromClassResource(resourceClass: Class<*>?,
                            classLoader: ClassLoader?,
                            path: String,
                            rasterizedCacheKey: Int,
                            scale: Float,
                            isDark: Boolean,
                            docSize: ImageLoader.Dimension2DDouble? = null /*OUT*/): Image? {
    var themeDigest: ByteArray?
    var data: ByteArray? = null
    val cache = SvgCache.prebuiltPersistentCache
    if (cache != null && !isColorRedefinitionContext) {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()
      themeDigest = DEFAULT_THEME
      val colorPatcher = ourColorPatcher
      if (colorPatcher != null) {
        val subPatcher = colorPatcher.forPath(path)
        if (subPatcher != null) {
          themeDigest = subPatcher.digest()
        }
      }
      if (themeDigest != null) {
        @Suppress("ReplaceArrayEqualityOpWithArraysEquals")
        if (themeDigest == DEFAULT_THEME && rasterizedCacheKey != 0) {
          try {
            cache.loadFromCache(rasterizedCacheKey, scale, isDark, docSize)?.let {
              return it
            }
          }
          catch (e: Throwable) {
            logger<SVGLoader>().error("cannot load from prebuilt icon cache", e)
          }
        }
        data = ImageLoader.getResourceData(path, resourceClass, classLoader) ?: return null
        SvgCache.persistentCache!!.loadFromCache(themeDigest, data, scale, isDark, docSize)?.let {
          return it
        }
      }
      if (start != -1L) {
        IconLoadMeasurer.svgCacheRead.end(start)
      }
    }
    else {
      themeDigest = null
    }
    if (data == null) {
      data = ImageLoader.getResourceData(path, resourceClass, classLoader) ?: return null
    }
    return loadAndCache(path = path, data = data, scale = scale, docSize = docSize, themeDigest = themeDigest)
  }

  @ApiStatus.Internal
  @Throws(IOException::class)
  @JvmStatic
  fun load(path: String?,
           stream: InputStream,
           scale: Float,
           isDark: Boolean,
           docSize: ImageLoader.Dimension2DDouble? /*OUT*/): Image {
    val persistentCache = SvgCache.persistentCache
    if (persistentCache == null || isColorRedefinitionContext) {
      return loadAndCache(path = path,
                          data = stream.readAllBytes(),
                          scale = scale,
                          docSize = docSize,
                          themeDigest = null)
    }


    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val colorPatcher = ourColorPatcher?.forPath(path)
    val themeDigest = if (colorPatcher == null) DEFAULT_THEME else colorPatcher.digest()
    val data: ByteArray?
    if (themeDigest == null) {
      data = null
    }
    else {
      data = stream.readAllBytes()
      persistentCache.loadFromCache(themeDigest = themeDigest,
                                    imageBytes = data,
                                    scale = scale,
                                    isDark = isDark,
                                    docSize = docSize)?.let {
        return it
      }
    }
    if (start != -1L) {
      IconLoadMeasurer.svgCacheRead.end(start)
    }
    return loadAndCache(path, data, scale, docSize, themeDigest)
  }

  @Throws(IOException::class)
  private fun loadAndCache(path: String?,
                           data: ByteArray?,
                           scale: Float,
                           docSize: ImageLoader.Dimension2DDouble?,
                           themeDigest: ByteArray?): BufferedImage {
    val decodingStart = StartUpMeasurer.getCurrentTimeIfEnabled()
    val bufferedImage = try {
      createImage(scale, createDocument(path, data), docSize)
    }
    catch (e: TranscoderException) {
      docSize?.setSize(0.0, 0.0)
      throw IOException(e)
    }
    if (decodingStart != -1L) {
      IconLoadMeasurer.svgDecoding.end(decodingStart)
    }
    if (themeDigest != null) {
      try {
        val cacheWriteStart = StartUpMeasurer.getCurrentTimeIfEnabled()
        SvgCache.persistentCache?.storeLoadedImage(themeDigest, data!!, scale, bufferedImage)
        IconLoadMeasurer.svgCacheWrite.end(cacheWriteStart)
      }
      catch (e: Exception) {
        Logger.getInstance(SVGLoader::class.java).error("Failed to write SVG cache for: $path", e)
      }
    }
    return bufferedImage
  }

  @Throws(IOException::class)
  @JvmStatic
  fun loadWithoutCache(content: ByteArray, scale: Float): BufferedImage {
    return try {
      createImage(scale, createDocument(null, ByteArrayInputStream(content)), null)
    }
    catch (e: TranscoderException) {
      throw IOException(e)
    }
  }

  /**
   * Loads an image with the specified `width` and `height` (in user space). Size specified in svg file is ignored.
   *
   *
   * Note: always pass `url` when it is available.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun load(url: URL?, stream: InputStream, scaleContext: ScaleContext, width: Double, height: Double): Image {
    return try {
      val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE)
      createImage(1f, createDocument(url?.path, stream), null, (width * scale).toFloat(), (height * scale).toFloat())
    }
    catch (e: TranscoderException) {
      throw IOException(e)
    }
  }

  /**
   * Loads a HiDPI-aware image of the size specified in the svg file.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun loadHiDPI(url: URL?, stream: InputStream, context: ScaleContext): Image {
    val image = load(path = url?.path,
                     stream = stream,
                     scale = context.getScale(DerivedScaleType.PIX_SCALE).toFloat(),
                     isDark = false,
                     docSize = null)
    return ImageUtil.ensureHiDPI(image, context)
  }

  @Throws(IOException::class)
  @JvmStatic
  fun getDocumentSize(stream: InputStream, scale: Float): ImageLoader.Dimension2DDouble {
    // In order to get the size we parse the whole document and build a tree ("GVT"), what might be too expensive.
    // So, to optimize we extract the svg header (possibly prepended with <?xml> header) and parse only it.
    // Assumes 8-bit encoding of the input stream (no one in theirs right mind would use wide characters for SVG anyway).
    val buffer = BufferExposingByteArrayOutputStream(100)
    val bytes = ByteArray(3)
    var checkClosingBracket = false
    var ch: Int
    while (stream.read().also { ch = it } != -1) {
      buffer.write(ch)
      if (ch == '<'.code) {
        val n = stream.read(bytes, 0, 3)
        if (n == -1) break
        buffer.write(bytes, 0, n)
        checkClosingBracket = n == 3 && bytes[0] == 's'.code.toByte() && bytes[1] == 'v'.code.toByte() && bytes[2] == 'g'.code.toByte()
      }
      else if (checkClosingBracket && ch == '>'.code) {
        buffer.write(
          byteArrayOf('<'.code.toByte(), '/'.code.toByte(), 's'.code.toByte(), 'v'.code.toByte(), 'g'.code.toByte(), '>'.code.toByte()))
        val input = ByteArrayInputStream(buffer.internalBuffer, 0, buffer.size())
        return getDocumentSize(scale, createSvgDocument(null, input))
      }
    }
    return ImageLoader.Dimension2DDouble((ICON_DEFAULT_SIZE * scale).toDouble(), (ICON_DEFAULT_SIZE * scale).toDouble())
  }

  @Throws(IOException::class)
  @JvmStatic
  fun getMaxZoomFactor(path: String?, stream: InputStream, scaleContext: ScaleContext): Double {
    val size = getDocumentSize(scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat(), createDocument(path, stream))
    val iconMaxSize = iconMaxSize
    return (iconMaxSize / size.width).coerceAtMost(iconMaxSize / size.height)
  }

  private fun createDocument(url: String?, inputStream: InputStream): Document {
    val document = createSvgDocument(url, inputStream)
    patchColors(url, document)
    return document
  }

  private fun createDocument(url: String?, data: ByteArray?): Document {
    val document = createSvgDocument(url, data!!)
    patchColors(url, document)
    return document
  }

  private fun patchColors(url: String?, document: Document) {
    val colorPatcher = ourColorPatcher
    if (colorPatcher != null) {
      val patcher = colorPatcher.forPath(url)
      patcher?.patchColors(document.documentElement)
    }
    if (isColorRedefinitionContext) {
      val selectionPatcherProvider = colorPatcherProvider
      if (selectionPatcherProvider != null) {
        val selectionPatcher = selectionPatcherProvider.forPath(url)
        selectionPatcher?.patchColors(document.documentElement)
      }
    }
  }

  @JvmStatic
  fun setContextColorPatcher(provider: SvgElementColorPatcherProvider?) {
    contextColorPatcher = provider
  }

  @JvmStatic
  var colorPatcherProvider: SvgElementColorPatcherProvider?
    get() = contextColorPatcher
    set(colorPatcher) {
      ourColorPatcher = colorPatcher
      IconLoader.clearCache()
    }

  @JvmStatic
  fun newPatcher(digest: ByteArray?,
                 newPalette: Map<String, String>,
                 alphas: Map<String, Int>): SvgElementColorPatcher? {
    return if (newPalette.isEmpty()) {
      null
    }
    else object : SvgElementColorPatcher {
      override fun digest(): ByteArray? {
        return digest
      }

      override fun patchColors(svg: Element) {
        patchColorAttribute(svg, "fill")
        patchColorAttribute(svg, "stroke")
        val nodes = svg.childNodes
        val length = nodes.length
        for (i in 0 until length) {
          val item = nodes.item(i)
          if (item is Element) {
            patchColors(item)
          }
        }
      }

      private fun patchColorAttribute(svg: Element, attrName: String) {
        val color = svg.getAttribute(attrName)
        val opacity = svg.getAttribute("$attrName-opacity")
        if (!Strings.isEmpty(color)) {
          var alpha = 255
          if (!Strings.isEmpty(opacity)) {
            try {
              alpha = ceil((255f * opacity.toFloat()).toDouble()).toInt()
            }
            catch (ignore: Exception) {
            }
          }
          var newColor: String? = null
          val key = toCanonicalColor(color)
          if (alpha != 255) {
            newColor = newPalette[key + Integer.toHexString(alpha)]
          }
          if (newColor == null) {
            newColor = newPalette[key]
          }
          if (newColor != null) {
            svg.setAttribute(attrName, newColor)
            if (alphas[newColor] != null) {
              svg.setAttribute("$attrName-opacity", (java.lang.Float.valueOf(alphas[newColor]!!.toFloat()) / 255f).toString())
            }
          }
        }
      }
    }
  }

  private fun toCanonicalColor(color: String): String {
    var s = color.lowercase()
    //todo[kb]: add support for red, white, black, and other named colors
    if (s.startsWith("#") && s.length < 7) {
      s = "#" + ColorUtil.toHex(ColorUtil.fromHex(s))
    }
    return s
  }

  @JvmStatic
  fun setSelectionColorPatcherProvider(colorPatcher: SvgElementColorPatcherProvider?) {
    selectionColorPatcher = colorPatcher
    IconLoader.clearCache()
  }

  @JvmStatic
  fun paintIconWithSelection(icon: Icon, c: Component?, g: Graphics?, x: Int, y: Int) {
    if (selectionColorPatcher == null) {
      icon.paintIcon(c, g, x, y)
    }
    else {
      try {
        setContextColorPatcher(selectionColorPatcher)
        isColorRedefinitionContext = true
        icon.paintIcon(c, g, x, y)
      }
      finally {
        setContextColorPatcher(null)
        isColorRedefinitionContext = false
      }
    }
  }

  interface SvgElementColorPatcher {
    fun patchColors(svg: Element)

    /**
     * @return hash code of the current SVG color patcher or null to disable rendered SVG images caching
     */
    fun digest(): ByteArray?
  }

  interface SvgElementColorPatcherProvider {
    fun forPath(path: String?): SvgElementColorPatcher?
  }

  val persistentCache: SvgCacheManager?
    get() = SvgCache.persistentCache
}

private object SvgCache {
  val persistentCache: SvgCacheManager?
  val prebuiltPersistentCache: SvgPrebuiltCacheManager?

  init {
    var prebuiltCache: SvgPrebuiltCacheManager? = null
    if (USE_CACHE) {
      try {
        var dbDir: Path? = null
        val dbPath = System.getProperty("idea.ui.icons.prebuilt.db")
        if (dbPath != "false") {
          dbDir = if (dbPath == null || dbPath.isEmpty()) {
            Path.of(PathManager.getBinPath() + "/icons")
          }
          else {
            Path.of(dbPath)
          }
        }
        prebuiltCache = if (dbDir != null && Files.isDirectory(dbDir)) SvgPrebuiltCacheManager(dbDir) else null
      }
      catch (e: Exception) {
        logger<SVGLoader>().error("Cannot use prebuilt svg cache", e)
        prebuiltCache = null
      }
    }

    prebuiltPersistentCache = prebuiltCache
    persistentCache = try {
      if (USE_CACHE) SvgCacheManager(Path.of(PathManager.getSystemPath(), "icons-v6.db")) else null
    }
    catch (e: Exception) {
      Logger.getInstance(SVGLoader::class.java).error(e)
      null
    }
  }
}