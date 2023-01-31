// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorUtil
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.*
import com.intellij.util.io.DigestUtil.sha512
import com.intellij.util.ui.ImageUtil
import org.apache.batik.transcoder.TranscoderException
import org.jetbrains.annotations.ApiStatus
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.Color
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

private val USE_CACHE = java.lang.Boolean.parseBoolean(System.getProperty("idea.ui.icons.svg.disk.cache", "true"))

private var selectionColorPatcher: SVGLoader.SvgElementColorPatcherProvider? = null

/**
 * Plugins should use [ImageLoader.loadFromResource].
 */
@ApiStatus.Internal
object SVGLoader {
  const val ICON_DEFAULT_SIZE = 16

  @ApiStatus.Internal
  val DEFAULT_THEME: ByteArray = ArrayUtilRt.EMPTY_BYTE_ARRAY

  @Throws(IOException::class)
  @JvmStatic
  fun load(url: URL, scale: Float): Image {
    return load(path = url.path, stream = url.openStream(), SvgCacheMapper(scale = scale), colorPatcher = null)
  }

  @Throws(IOException::class)
  @JvmStatic
  fun load(stream: InputStream, scale: Float): Image {
    return load(path = null, stream = stream, SvgCacheMapper(scale = scale), colorPatcher = null)
  }

  @Throws(IOException::class)
  fun load(url: URL?, stream: InputStream, scale: Float): BufferedImage {
    return load(path = url?.path, stream = stream, SvgCacheMapper(scale = scale), colorPatcher = null)
  }

  @ApiStatus.Internal
  fun loadFromClassResource(classLoader: ClassLoader?,
                            path: String,
                            rasterizedCacheKey: Int,
                            mapper: SvgCacheMapper,
                            colorPatcher: SvgElementColorPatcherProvider?,
                            resourceClass: Class<*>? = null): Image? {
    val themeDigest: ByteArray?
    var data: ByteArray? = null
    val subPatcher = colorPatcher?.forPath(path)
    val subPatcherDigest = subPatcher?.digest()
    if (subPatcher == null || subPatcherDigest != null) {
      val start = StartUpMeasurer.getCurrentTimeIfEnabled()
      themeDigest = subPatcherDigest ?: DEFAULT_THEME
      if (themeDigest === DEFAULT_THEME && rasterizedCacheKey != 0 && !mapper.isStroke) {
        try {
          SvgCache.prebuiltPersistentCache?.loadFromCache(key = rasterizedCacheKey, mapper = mapper)?.let {
            return it
          }
        }
        catch (e: Throwable) {
          logger<SVGLoader>().error("cannot load from prebuilt icon cache", e)
        }
      }

      data = ImageLoader.getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader) ?: return null
      SvgCache.persistentCache?.loadFromCache(themeDigest, data, mapper)?.let {
        return it
      }
      if (start != -1L) {
        IconLoadMeasurer.svgCacheRead.end(start)
      }
    }
    else {
      themeDigest = null
    }

