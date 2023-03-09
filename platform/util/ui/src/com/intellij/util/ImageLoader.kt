// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "LiftReturnOrAssignment")

package com.intellij.util

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Weigher
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.icons.ImageDescriptor
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.JBUIScale.isHiDPI
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.SvgCacheClassifier
import com.intellij.ui.svg.renderSvg
import com.intellij.util.ImageCache.ioMissCache
import com.intellij.util.ImageLoader.convertImage
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import org.imgscalr.Scalr
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.xxh3.Xxh3
import java.awt.Component
import java.awt.Image
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.image.FilteredImageSource
import java.awt.image.ImageFilter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.math.roundToInt

private val LOG: Logger
  get() = logger<ImageLoader>()

internal val CACHED_IMAGE_MAX_SIZE: Long = (SystemProperties.getFloatProperty("ide.cached.image.max.size", 1.5f) * 1024 * 1024).toLong()

@Internal
fun loadImageByClassLoader(path: String,
                           classLoader: ClassLoader,
                           scaleContext: ScaleContext,
                           isDark: Boolean = StartupUiUtil.isUnderDarcula()): Image? {
  return loadImage(path = path, isDark = isDark, scaleContext = scaleContext, classLoader = classLoader)
}

internal fun loadImage(path: String,
                       resourceClass: Class<*>? = null,
                       classLoader: ClassLoader?,
                       scaleContext: ScaleContext,
                       isDark: Boolean = StartupUiUtil.isUnderDarcula(),
                       colorPatcherProvider: SvgElementColorPatcherProvider? = null,
                       filters: List<ImageFilter> = emptyList(),
                       useCache: Boolean = true): Image? {
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  val descriptors = createImageDescriptorList(path = path, isDark = isDark, scaleContext = scaleContext)

  val lastDotIndex = path.lastIndexOf('.')
  val rawPathWithoutExt: String
  val ext: String
  if (lastDotIndex == -1) {
    rawPathWithoutExt = path
    ext = "svg"
  }
  else {
    rawPathWithoutExt = path.substring(0, lastDotIndex)
    ext = path.substring(lastDotIndex + 1)
  }

  for ((i, descriptor) in descriptors.withIndex()) {
    try {
      // check only for the first one, as io miss cache doesn't have a scale
      var image: Image?
      if (useCache) {
        image = loadByDescriptor(rawPathWithoutExt = rawPathWithoutExt,
                                 ext = ext,
                                 descriptor = descriptor,
                                 resourceClass = resourceClass,
                                 classLoader = classLoader,
                                 ioMissCache = if (i == 0) ioMissCache else null,
                                 imageCache = ImageCache,
                                 ioMissCacheKey = path,
                                 colorPatcherProvider = colorPatcherProvider)
      }
      else {
        if (i == 0 && ioMissCache.contains(path)) {
          return null
        }

        image = loadByDescriptorWithoutCache(rawPathWithoutExt = rawPathWithoutExt,
                                             ext = ext,
                                             descriptor = descriptor,
                                             resourceClass = resourceClass,
                                             classLoader = classLoader,
                                             colorPatcherProvider = colorPatcherProvider)
      }

      if (image != null) {
        if (start != -1L) {
          IconLoadMeasurer.addLoading(isSvg = descriptor.isSvg, start = start)
        }

        return convertImage(image = image,
                            filters = filters,
                            scaleContext = scaleContext,
                            isUpScaleNeeded = !path.endsWith(".svg"),
                            imageScale = descriptor.scale,
                            isSvg = descriptor.isSvg)
      }
    }
    catch (e: IOException) {
      LOG.debug(e)
    }
  }

  ioMissCache.add(path)
  return null
}

object ImageLoader {
  @Suppress("unused")
  const val ALLOW_FLOAT_SCALING = 0x01

  @Suppress("unused")
  const val USE_CACHE = 0x02

