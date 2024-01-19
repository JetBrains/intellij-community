// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui.svg

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.DummyIcon
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.LazyIcon
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorHexUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.IconManager
import com.intellij.ui.icons.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.isHiDPIEnabledAndApplicable
import com.intellij.util.ArrayUtilRt
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.SVGLoader
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.xml.dom.createXmlStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import javax.swing.Icon
import kotlin.math.ceil
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

const val ATTR_ID: String = "id"
const val ATTR_FILL: String = "fill"
const val ATTR_FILL_OPACITY: String = "fill-opacity"
const val ATTR_STROKE: String = "stroke"
const val ATTR_STROKE_OPACITY: String = "stroke-opacity"

// https://youtrack.jetbrains.com/issue/IDEA-312509/mvstore.MVStoreException-on-zoom-SVG-with-text
private const val MAX_SCALE_TO_CACHE = 4

@get:Internal
val activeSvgCache: SvgCacheManager?
  get() = SvgCacheManager.svgCache?.takeIf { it.isActive() }

interface SvgAttributePatcher {
  fun patchColors(attributes: MutableMap<String, String>) {
  }
}

@Internal
fun loadSvg(data: ByteArray, scale: Float): BufferedImage {
  return loadAndCacheIfApplicable(path = null,
                                  precomputedCacheKey = 0,
                                  scale = scale,
                                  colorPatcher = null,
                                  colorPatcherDigest = ArrayUtilRt.EMPTY_LONG_ARRAY) {
    data
  }!!
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
                                  colorPatcherDigest = colorPatcherDigestShim(colorPatcherProvider),
                                  colorPatcher = path?.let { colorPatcherProvider?.attributeForPath(it) },) {
    stream.readAllBytes()
  }!!
}

internal fun colorPatcherDigestShim(colorPatcherProvider: SVGLoader.SvgElementColorPatcherProvider?): LongArray {
  if (colorPatcherProvider == null) {
    return ArrayUtilRt.EMPTY_LONG_ARRAY
  }

  try {
    return colorPatcherProvider.digest()
  }
  catch (e: AbstractMethodError) {
    return longArrayOf(IconManager.getInstance().hashClass(colorPatcherProvider::class.java))
  }
}