    if (data == null) {
      data = ImageLoader.getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader) ?: return null
    }
    return loadAndCache(path = path, data = data, mapper = mapper, themeDigest = themeDigest, colorPatcher = colorPatcher)
  }

  internal fun load(path: String?,
                    stream: InputStream,
                    mapper: SvgCacheMapper,
                    colorPatcher: SvgElementColorPatcherProvider?): BufferedImage {
    val persistentCache = SvgCache.persistentCache
    val elementColorPatcher = colorPatcher?.forPath(path)
    val digest = elementColorPatcher?.digest()
    if (persistentCache == null || elementColorPatcher != null && digest == null) {
      return loadAndCache(path = path,
                          data = stream.readAllBytes(),
                          mapper = mapper,
                          themeDigest = null,
                          colorPatcher = colorPatcher)
    }

    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val themeDigest = if (elementColorPatcher == null) DEFAULT_THEME else digest!!

    val data: ByteArray = stream.readAllBytes()
    persistentCache.loadFromCache(themeDigest = themeDigest, imageBytes = data, mapper = mapper)?.let {
      return it
    }

    if (start != -1L) {
      IconLoadMeasurer.svgCacheRead.end(start)
    }
    return loadAndCache(path = path,
                        data = data,
                        mapper = mapper,
                        themeDigest = themeDigest,
                        colorPatcher = colorPatcher)
  }

  @Throws(IOException::class)
  fun loadWithoutCache(content: ByteArray, scale: Float): BufferedImage {
    return try {
      SvgTranscoder.createImage(scale, createDocument(null, ByteArrayInputStream(content)), null)
    }
    catch (e: TranscoderException) {
      throw IOException(e)
    }
  }

  /**
   * Loads an image with the specified `width` and `height` (in user space). Size specified in svg file is ignored.
   * Note: always pass `url` when it is available.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun load(url: URL?, stream: InputStream, scaleContext: ScaleContext, width: Double, height: Double): Image {
    return try {
      val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE)
      SvgTranscoder.createImage(scale = 1f,
                                document = createDocument(url?.path, stream),
                                outDimensions = null,
                                overriddenWidth = (width * scale).toFloat(),
                                overriddenHeight = (height * scale).toFloat())
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
                     mapper = SvgCacheMapper(scale = context.getScale(DerivedScaleType.PIX_SCALE).toFloat()),
                     colorPatcher = null)
    return ImageUtil.ensureHiDPI(image, context)
  }

  @Throws(IOException::class)
  @JvmStatic
  fun getDocumentSize(stream: InputStream, scale: Float): ImageLoader.Dimension2DDouble {
    // In order to get the size, we parse the whole document and build a tree ("GVT"), what might be too expensive.
    // So, to optimize we extract the svg header (possibly prepended with <?xml> header) and parse only it.
    // Assumes 8-bit encoding of the input stream (no one in his right mind would use wide characters for SVG anyway).
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
        buffer.write(byteArrayOf(
          '<'.code.toByte(),
          '/'.code.toByte(),
          's'.code.toByte(), 'v'.code.toByte(), 'g'.code.toByte(),
          '>'.code.toByte()))
        val input = ByteArrayInputStream(buffer.internalBuffer, 0, buffer.size())
        return SvgTranscoder.getDocumentSize(scale = scale, document = createSvgDocument(uri = null, reader = input))
      }
    }
    return ImageLoader.Dimension2DDouble((ICON_DEFAULT_SIZE * scale).toDouble(), (ICON_DEFAULT_SIZE * scale).toDouble())
  }

  @Throws(IOException::class)
  @JvmStatic
  fun getMaxZoomFactor(path: String?, stream: InputStream, scaleContext: ScaleContext): Double {
    val size = SvgTranscoder.getDocumentSize(scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat(),
                                             document = createDocument(path, stream))
    val iconMaxSize = SvgTranscoder.iconMaxSize
    return (iconMaxSize / size.width).coerceAtMost(iconMaxSize / size.height)
  }

  @Deprecated("Use colorPatcherProvider",
              ReplaceWith("colorPatcherProvider = provider", "com.intellij.util.SVGLoader.colorPatcherProvider"))
  @JvmStatic
  fun setContextColorPatcher(provider: SvgElementColorPatcherProvider?) {
    colorPatcherProvider = provider
  }

  @ApiStatus.Internal
  fun getStrokePatcher(resultColor: Color,
                       strokeColors: List<String>,
                       backgroundColors: List<String> = emptyList()): SvgElementColorPatcherProvider {
    val fg = ColorUtil.toHtmlColor(resultColor)
    val map: Map<String, String> = strokeColors.associateWith { fg }
    val alpha = HashMap<String, Int>(map.size)
    map.values.forEach { alpha[it] = resultColor.alpha }

    val hasher = sha512()
    for (x in strokeColors + "" + alpha.map { it.value.toString() } + backgroundColors + fg) {
      hasher.update(x.encodeToByteArray())
    }
    val digest = hasher.digest()

    return object : SvgElementColorPatcherProvider {
      override fun forPath(path: String?): SvgElementColorPatcher? {
        return newPatcher(digest, map + backgroundColors.associateWith { "#00000000" }, alpha)
      }
    }
  }

  @JvmStatic
  var colorPatcherProvider: SvgElementColorPatcherProvider? = null
    set(colorPatcher) {
      field = colorPatcher
      IconLoader.clearCache()
    }

  @JvmStatic
  fun newPatcher(digest: ByteArray?, newPalette: Map<String, String>, alphas: Map<String, Int>): SvgElementColorPatcher? {
    if (newPalette.isEmpty()) {
      return null
    }

    return object : SvgElementColorPatcher {
      override fun digest(): ByteArray? = digest

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
        if (!color.isEmpty()) {
          var alpha = 255
          if (!opacity.isEmpty()) {
            try {
              alpha = ceil((255f * opacity.toFloat())).toInt()
            }
            catch (ignore: Exception) {
            }
          }
          var newColor: String? = null
          val key = toCanonicalColor(color)
          if (alpha != 255) {
            newColor = newPalette.get(key + Integer.toHexString(alpha))
          }
          if (newColor == null) {
            newColor = newPalette.get(key)
          }
          if (newColor != null) {
            svg.setAttribute(attrName, newColor)
            alphas.get(newColor)?.let {
              svg.setAttribute("$attrName-opacity", (it.toFloat() / 255f).toString())
            }
          }
        }
      }
    }
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

private fun toCanonicalColor(color: String): String {
  var s = color.lowercase()
  //todo[kb]: add support for red, white, black, and other named colors
  if (s.startsWith("#") && s.length < 7) {
    s = "#" + ColorUtil.toHex(ColorUtil.fromHex(s))
  }
  return s
}

private fun createDocument(url: String?, inputStream: InputStream): Document {
  val document = createSvgDocument(url, inputStream)
  patchColors(url = url, document = document, colorPatcher = null)
  return document
}

private fun patchColors(url: String?, document: Document, colorPatcher: SVGLoader.SvgElementColorPatcherProvider?) {
  colorPatcher?.forPath(url)?.patchColors(document.documentElement)
}

private fun createDocument(url: String?, data: ByteArray, colorPatcher: SVGLoader.SvgElementColorPatcherProvider?): Document {
  val document = createSvgDocument(uri = url, data = data)
  patchColors(url = url, document = document, colorPatcher = colorPatcher)
  return document
}

private fun loadAndCache(path: String?,
                         data: ByteArray,
                         mapper: SvgCacheMapper,
                         themeDigest: ByteArray?,
                         colorPatcher: SVGLoader.SvgElementColorPatcherProvider?): BufferedImage {
  val decodingStart = StartUpMeasurer.getCurrentTimeIfEnabled()
  val bufferedImage = try {
    SvgTranscoder.createImage(scale = mapper.scale, document = createDocument(path, data, colorPatcher), outDimensions = mapper.docSize)
  }
  catch (e: TranscoderException) {
    mapper.docSize?.setSize(0.0, 0.0)
    throw IOException(e)
  }
  if (decodingStart != -1L) {
    IconLoadMeasurer.svgDecoding.end(decodingStart)
  }

  if (themeDigest != null) {
    try {
      val cacheWriteStart = StartUpMeasurer.getCurrentTimeIfEnabled()
      SvgCache.persistentCache?.storeLoadedImage(themeDigest = themeDigest, imageBytes = data, mapper = mapper, image = bufferedImage)
      IconLoadMeasurer.svgCacheWrite.end(cacheWriteStart)
    }
    catch (e: Exception) {
      logger<SVGLoader>().error("Failed to write SVG cache for: $path", e)
    }
  }
  return bufferedImage
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
      if (USE_CACHE) SvgCacheManager(Path.of(PathManager.getSystemPath(), "icon-v7.db")) else null
    }
    catch (e: Exception) {
      logger<SVGLoader>().error(e)
      null
    }
  }
}