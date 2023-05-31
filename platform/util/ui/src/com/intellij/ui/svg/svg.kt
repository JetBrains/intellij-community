// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.ColorUtil
import com.intellij.ui.hasher
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.icons.getResourceData
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.isHiDPIEnabledAndApplicable
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.SVGLoader
import com.intellij.util.text.CharSequenceReader
import com.intellij.util.xml.dom.createXmlStreamReader
import org.jetbrains.annotations.ApiStatus
import java.awt.Image
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.ceil

// https://youtrack.jetbrains.com/issue/IDEA-312509/mvstore.MVStoreException-on-zoom-SVG-with-text
private const val MAX_SCALE_TO_CACHE = 4

@Volatile
@ApiStatus.Internal
@JvmField
var svgCache: SvgCacheManager? = null

internal val activeSvgCache: SvgCacheManager?
  get() = svgCache?.takeIf { it.isActive() }

interface SvgAttributePatcher {
  fun patchColors(attributes: MutableMap<String, String>) {
  }

  /**
   * @return hash code of the current SVG color patcher or null to disable rendered SVG images caching
   */
  fun digest(): LongArray?
}

@ApiStatus.Internal
fun loadSvg(data: ByteArray, scale: Float): Image {
  return loadAndCacheIfApplicable(path = null, precomputedCacheKey = 0, scale = scale, colorPatcherProvider = null) {
    data
  }!!
}

@ApiStatus.Internal
fun loadSvgFromClassResource(classLoader: ClassLoader?,
                             path: String,
                             precomputedCacheKey: Int,
                             scale: Float,
                             compoundCacheKey: SvgCacheClassifier,
                             colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider?,
                             resourceClass: Class<*>? = null): Image? {
  return loadAndCacheIfApplicable(path = path,
                                  precomputedCacheKey = precomputedCacheKey,
                                  scale = scale,
                                  compoundCacheKey = compoundCacheKey,
                                  colorPatcherProvider = colorPatcherProvider) {
    getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader)
  }
}

internal fun loadSvg(path: String?,
                     stream: InputStream,
                     scale: Float,
                     compoundCacheKey: SvgCacheClassifier? = null,
                     colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider?): BufferedImage {
  return loadAndCacheIfApplicable(path = path,
                                  precomputedCacheKey = 0,
                                  scale = scale,
                                  compoundCacheKey = compoundCacheKey ?: SvgCacheClassifier(scale),
                                  colorPatcherProvider = colorPatcherProvider) {
    stream.readAllBytes()
  }!!
}

@ApiStatus.Internal
fun newSvgPatcher(digest: LongArray?, newPalette: Map<String, String>, alphaProvider: (String) -> Int?): SvgAttributePatcher? {
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
        alphaProvider(newColor)?.let {
          attributes.put("$attributeName-opacity", (it.toFloat() / 255f).toString())
        }
      }
    }
  }
}

private fun toCanonicalColor(color: String): String {
  var s = color.lowercase()
  //todo[kb]: add support for red, white, black, and other named colors
  if (s.startsWith('#') && s.length < 7) {
    s = "#" + ColorUtil.toHex(ColorUtil.fromHex(s))
  }
  return s
}

@ApiStatus.Internal
fun renderSvg(inputStream: InputStream, scale: Float = JBUIScale.sysScale(), path: String? = null): BufferedImage {
  return renderSvg(scale = scale, path = path, document = createJSvgDocument(inputStream))
}

@ApiStatus.Internal
fun renderSvgWithSize(inputStream: InputStream, width: Float, height: Float, scale: Float = JBUIScale.sysScale()): BufferedImage {
  return renderSvgWithSize(document = createJSvgDocument(inputStream), width = width * scale, height = height * scale)
}

@ApiStatus.Internal
@JvmOverloads
@Throws(IOException::class)
fun renderSvg(data: ByteArray, scale: Float = JBUIScale.sysScale()): BufferedImage {
  return renderSvg(scale = scale, document = createJSvgDocument(data = data))
}

@ApiStatus.Internal
fun getSvgDocumentSize(data: ByteArray): Rectangle2D.Float {
  return withSvgSize(document = createJSvgDocument(data = data), baseWidth = 16f, baseHeight = 16f) { w, h ->
    Rectangle2D.Float(0f, 0f, w, h)
  }
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
                                  dataProvider = dataProvider)
}

private inline fun loadAndCacheIfApplicable(path: String?,
                                            precomputedCacheKey: Int,
                                            scale: Float,
                                            compoundCacheKey: SvgCacheClassifier = SvgCacheClassifier(scale = scale),
                                            colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider?,
                                            dataProvider: () -> ByteArray?): BufferedImage? {
  @Suppress("DEPRECATION")
  return loadAndCacheIfApplicable(path = path,
                                  precomputedCacheKey = precomputedCacheKey,
                                  scale = scale,
                                  compoundCacheKey = compoundCacheKey,
                                  colorPatcher = colorPatcherProvider?.attributeForPath(path),
                                  deprecatedColorPatcher = colorPatcherProvider?.forPath(path),
                                  dataProvider = dataProvider)
}

private inline fun loadAndCacheIfApplicable(path: String?,
                                            precomputedCacheKey: Int,
                                            scale: Float,
                                            compoundCacheKey: SvgCacheClassifier = SvgCacheClassifier(scale = scale),
                                            colorPatcher: SvgAttributePatcher?,
                                            deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?,
                                            dataProvider: () -> ByteArray?): BufferedImage? {
  val colorPatcherDigest = colorPatcher?.digest() ?: deprecatedColorPatcher?.digest()?.let { longArrayOf(hasher.hashBytesToLong(it)) }
  val svgCache = activeSvgCache
  if (svgCache == null ||
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
      svgCache.loadPrecomputedFromCache(precomputedCacheKey = precomputedCacheKey, themeKey = themeKey, compoundKey = compoundCacheKey)
    }
    else {
      svgCache.loadFromCache(imageBytes = data, themeKey = themeKey, compoundKey = compoundCacheKey)
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
                        cache = svgCache)
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
  // maybe closed during rendering
  if (cache.isActive()) {
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
      if (cache.isActive()) {
        logger<SVGLoader>().error("Failed to save icon to cache (path=$path, precomputedCacheKey=$precomputedCacheKey)", e)
      }
    }
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

  val bufferedImage = renderSvg(scale = scale, path = path, document = jsvgDocument)
  if (decodingStart != -1L) {
    IconLoadMeasurer.svgDecoding.end(decodingStart)
  }
  return bufferedImage
}

@ApiStatus.Internal
fun loadWithSizes(sizes: List<Int>, data: ByteArray, scale: Float = JBUIScale.sysScale()): List<Image> {
  val svgCache = activeSvgCache
  val document by lazy(LazyThreadSafetyMode.NONE) { createJSvgDocument(data) }
  val isHiDpiNeeded = isHiDPIEnabledAndApplicable(scale)
  return sizes.map { size ->
    val compoundKey = SvgCacheClassifier(scale = scale, size = size)
    var image = svgCache?.loadFromCache(imageBytes = data, themeKey = 0, compoundKey = compoundKey)
    if (image == null) {
      image = renderSvgWithSize(document = document, width = (size * scale), height = (size * scale))
      svgCache?.storeLoadedImage(precomputedCacheKey = 0, themeKey = 0, imageBytes = data, compoundKey = compoundKey, image = image)
    }

    if (isHiDpiNeeded) {
      JBHiDPIScaledImage(image, scale.toDouble())
    }
    else {
      image
    }
  }
}