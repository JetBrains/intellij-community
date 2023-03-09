// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.util

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorUtil
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.*
import com.intellij.util.text.CharSequenceReader
import com.intellij.util.ui.ImageUtil
import com.intellij.util.xml.dom.createXmlStreamReader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.xxh3.Xxh3
import org.w3c.dom.Element
import java.awt.*
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter
import java.net.URL
import java.nio.file.Path
import javax.swing.Icon
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.ceil
import kotlin.math.max

private val USE_CACHE = java.lang.Boolean.parseBoolean(System.getProperty("idea.ui.icons.svg.disk.cache", "true"))

// https://youtrack.jetbrains.com/issue/IDEA-312509/mvstore.MVStoreException-on-zoom-SVG-with-text
private const val MAX_SCALE_TO_CACHE = 4

private var selectionColorPatcher: SVGLoader.SvgElementColorPatcherProvider? = null

internal val iconMaxSize: Float by lazy {
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
  @JvmField
  @ApiStatus.Internal
  val cache: SvgCacheManager? = try {
    if (USE_CACHE) SvgCacheManager(Path.of(PathManager.getSystemPath(), "icon-v8.db")) else null
  }
  catch (e: Exception) {
    logger<SVGLoader>().error(e)
    null
  }

  const val ICON_DEFAULT_SIZE = 16

  @Throws(IOException::class)
  @JvmStatic
  fun load(url: URL, scale: Float): Image {
    return load(path = url.path, stream = url.openStream(), scale = scale, colorPatcherProvider = null)
  }

  @Throws(IOException::class)
  @JvmStatic
  fun load(stream: InputStream, scale: Float): Image {
    return load(path = null, stream = stream, scale = scale, colorPatcherProvider = null)
  }

  @ApiStatus.Internal
  fun loadSvg(data: ByteArray, scale: Float): Image {
    return loadAndCacheIfApplicable(path = null,
                                    precomputedCacheKey = 0,
                                    scale = scale,
                                    colorPatcherProvider = null,
                                    cache = cache) {
      data
    }!!
  }

  @Throws(IOException::class)
  fun load(url: URL?, stream: InputStream, scale: Float): BufferedImage {
    return load(path = url?.path, stream = stream, scale = scale, colorPatcherProvider = null)
  }

  @ApiStatus.Internal
  fun loadFromClassResource(classLoader: ClassLoader?,
                            path: String,
                            precomputedCacheKey: Int,
                            scale: Float,
                            compoundCacheKey: SvgCacheClassifier,
                            colorPatcherProvider: SvgElementColorPatcherProvider?,
                            resourceClass: Class<*>? = null): Image? {
    return loadAndCacheIfApplicable(path = path,
                                    precomputedCacheKey = precomputedCacheKey,
                                    scale = scale,
                                    compoundCacheKey = compoundCacheKey,
                                    colorPatcherProvider = colorPatcherProvider,
                                    cache = cache) {
      ImageLoader.getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader)
    }
  }

  internal fun load(path: String?,
                    stream: InputStream,
                    scale: Float,
                    compoundCacheKey: SvgCacheClassifier? = null,
                    colorPatcherProvider: SvgElementColorPatcherProvider?): BufferedImage {
    return loadAndCacheIfApplicable(path = path,
                                    precomputedCacheKey = 0,
                                    scale = scale,
                                    compoundCacheKey = compoundCacheKey ?: SvgCacheClassifier(scale),
                                    colorPatcherProvider = colorPatcherProvider,
                                    cache = cache) {
      stream.readAllBytes()
    }!!
  }

  /**
   * Loads an image with the specified `width` and `height` (in user space). Size specified in svg file is ignored.
   * Note: always pass `url` when it is available.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun load(@Suppress("UNUSED_PARAMETER") url: URL?, stream: InputStream, scaleContext: ScaleContext, width: Double, height: Double): Image {
    val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE)
    return renderUsingJSvg(scale = 1f,
                           document = createJSvgDocument(stream),
                           baseWidth = (width * scale).toFloat(),
                           baseHeight = (height * scale).toFloat())
  }

  /**
   * Loads a HiDPI-aware image of the size specified in the svg file.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun loadHiDPI(url: URL?, stream: InputStream, context: ScaleContext): Image {
    val scale = context.getScale(DerivedScaleType.PIX_SCALE).toFloat()
    val image = load(path = url?.path, stream = stream, scale = scale, colorPatcherProvider = null)
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

  @ApiStatus.Internal
  fun getStrokePatcher(resultColor: Color,
                       strokeColors: List<String>,
                       backgroundColors: List<String> = emptyList()): SvgElementColorPatcherProvider {
    val fg = ColorUtil.toHtmlColor(resultColor)
    val map = strokeColors.associateWith { fg }
    val alpha = HashMap<String, Int>(map.size)
    for (s in map.values) {
      alpha.put(s, resultColor.alpha)
    }

    val hash = InsecureHashBuilder()
    hash.stringList(strokeColors)
    hash.stringList(backgroundColors)
    hash.update(fg)
    hash.update(resultColor.alpha)
    val digest = hash.build()
    return object : SvgElementColorPatcherProvider {
      override fun attributeForPath(path: String?): SvgAttributePatcher? {
        return newPatcher(digest = digest, newPalette = map + backgroundColors.associateWith { "#00000000" }, alphas = alpha)
      }

      override fun digest() = digest
    }
  }

  @JvmStatic
  var colorPatcherProvider: SvgElementColorPatcherProvider? = null
    set(colorPatcher) {
      field = colorPatcher
      IconLoader.clearCache()
    }

  fun newPatcher(digest: LongArray?, newPalette: Map<String, String>, alphas: Map<String, Int>): SvgAttributePatcher? {
    if (newPalette.isEmpty()) {
      return null
    }

    return object : SvgAttributePatcher {
      override fun digest(): LongArray? = digest

      override fun patchColors(attributes: MutableMap<String, String>) {
        patchColorAttribute(attributes = attributes, attributeName = "fill")
        patchColorAttribute(attributes = attributes, attributeName = "stroke")
      }

      private fun patchColorAttribute(attributes: MutableMap<String, String>, attributeName: String) {
        val color = attributes.get(attributeName) ?: return
        val opacity = attributes.get("$attributeName-opacity")
        var alpha = 255
        if (!opacity.isNullOrEmpty()) {
          try {
            alpha = ceil(255f * opacity.toFloat()).toInt()
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
          attributes.put(attributeName, newColor)
          alphas.get(newColor)?.let {
            attributes.put("$attributeName-opacity", (it.toFloat() / 255f).toString())
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

private fun toCanonicalColor(color: String): String {
  var s = color.lowercase()
  //todo[kb]: add support for red, white, black, and other named colors
  if (s.startsWith("#") && s.length < 7) {
    s = "#" + ColorUtil.toHex(ColorUtil.fromHex(s))
  }
  return s
}

internal fun loadSvgAndCacheIfApplicable(path: String?,
                                         scale: Float,
                                         compoundCacheKey: SvgCacheClassifier,
                                         colorPatcher: SvgAttributePatcher?,
                                         deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?,
                                         dataProvider: () -> ByteArray?): BufferedImage? {
  return loadAndCacheIfApplicable(path = path,
                                  precomputedCacheKey = 0,
                                  scale = scale,
                                  compoundCacheKey = compoundCacheKey,
                                  colorPatcher = colorPatcher,
                                  deprecatedColorPatcher = deprecatedColorPatcher,
                                  cache = SVGLoader.cache,
                                  dataProvider = dataProvider)
}

private inline fun loadAndCacheIfApplicable(path: String?,
                                            precomputedCacheKey: Int,
                                            scale: Float,
                                            compoundCacheKey: SvgCacheClassifier = SvgCacheClassifier(scale = scale),
                                            colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider?,
                                            @Suppress("SameParameterValue") cache: SvgCacheManager?,
                                            dataProvider: () -> ByteArray?): BufferedImage? {
  @Suppress("DEPRECATION")
  return loadAndCacheIfApplicable(path = path,
                                  precomputedCacheKey = precomputedCacheKey,
                                  scale = scale,
                                  compoundCacheKey = compoundCacheKey,
                                  colorPatcher = colorPatcherProvider?.attributeForPath(path),
                                  deprecatedColorPatcher = colorPatcherProvider?.forPath(path),
                                  cache = cache,
                                  dataProvider = dataProvider)
}

private inline fun loadAndCacheIfApplicable(path: String?,
                                            precomputedCacheKey: Int,
                                            scale: Float,
                                            compoundCacheKey: SvgCacheClassifier = SvgCacheClassifier(scale = scale),
                                            colorPatcher: SvgAttributePatcher?,
                                            deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?,
                                            @Suppress("SameParameterValue") cache: SvgCacheManager?,
                                            dataProvider: () -> ByteArray?): BufferedImage? {
  val colorPatcherDigest = colorPatcher?.digest() ?: deprecatedColorPatcher?.digest()?.let { longArrayOf(Xxh3.hash(it)) }
  if (cache == null ||
      (colorPatcherDigest == null && (colorPatcher != null || deprecatedColorPatcher != null)) ||
      scale > MAX_SCALE_TO_CACHE) {
    return renderImage(colorPatcher = colorPatcher,
                       deprecatedColorPatcher = deprecatedColorPatcher,
                       data = dataProvider() ?: return null,
                       scale = scale,
                       path = path)
  }

  val data = if (precomputedCacheKey == 0) (dataProvider() ?: return null) else null
  val themeKey = colorPatcherDigest?.let(::themeDigestToCacheKey) ?: 0
  try {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val result = if (data == null) {
      cache.loadPrecomputedFromCache(precomputedCacheKey = precomputedCacheKey, themeKey = themeKey, compoundKey = compoundCacheKey)
    }
    else {
      cache.loadFromCache(imageBytes = data, themeKey = themeKey, compoundKey = compoundCacheKey)
    }
    if (start != -1L) {
      IconLoadMeasurer.svgCacheRead.end(start)
    }

    result?.let {
      return it
    }
  }
  catch (e: Throwable) {
    logger<SVGLoader>().error("cannot load from icon cache (path=$path, precomputedCacheKey=$precomputedCacheKey)", e)
  }

  return renderAndCache(deprecatedColorPatcher = deprecatedColorPatcher,
                        colorPatcher = colorPatcher,
                        data = data ?: dataProvider() ?: return null,
                        scale = scale,
                        compoundCacheKey = compoundCacheKey,
                        path = path,
                        precomputedCacheKey = precomputedCacheKey,
                        themeKey = themeKey,
                        cache = cache)
}

private fun renderAndCache(deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?,
                           colorPatcher: SvgAttributePatcher?,
                           data: ByteArray,
                           scale: Float,
                           compoundCacheKey: SvgCacheClassifier,
                           path: String?,
                           precomputedCacheKey: Int,
                           themeKey: Long,
                           @Suppress("SameParameterValue") cache: SvgCacheManager): BufferedImage {
  val image = renderImage(colorPatcher = colorPatcher, deprecatedColorPatcher = deprecatedColorPatcher, data = data, scale = scale,
                          path = path)
  try {
    val cacheWriteStart = StartUpMeasurer.getCurrentTimeIfEnabled()
    cache.storeLoadedImage(precomputedCacheKey = precomputedCacheKey,
                           themeKey = themeKey,
                           imageBytes = data,
                           compoundKey = compoundCacheKey,
                           image = image)
    IconLoadMeasurer.svgCacheWrite.end(cacheWriteStart)
  }
  catch (e: Throwable) {
    logger<SVGLoader>().error("Failed to save icon to cache (path=$path, precomputedCacheKey=$precomputedCacheKey)", e)
  }
  return image
}

private fun renderImage(colorPatcher: SvgAttributePatcher?,
                        deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?,
                        data: ByteArray,
                        scale: Float,
                        path: String?): BufferedImage {
  val decodingStart = StartUpMeasurer.getCurrentTimeIfEnabled()
  val jsvgDocument = if (deprecatedColorPatcher == null) {
    if (colorPatcher == null) {
      createJSvgDocument(data)
    }
    else {
      createJSvgDocument(createXmlStreamReader(data), colorPatcher::patchColors)
    }
  }
  else {
    val documentElement = DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder().parse(data.inputStream()).documentElement
    deprecatedColorPatcher.patchColors(documentElement)

    val writer = StringWriter()
    TransformerFactory.newDefaultInstance().newTransformer().transform(DOMSource(documentElement), StreamResult(writer))
    createJSvgDocument(createXmlStreamReader(CharSequenceReader(writer.buffer)))
  }

  val bufferedImage = renderUsingJSvg(scale = scale, path = path, document = jsvgDocument)
  if (decodingStart != -1L) {
    IconLoadMeasurer.svgDecoding.end(decodingStart)
  }
  return bufferedImage
}

interface SvgAttributePatcher {
  fun patchColors(attributes: MutableMap<String, String>) {
  }

  /**
   * @return hash code of the current SVG color patcher or null to disable rendered SVG images caching
   */
  fun digest(): LongArray?
}