  @Suppress("unused")
  const val USE_DARK = 0x04

  @Suppress("unused")
  const val USE_SVG = 0x08

  @get:Internal
  val ourComponent: Component by lazy { object : Component() {} }

  @Internal
  fun loadImageForStartUp(requestedPath: String, classLoader: ClassLoader): BufferedImage? {
    val scaleContext = ScaleContext.create()
    val descriptors = createImageDescriptorList(path = requestedPath, isDark = false, scaleContext = scaleContext)
    for (descriptor in descriptors) {
      try {
        val dotIndex = requestedPath.lastIndexOf('.')
        val pathWithoutExt = requestedPath.substring(0, dotIndex)
        val data = getResourceData(path = descriptor.pathTransform(pathWithoutExt, requestedPath.substring(dotIndex + 1)),
                                   resourceClass = null,
                                   classLoader = classLoader) ?: continue
        if (descriptor.isSvg) {
          return renderSvg(data = data, scale = descriptor.scale)
        }
        else {
          val image = loadPng(stream = ByteArrayInputStream(data), scale = descriptor.scale, originalUserSize = null)
          var scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
          if (descriptor.scale > 1) {
            // compensate the image original scale
            scale /= descriptor.scale
          }
          return scaleImage(image, scale.toDouble()) as BufferedImage
        }
      }
      catch (ignore: IOException) {
      }
    }
    return null
  }

  @Internal
  internal fun getResourceData(path: String, resourceClass: Class<*>?, classLoader: ClassLoader?): ByteArray? {
    assert(resourceClass != null || classLoader != null || path.startsWith("file://"))
    if (classLoader != null) {
      val isAbsolute = path.startsWith("/")
      val data = ResourceUtil.getResourceAsBytes(if (isAbsolute) path.substring(1) else path, classLoader, true)
      if (data != null || isAbsolute) {
        return data
      }
    }
    resourceClass?.getResourceAsStream(path)?.use { stream -> return stream.readAllBytes() }
    if (path.startsWith("file:/")) {
      val nioPath = Path.of(URI.create(path))
      try {
        return Files.readAllBytes(nioPath)
      }
      catch (e: NoSuchFileException) {
        return null
      }
      catch (e: IOException) {
        LOG.warn(e)
      }
    }
    return null
  }

