// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui.svg

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.hasher
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.icons.getResourceData
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.isHiDPIEnabledAndApplicable
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.SVGLoader
import com.intellij.util.createDocumentBuilder
import com.intellij.util.text.CharSequenceReader
import com.intellij.util.xml.dom.createXmlStreamReader
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter
import javax.swing.Icon
import javax.xml.XMLConstants
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.ceil

// https://youtrack.jetbrains.com/issue/IDEA-312509/mvstore.MVStoreException-on-zoom-SVG-with-text
private const val MAX_SCALE_TO_CACHE = 4

@get:Internal
val activeSvgCache: SvgCacheManager?
  get() = SvgCacheManager.svgCache?.takeIf { it.isActive() }

interface SvgAttributePatcher {
  fun patchColors(attributes: MutableMap<String, String>) {
  }

  /**
   * @return hash code of the current SVG color patcher or null to disable rendered SVG images caching
   */
  fun digest(): LongArray?
}

@Internal
fun loadSvg(data: ByteArray, scale: Float): BufferedImage {
  return loadAndCacheIfApplicable(path = null, precomputedCacheKey = 0, scale = scale, colorPatcherProvider = null) {
    data
  }!!
}

@Internal
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

@Internal
fun newSvgPatcher(digest: LongArray?, newPalette: Map<String, String>, alphaProvider: (String) -> Int?): SvgAttributePatcher {
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
  val s = color.lowercase()
  //todo[kb]: add support for red, white, black, and other named colors
  if (s.startsWith('#') && s.length < 7) {
    return "#" + ColorUtil.toHex(ColorHexUtil.fromHex(s))
  }
  else {
    return s
  }
}

@Internal
fun renderSvg(inputStream: InputStream, scale: Float = JBUIScale.sysScale(), path: String? = null): BufferedImage {
  return renderSvg(scale = scale, path = path, document = createJSvgDocument(inputStream))
}

@Internal
fun renderSvgWithSize(inputStream: InputStream, width: Float, height: Float, scale: Float = JBUIScale.sysScale()): BufferedImage {
  return renderSvgWithSize(document = createJSvgDocument(inputStream), width = width * scale, height = height * scale)
}

@Internal
@JvmOverloads
@Throws(IOException::class)
fun renderSvg(data: ByteArray, scale: Float = JBUIScale.sysScale()): BufferedImage {
  return renderSvg(scale = scale, document = createJSvgDocument(data = data))
}

@Internal
fun getSvgDocumentSize(data: ByteArray): Rectangle2D.Float {
  return withSvgSize(document = createJSvgDocument(data = data), baseWidth = 16f, baseHeight = 16f) { w, h ->
    Rectangle2D.Float(0f, 0f, w, h)
  }
}

internal fun loadSvgAndCacheIfApplicable(path: String?,
                                         scale: Float,
                                         compoundCacheKey: SvgCacheClassifier,
                                         colorPatcher: SvgAttributePatcher?,
                                         @Suppress("DEPRECATION") deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?,
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
                                  colorPatcher = path?.let { colorPatcherProvider?.attributeForPath(it) },
                                  deprecatedColorPatcher = path?.let { colorPatcherProvider?.forPath(path) },
                                  dataProvider = dataProvider)
}

private inline fun loadAndCacheIfApplicable(path: String?,
                                            precomputedCacheKey: Int,
                                            scale: Float,
                                            compoundCacheKey: SvgCacheClassifier = SvgCacheClassifier(scale = scale),
                                            colorPatcher: SvgAttributePatcher?,
                                            @Suppress("DEPRECATION") deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?,
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
  val key = if (data == null) {
    createPrecomputedIconCacheKey(precomputedCacheKey = precomputedCacheKey, compoundKey = compoundCacheKey, themeKey = themeKey)
  }
  else {
    createIconCacheKey(imageBytes = data, compoundKey = compoundCacheKey, themeKey = themeKey)
  }

  try {
    val start = StartUpMeasurer.getCurrentTimeIfEnabled()
    val result = svgCache.loadFromCache(key = key)
    if (start != -1L) {
      IconLoadMeasurer.svgCacheRead.end(start)
    }

    result?.let {
      return it
    }
  }
  catch (e: ProcessCanceledException) {
    // PHM can throw ProcessCanceledException
    throw e
  }
  catch (e: Throwable) {
    svgCache.markCorrupted()
    logger<SVGLoader>().warn("Cannot load from icon cache (path=$path, precomputedCacheKey=$precomputedCacheKey)", e)
  }

  return renderAndCache(deprecatedColorPatcher = deprecatedColorPatcher,
                        colorPatcher = colorPatcher,
                        data = data ?: dataProvider() ?: return null,
                        scale = scale,
                        path = path,
                        key = key,
                        cache = svgCache)
}