@Internal
fun newSvgPatcher(newPalette: Map<String, String>, alphaProvider: (String) -> Int?): SvgAttributePatcher {
  return object : SvgAttributePatcher {
    override fun patchColors(attributes: MutableMap<String, String>) {
      patchColorAttribute(attributes = attributes, attributeName = ATTR_FILL)
      patchColorAttribute(attributes = attributes, attributeName = ATTR_STROKE)
    }

    private fun patchColorAttribute(attributes: MutableMap<String, String>, attributeName: String) {
      val color = attributes.get(attributeName) ?: return
      val opacityAttributeName = "$attributeName-opacity"
      val opacity = attributes.get(opacityAttributeName)
      var alpha = 255
      if (!opacity.isNullOrEmpty()) {
        try {
          alpha = ceil(255f * opacity.toFloat()).toInt()
        }
        catch (_: Exception) {
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
          if (it == 255) {
            attributes.remove(opacityAttributeName)
          }
          else {
            attributes.put(opacityAttributeName, (it.toFloat() / 255f).toString())
          }
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
                                         colorPatcherDigest: LongArray,
                                         dataProvider: () -> ByteArray?): BufferedImage? {
  return loadAndCacheIfApplicable(path = path,
                                  precomputedCacheKey = 0,
                                  scale = scale,
                                  compoundCacheKey = compoundCacheKey,
                                  colorPatcher = colorPatcher,
                                  colorPatcherDigest = colorPatcherDigest,
                                  dataProvider = dataProvider)
}

internal inline fun loadAndCacheIfApplicable(path: String?,
                                             precomputedCacheKey: Int,
                                             scale: Float,
                                             compoundCacheKey: SvgCacheClassifier = SvgCacheClassifier(scale = scale),
                                             colorPatcher: SvgAttributePatcher?,
                                             colorPatcherDigest: LongArray,
                                             dataProvider: () -> ByteArray?): BufferedImage? {
  val svgCache = activeSvgCache
  if (svgCache == null || scale > MAX_SCALE_TO_CACHE) {
    return renderImage(colorPatcher = colorPatcher, data = dataProvider() ?: return null, scale = scale, path = path)
  }

  val data = if (precomputedCacheKey == 0) (dataProvider() ?: return null) else null
  val key = if (data == null) {
    createPrecomputedIconCacheKey(precomputedCacheKey = precomputedCacheKey,
                                  compoundKey = compoundCacheKey,
                                  colorPatcherDigest = colorPatcherDigest)
  }
  else {
    createIconCacheKey(imageBytes = data, compoundKey = compoundCacheKey, colorPatcherDigest = colorPatcherDigest)
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

  return renderAndCache(colorPatcher = colorPatcher,
                        data = data ?: dataProvider() ?: return null,
                        scale = scale,
                        path = path,
                        key = key,
                        cache = svgCache)
}

private fun renderAndCache(colorPatcher: SvgAttributePatcher?,
                           data: ByteArray,
                           scale: Float,
                           path: String?,
                           key: LongArray,
                           cache: SvgCacheManager): BufferedImage {
  val image = renderImage(colorPatcher = colorPatcher, data = data, scale = scale, path = path)
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

private fun renderImage(colorPatcher: SvgAttributePatcher?, data: ByteArray, scale: Float, path: String?): BufferedImage {
  val decodingStart = StartUpMeasurer.getCurrentTimeIfEnabled()
  val jsvgDocument = if (colorPatcher == null) {
    createJSvgDocument(data)
  }
  else {
    createJSvgDocument(createXmlStreamReader(data), colorPatcher::patchColors)
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
    val key = createIconCacheKey(imageBytes = data, compoundKey = compoundKey, colorPatcherDigest = null)
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
    colorPatchedIcon(icon = icon, colorPatcher = patcher).paintIcon(c, g, x, y)
  }
}

private val colorPatchCache = CollectionFactory.createConcurrentWeakValueMap<SVGLoader.SvgElementColorPatcherProvider, LoadingCache<Icon, Icon>>().also {
  registerIconCacheCleaner(it::clear)
}

/**
 * Creates a new icon with the color patching applied.
 */
@Internal
fun colorPatchedIcon(icon: Icon, colorPatcher: SVGLoader.SvgElementColorPatcherProvider): Icon {
  if (icon is DummyIcon || icon is EmptyIcon) {
    return icon
  }

  return colorPatchCache
    .computeIfAbsent(colorPatcher) {
      Caffeine.newBuilder()
        .maximumSize(64)
        .executor(Dispatchers.Default.asExecutor())
        .expireAfterAccess(10.minutes.toJavaDuration())
        .build {
          patchIconsWithColorPatcher(icon = it, colorPatcher = colorPatcher)
        }
    }
    .get(icon)
}

private fun patchIconsWithColorPatcher(icon: Icon, colorPatcher: SVGLoader.SvgElementColorPatcherProvider): Icon {
  return replaceCachedImageIcons(icon) { cachedImageIcon ->
    cachedImageIcon.createWithPatcher(colorPatcher)
  }!!
}

/**
 * Creates a new icon with the low-level CachedImageIcon changing
 */
internal fun replaceCachedImageIcons(icon: Icon, cachedImageIconReplacer: (CachedImageIcon) -> Icon): Icon? {
  if (icon is CachedImageIcon) {
    return cachedImageIconReplacer(icon)
  }

  return object : IconReplacer {
    override fun replaceIcon(icon: Icon?): Icon? {
      return when {
        icon == null || icon is DummyIcon || icon is EmptyIcon -> icon
        icon is LazyIcon -> replaceIcon(icon.getOrComputeIcon())
        icon is ReplaceableIcon -> icon.replaceBy(this)
        !checkIconSize(icon) -> EMPTY_ICON
        icon is CachedImageIcon -> cachedImageIconReplacer(icon)
        else -> icon
      }
    }
  }.replaceIcon(icon)
}