  @Internal
  fun loadPngFromClassResource(path: String, classLoader: ClassLoader?, scale: Float, resourceClass: Class<*>? = null): BufferedImage? {
    val data = getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader) ?: return null
    return loadPng(stream = ByteArrayInputStream(data), scale = scale, originalUserSize = null)
  }

  @Internal
  fun loadFromStream(stream: InputStream,
                     path: String?,
                     scale: Float,
                     originalUserSize: Dimension2DDouble? = null,
                     isDark: Boolean,
                     useSvg: Boolean): Image {
    stream.use {
      if (useSvg) {
        val compoundCacheKey = SvgCacheClassifier(scale = scale, isDark = isDark, isStroke = false)
        return SVGLoader.load(path = path, stream = stream, scale = scale, compoundCacheKey = compoundCacheKey, colorPatcherProvider = null)
      }
      else {
        return loadPng(stream = stream, scale = scale, originalUserSize = originalUserSize)
      }
    }
  }

  @Internal
  fun convertImage(image: Image,
                   filters: List<ImageFilter>,
                   scaleContext: ScaleContext,
                   isUpScaleNeeded: Boolean,
                   isHiDpiNeeded: Boolean = StartupUiUtil.isJreHiDPI(scaleContext),
                   imageScale: Float,
                   isSvg: Boolean): Image {
    var result = image
    if (isUpScaleNeeded && !isSvg) {
      var scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
      if (imageScale > 1) {
        // compensate the image original scale
        scale /= imageScale
      }
      result = scaleImage(result, scale.toDouble())
    }
    if (!filters.isEmpty()) {
      val toolkit = Toolkit.getDefaultToolkit()
      for (filter in filters) {
        result = toolkit.createImage(FilteredImageSource(ImageUtil.toBufferedImage(result, false).source, filter))
      }
    }
    if (isHiDpiNeeded) {
      // The {originalUserSize} can contain calculation inaccuracy. If we use it to derive the HiDPI image scale
      // in JBHiDPIScaledImage, the derived scale will also be inaccurate and this will cause distortions
      // when the image is painted on a scaled (hidpi) screen graphics, see
      // StartupUiUtil.drawImage(Graphics, Image, Rectangle, Rectangle, BufferedImageOp, ImageObserver).
      //
      // To avoid that, we instead directly use the provided ScaleContext which contains correct ScaleContext.SYS_SCALE,
      // the image user space size will then be derived by JBHiDPIScaledImage (it is assumed the derived size is equal to
      // {originalUserSize} * DerivedScaleType.EFF_USR_SCALE, taking into account calculation accuracy).
      result = JBHiDPIScaledImage(result, scaleContext, BufferedImage.TYPE_INT_ARGB)
    }
    return result
  }

  fun getImageDescriptors(path: String, isDark: Boolean, scaleContext: ScaleContext): List<ImageDescriptor> {
    return createImageDescriptorList(path = path, isDark = isDark, scaleContext = scaleContext)
  }

  @JvmStatic
  fun loadFromUrl(url: URL): Image? {
    val path = url.toString()
    return loadImage(path = path, scaleContext = ScaleContext.create(), classLoader = null)
  }

  @TestOnly
  fun loadFromUrlWithoutCache(path: String, scaleContext: ScaleContext): Image? {
    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // In IDE-managed HiDPI mode with a scale > 1.0 we scale images manually - pass isUpScaleNeeded = true
    return loadImage(path = path, useCache = false, classLoader = null, scaleContext = scaleContext)
  }

  @JvmStatic
  fun scaleImage(image: Image, scale: Double): Image {
    if (scale == 1.0) {
      return image
    }
    if (image is JBHiDPIScaledImage) {
      return image.scale(scale)
    }
    val w = image.getWidth(null)
    val h = image.getHeight(null)
    if (w <= 0 || h <= 0) {
      return image
    }
    val width = (scale * w).roundToInt()
    val height = (scale * h).roundToInt()
    // Using "QUALITY" instead of "ULTRA_QUALITY" results in images that are less blurry
    // because ultra quality performs a few more passes when scaling, which introduces blurriness
    // when the scaling factor is relatively small (i.e. <= 3.0f) -- which is the case here.
    return Scalr.resize(ImageUtil.toBufferedImage(image, false), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, width, height, null)
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
    return loadImage(path = path,
                     scaleContext = ScaleContext.create(),
                     resourceClass = aClass,
                     classLoader = null)
  }

  @JvmStatic
  fun loadFromBytes(bytes: ByteArray): Image? {
    return loadFromStream(ByteArrayInputStream(bytes))
  }

  @JvmStatic
  fun loadFromStream(inputStream: InputStream): Image? {
    // for backward compatibility assume the image is hidpi-aware (includes default SYS_SCALE)
    val scaleContext = ScaleContext.create()
    try {
      inputStream.use {
        val originalUserSize = Dimension2DDouble(0.0, 0.0)
        val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
        var image: Image? = loadPng(inputStream, scale, originalUserSize)
        if (StartupUiUtil.isJreHiDPI(scaleContext)) {
          val userScale = scaleContext.getScale(DerivedScaleType.EFF_USR_SCALE)
          image = JBHiDPIScaledImage(image!!, originalUserSize.width * userScale, originalUserSize.height * userScale,
                                     BufferedImage.TYPE_INT_ARGB)
        }
        return image
      }
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    return null
  }

  @Suppress("unused")
  @Throws(IOException::class)
  @JvmStatic
  fun loadCustomIcon(file: File): Image? {
    return loadCustomIcon(file.toURI().toURL())
  }

  @Throws(IOException::class)
  fun loadCustomIcon(url: URL): Image? {
    val path = url.toString()
    val scaleContext = ScaleContext.create()
    // probably, need it implements naming conventions: filename ends with @2x => HiDPI (scale=2)
    val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
    val imageDescriptor = ImageDescriptor(pathTransform = { p, e -> "$p.$e" }, scale = scale,
                                          isSvg = path.endsWith(".svg", ignoreCase = true),
                                          isDark = path.contains("_dark."),
                                          isStroke = path.contains("_stroke."))

    val lastDotIndex = path.lastIndexOf('.')
    val rawPathWithoutExt: String
    val ext: String
    if (lastDotIndex == -1) {
      rawPathWithoutExt = path
      ext = "png"
    }
    else {
      rawPathWithoutExt = path.substring(0, lastDotIndex)
      ext = path.substring(lastDotIndex + 1)
    }
    val icon = ImageUtil.ensureHiDPI(
      loadByDescriptor(rawPathWithoutExt = rawPathWithoutExt,
                       ext = ext,
                       descriptor = imageDescriptor,
                       resourceClass = null,
                       classLoader = null,
                       ioMissCache = null,
                       imageCache = ImageCache,
                       ioMissCacheKey = null,
                       colorPatcherProvider = null),
      scaleContext) ?: return null
    val w = icon.getWidth(null)
    val h = icon.getHeight(null)
    if (w <= 0 || h <= 0) {
      LOG.error("negative image size: w=$w, h=$h, path=$path")
      return null
    }

    if (w > EmptyIcon.ICON_18.iconWidth || h > EmptyIcon.ICON_18.iconHeight) {
      val s = EmptyIcon.ICON_18.iconWidth / w.coerceAtLeast(h).toDouble()
      return scaleImage(icon, s)
    }
    return icon
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

private fun createImageDescriptorList(path: String, isDark: Boolean, scaleContext: ScaleContext): List<ImageDescriptor> {
  // prefer retina images for HiDPI scale, because downscaling retina images provide a better result than up-scaling non-retina images
  if (!path.startsWith("file:") && path.contains("://")) {
    val qI = path.lastIndexOf('?')
    val isSvg = (if (qI == -1) path else path.substring(0, qI)).endsWith(".svg", ignoreCase = true)
    return listOf(ImageDescriptor(pathTransform = { p, e -> "$p.$e" }, scale = 1f, isSvg = isSvg, isDark = isDark, isStroke = false))
  }

  val pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
  val isSvg = path.endsWith(".svg")
  val isRetina = isHiDPI(pixScale.toDouble())

  val list = ArrayList<ImageDescriptor>()
  if (!isSvg) {
    list.add(if (isRetina) ImageDescriptor.STROKE_RETINA else ImageDescriptor.STROKE_NON_RETINA)
    addFileNameVariant(isRetina = isRetina, isDark = isDark, isSvg = false, scale = pixScale, list = list)
  }

  addFileNameVariant(isRetina = isRetina, isDark = isDark, isSvg = isSvg, scale = pixScale, list = list)

  // fallback to non-dark
  if (isDark) {
    addFileNameVariant(isRetina = isRetina, isDark = false, isSvg = isSvg, scale = pixScale, list = list)
    if (!isSvg) {
      addFileNameVariant(isRetina = false, isDark = false, isSvg = true, scale = pixScale, list = list)
    }
  }
  return list
}

internal fun clearImageCache() {
  ImageCache.clearCache()
}

private object ImageCache {
  @JvmField
  val ioMissCache: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

  @JvmField
  val imageCache: Cache<CacheKey, BufferedImage> = Caffeine.newBuilder()
    .expireAfterAccess(1, TimeUnit.HOURS)
    // 32 MB
    .maximumWeight(32 * 1024 * 1024)
    .weigher(Weigher { _: CacheKey, value: BufferedImage -> 4 * value.width * value.height })
    .build()

  fun clearCache() {
    imageCache.invalidateAll()
    ioMissCache.clear()
  }
}

// @2x is used even for SVG icons by intention
private fun addFileNameVariant(isRetina: Boolean,
                               isDark: Boolean,
                               isSvg: Boolean,
                               scale: Float,
                               list: MutableList<ImageDescriptor>) {
  val retinaScale = if (isSvg) scale else 2f
  val nonRetinaScale = if (isSvg) scale else 1f
  if (isDark) {
    val d1 = ImageDescriptor(pathTransform = { p, e -> "${p}@2x_dark.$e" }, scale = retinaScale, isSvg = isSvg, isDark = true)
    val d2 = ImageDescriptor(pathTransform = { p, e -> "${p}_dark@2x.$e" }, scale = retinaScale, isSvg = isSvg, isDark = true)
    val d3 = ImageDescriptor(pathTransform = { p, e -> "${p}_dark.$e" }, scale = nonRetinaScale, isSvg = isSvg, isDark = true)

    if (isRetina) {
      list.add(d1)
      list.add(d2)
      list.add(d3)
    }
    else {
      list.add(d3)
      list.add(d2)
      list.add(d1)
    }
  }
  else {
    val d1 = ImageDescriptor(pathTransform = { p, e -> "${p}@2x.$e" }, scale = retinaScale, isSvg = isSvg)
    val d2 = ImageDescriptor(pathTransform = { p, e -> "${p}.$e" }, scale = nonRetinaScale, isSvg = isSvg)

    if (isRetina) {
      list.add(d1)
      list.add(d2)
    }
    else {
      list.add(d2)
      list.add(d1)
    }
  }
}

@Suppress("DuplicatedCode")
private fun loadByDescriptorWithoutCache(rawPathWithoutExt: String,
                                         ext: String,
                                         descriptor: ImageDescriptor,
                                         resourceClass: Class<*>?,
                                         classLoader: ClassLoader?,
                                         colorPatcherProvider: SvgElementColorPatcherProvider?): Image? {
  val path = descriptor.pathTransform(rawPathWithoutExt, ext)
  @Suppress("DEPRECATION")
  return doLoadByDescriptor(path = path,
                            descriptor = descriptor,
                            resourceClass = resourceClass,
                            classLoader = classLoader,
                            colorPatcher = colorPatcherProvider?.attributeForPath(path),
                            deprecatedColorPatcher = colorPatcherProvider?.forPath(path))
}


private fun loadByDescriptor(rawPathWithoutExt: String,
                             ext: String,
                             descriptor: ImageDescriptor,
                             resourceClass: Class<*>?,
                             classLoader: ClassLoader?,
                             ioMissCache: Set<String?>?,
                             imageCache: ImageCache,
                             ioMissCacheKey: String?,
                             colorPatcherProvider: SvgElementColorPatcherProvider?): Image? {
  var tmpPatcher = false
  var digest: LongArray? = null

  val path = descriptor.pathTransform(rawPathWithoutExt, ext)

  var colorPatcher: SvgAttributePatcher? = null
  var deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher? = null
  if (colorPatcherProvider != null) {
    colorPatcher = colorPatcherProvider.attributeForPath(path)
    if (colorPatcher == null) {
      @Suppress("DEPRECATION")
      deprecatedColorPatcher = colorPatcherProvider.forPath(path)
      if (deprecatedColorPatcher != null) {
        digest = deprecatedColorPatcher.digest()?.let { longArrayOf(Xxh3.hash(it), Xxh3.seededHash(it, 4849512324275L)) }
        if (digest == null) {
          tmpPatcher = true
        }
      }
    }
    else {
      digest = colorPatcher.digest()
      if (digest == null) {
        tmpPatcher = true
      }
    }
  }

  if (digest == null) {
    digest = ArrayUtilRt.EMPTY_LONG_ARRAY!!
  }

  var cacheKey: CacheKey? = null
  if (!tmpPatcher) {
    cacheKey = CacheKey(path = path, scale = (if (descriptor.isSvg) descriptor.scale else 0f), digest = digest)
    imageCache.imageCache.getIfPresent(cacheKey)?.let {
      return it
    }
  }

  if (ioMissCache != null && ioMissCache.contains(ioMissCacheKey)) {
    return null
  }

  val image = doLoadByDescriptor(path = path,
                                 descriptor = descriptor,
                                 resourceClass = resourceClass,
                                 classLoader = classLoader,
                                 colorPatcher = colorPatcher,
                                 deprecatedColorPatcher = deprecatedColorPatcher) ?: return null
  if (cacheKey != null) {
    imageCache.imageCache.put(cacheKey, image)
  }
  return image
}

private fun doLoadByDescriptor(path: String,
                               descriptor: ImageDescriptor,
                               resourceClass: Class<*>?,
                               classLoader: ClassLoader?,
                               colorPatcher: SvgAttributePatcher?,
                               deprecatedColorPatcher: SVGLoader.SvgElementColorPatcher?): BufferedImage? {
  var image: BufferedImage?
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  if (resourceClass == null && (classLoader == null || URLUtil.containsScheme(path)) && !path.startsWith("file://")) {
    val connection = URL(path).openConnection()
    (connection as? HttpURLConnection)?.addRequestProperty("User-Agent", "IntelliJ")
    connection.getInputStream().use { stream ->
      image = if (descriptor.isSvg) {
        loadSvgAndCacheIfApplicable(path = path,
                                    scale = descriptor.scale,
                                    compoundCacheKey = descriptor.toSvgMapper(),
                                    colorPatcher = colorPatcher,
                                    deprecatedColorPatcher = deprecatedColorPatcher) { stream.readAllBytes() }
      }
      else {
        loadPng(stream = stream, scale = descriptor.scale, originalUserSize = null)
      }
    }
    if (start != -1L) {
      IconLoadMeasurer.loadFromUrl.end(start)
    }
  }
  else {
    image = if (descriptor.isSvg) {
      loadSvgAndCacheIfApplicable(path = path,
                                  scale = descriptor.scale,
                                  compoundCacheKey = descriptor.toSvgMapper(),
                                  colorPatcher = colorPatcher,
                                  deprecatedColorPatcher = deprecatedColorPatcher) {
        ImageLoader.getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader)
      }
    }
    else {
      ImageLoader.loadPngFromClassResource(path = path,
                                           resourceClass = resourceClass,
                                           classLoader = classLoader,
                                           scale = descriptor.scale)
    }
    if (start != -1L) {
      IconLoadMeasurer.loadFromResources.end(start)
    }
  }
  return image
}

fun loadPng(stream: InputStream, scale: Float, originalUserSize: ImageLoader.Dimension2DDouble? = null): BufferedImage {
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  var image: BufferedImage
  val reader = ImageIO.getImageReadersByFormatName("png").next()
  try {
    MemoryCacheImageInputStream(stream).use { imageInputStream ->
      reader.setInput(imageInputStream, true, true)
      image = reader.read(0, null)
    }
  }
  finally {
    reader.dispose()
  }
  originalUserSize?.setSize((image.width / scale).toDouble(), (image.height / scale).toDouble())
  if (start != -1L) {
    IconLoadMeasurer.pngDecoding.end(start)
  }
  return image
}

private class CacheKey(private val path: String, private val scale: Float, private val digest: LongArray) {
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    val key = other as CacheKey
    return key.scale == scale && path == key.path && key.digest.contentEquals(digest)
  }

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + java.lang.Float.floatToIntBits(scale)
    result = 31 * result + digest.contentHashCode()
    return result
  }
}