private fun themeDigestToCacheKey(themeDigest: LongArray): Long {
  return when (themeDigest.size) {
    0 -> 0
    1 -> themeDigest.first()
    2 -> hasher.hashLongLongToLong(themeDigest[0], themeDigest[1])
    3 -> hasher.hashLongLongLongToLong(themeDigest[0], themeDigest[1], themeDigest[2])
    else -> hasher.hashStream().putLongArray(themeDigest).asLong
  }
}

private fun renderAndCache(@Suppress("DEPRECATION") deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?,
                           colorPatcher: SvgAttributePatcher?,
                           data: ByteArray,
                           scale: Float,
                           path: String?,
                           key: LongArray,
                           cache: SvgCacheManager): BufferedImage {
  val image = renderImage(colorPatcher = colorPatcher,
                          deprecatedColorPatcher = deprecatedColorPatcher,
                          data = data,
                          scale = scale,
                          path = path)
  // maybe closed during rendering
  if (!cache.isActive()) {
    return image
  }

  try {
    val cacheWriteStart = StartUpMeasurer.getCurrentTimeIfEnabled()
    cache.storeLoadedImage(key = key, image = image)
    IconLoadMeasurer.svgCacheWrite.end(cacheWriteStart)
  }
  catch (e: ProcessCanceledException) {
    // PHM can throw ProcessCanceledException
    throw e
  }
  catch (e: Throwable) {
    if (cache.isActive()) {
      cache.markCorrupted()
      logger<SVGLoader>().error("Failed to save icon to cache (path=$path, key=$key)", e)
    }
  }
  return image
}

private fun renderImage(colorPatcher: SvgAttributePatcher?,
                        @Suppress("DEPRECATION") deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?,
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
    val documentElement = createDocumentBuilder(namespaceAware = true).parse(data.inputStream()).documentElement
    deprecatedColorPatcher.patchColors(documentElement)

    val writer = StringWriter()
    val transformerFactory = TransformerFactory.newDefaultInstance()
    transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
    transformerFactory.newTransformer().transform(DOMSource(documentElement), StreamResult(writer))
    createJSvgDocument(createXmlStreamReader(CharSequenceReader(writer.buffer)))
  }

  val bufferedImage = renderSvg(scale = scale, path = path, document = jsvgDocument)
  if (decodingStart != -1L) {
    IconLoadMeasurer.svgDecoding.end(decodingStart)
  }
  return bufferedImage
}

@Internal
fun loadWithSizes(sizes: List<Int>, data: ByteArray, scale: Float = JBUIScale.sysScale()): List<Image> {
  val svgCache = activeSvgCache
  val document by lazy(LazyThreadSafetyMode.NONE) { createJSvgDocument(data) }
  val isHiDpiNeeded = isHiDPIEnabledAndApplicable(scale)
  return sizes.map { size ->
    val compoundKey = SvgCacheClassifier(scale = scale, size = size)
    val key = createIconCacheKey(imageBytes = data, compoundKey = compoundKey, themeKey = 0)
    var image = svgCache?.loadFromCache(key)
    if (image == null) {
      image = renderSvgWithSize(document = document, width = (size * scale), height = (size * scale))
      svgCache?.storeLoadedImage(key = key, image = image)
    }

    if (isHiDpiNeeded) {
      JBHiDPIScaledImage(image, scale.toDouble())
    }
    else {
      image
    }
  }
}

private var selectionColorPatcher: SVGLoader.SvgElementColorPatcherProvider? = null

fun setSelectionColorPatcherProvider(colorPatcher: SVGLoader.SvgElementColorPatcherProvider?) {
  selectionColorPatcher = colorPatcher
  IconLoader.clearCache()
}

@Internal
fun paintIconWithSelection(icon: Icon, c: Component?, g: Graphics?, x: Int, y: Int) {
  val patcher = selectionColorPatcher
  if (patcher == null || !Registry.`is`("ide.patch.icons.on.selection", false)) {
    icon.paintIcon(c, g, x, y)
  }
  else {
    IconLoader.colorPatchedIcon(icon, patcher).paintIcon(c, g, x, y)
  }
}