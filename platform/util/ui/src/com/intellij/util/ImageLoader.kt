// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "LiftReturnOrAssignment")

package com.intellij.util

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.ui.icons.IconLoadMeasurer
import com.intellij.ui.icons.ImageDescriptor
import com.intellij.ui.icons.LoadIconParameters
import com.intellij.ui.icons.LoadIconParameters.Companion.defaultParameters
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.JBUIScale.isHiDPI
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.SvgCacheMapper
import com.intellij.ui.svg.renderSvg
import com.intellij.util.ImageLoader.USE_CACHE
import com.intellij.util.ImageLoader.convertImage
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import org.imgscalr.Scalr
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
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
import java.util.concurrent.ConcurrentMap
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.swing.Icon
import kotlin.math.roundToInt

private val LOG: Logger
  get() = logger<ImageLoader>()

// Some duplication here: isDark presents in parameters and in flags
@Internal
fun loadImage(path: String,
              parameters: LoadIconParameters,
              resourceClass: Class<*>? = null,
              classLoader: ClassLoader?,
              @MagicConstant(flagsFromClass = ImageLoader::class) flags: Int,
              isUpScaleNeeded: Boolean): Image? {
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  val descriptors = createImageDescriptorList(path = path, flags = flags, scaleContext = parameters.scaleContext)
  val imageCache = ImageCache.INSTANCE
  var ioExceptionThrown = false
  for (i in descriptors.indices) {
    val descriptor = descriptors.get(i)
    ioExceptionThrown = try {
      // check only for the first one, as io miss cache doesn't have a scale
      val image = loadByDescriptor(descriptor = descriptor,
                                   flags = flags,
                                   resourceClass = resourceClass,
                                   classLoader = classLoader,
                                   ioMissCache = if (i == 0) imageCache.ioMissCache else null,
                                   imageCache = imageCache,
                                   ioMissCacheKey = path,
                                   colorPatcherProvider = parameters.colorPatcher) ?: continue
      if (start != -1L) {
        IconLoadMeasurer.addLoading(isSvg = descriptor.isSvg, start = start)
      }

      val isHiDpiNeeded = StartupUiUtil.isJreHiDPI(parameters.scaleContext)
      return convertImage(image = image,
                          filters = parameters.filters,
                          flags = flags,
                          scaleContext = parameters.scaleContext,
                          isUpScaleNeeded = isUpScaleNeeded,
                          isHiDpiNeeded = isHiDpiNeeded,
                          imageScale = descriptor.scale,
                          isSvg = descriptor.isSvg)
    }
    catch (e: IOException) {
      true
    }
  }
  if (ioExceptionThrown) {
    imageCache.ioMissCache.add(path)
  }
  return null
}

object ImageLoader {
  const val ALLOW_FLOAT_SCALING = 0x01
  const val USE_CACHE = 0x02
  const val USE_DARK = 0x04
  const val USE_SVG = 0x08

  @get:Internal
  val ourComponent: Component by lazy { object : Component() {} }

  @Internal
  fun isIconTooLargeForCache(icon: Icon): Boolean {
    return 4L * icon.iconWidth * icon.iconHeight > CACHED_IMAGE_MAX_SIZE
  }

  @Internal
  fun loadImageForStartUp(requestedPath: String, classLoader: ClassLoader): BufferedImage? {
    val scaleContext = ScaleContext.create()
    val descriptors = createImageDescriptorList(requestedPath, ALLOW_FLOAT_SCALING, scaleContext)
    for (descriptor in descriptors) {
      try {
        val data = getResourceData(path = descriptor.path, resourceClass = null, classLoader = classLoader) ?: continue
        val image: Image
        if (descriptor.isSvg) {
          return renderSvg(data = data, scale = descriptor.scale)
        }
        else {
          image = loadPng(stream = ByteArrayInputStream(data), scale = descriptor.scale, originalUserSize = null)
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
  fun loadPngFromClassResource(path: String, classLoader: ClassLoader?, scale: Float, resourceClass: Class<*>? = null): Image? {
    val data = getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader) ?: return null
    return loadPng(stream = ByteArrayInputStream(data), scale = scale, originalUserSize = null)
  }

  @Internal
  fun loadFromStream(stream: InputStream,
                     path: String?,
                     scale: Float,
                     originalUserSize: Dimension2DDouble? = null,
                     @MagicConstant(flags = [USE_DARK.toLong(), USE_SVG.toLong()]) flags: Int): Image {
    stream.use {
      return if (flags and USE_SVG == USE_SVG) {
        val compoundCacheKey = SvgCacheMapper(scale = scale, isDark = flags and USE_DARK == USE_DARK, isStroke = false)
        SVGLoader.load(path = path, stream = stream, scale = scale, compoundCacheKey = compoundCacheKey, colorPatcherProvider = null)
      }
      else {
        loadPng(stream = stream, scale = scale, originalUserSize = originalUserSize)
      }
    }
  }

  fun convertImage(image: Image,
                   filters: List<ImageFilter?>,
                   @MagicConstant(flagsFromClass = ImageLoader::class) flags: Int,
                   scaleContext: ScaleContext,
                   isUpScaleNeeded: Boolean,
                   isHiDpiNeeded: Boolean,
                   imageScale: Float,
                   isSvg: Boolean): Image {
    var result = image
    if (isUpScaleNeeded && !isSvg) {
      var scale = adjustScaleFactor(
        flags and ALLOW_FLOAT_SCALING == ALLOW_FLOAT_SCALING, scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat())
      if (imageScale > 1) {
        // compensate the image original scale
        scale /= imageScale
      }
      result = scaleImage(result, scale.toDouble())
    }
    if (!filters.isEmpty()) {
      val toolkit = Toolkit.getDefaultToolkit()
      for (filter in filters) {
        if (filter != null) {
          result = toolkit.createImage(FilteredImageSource(ImageUtil.toBufferedImage(result, false).source, filter))
        }
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

  fun getImageDescriptors(path: String,
                          @MagicConstant(flagsFromClass = ImageLoader::class) flags: Int,
                          scaleContext: ScaleContext): List<ImageDescriptor> {
    return createImageDescriptorList(path = path, flags = flags, scaleContext = scaleContext)
  }


  @JvmStatic
  fun loadFromUrl(url: URL): Image? {
    var flags = USE_SVG or USE_CACHE or ALLOW_FLOAT_SCALING
    val isDark = StartupUiUtil.isUnderDarcula()
    if (isDark) {
      flags = flags or USE_DARK
    }
    val path = url.toString()
    return loadImage(path = path,
                     parameters = defaultParameters(isDark),
                     classLoader = null,
                     flags = flags,
                     isUpScaleNeeded = !path.endsWith(".svg"))
  }

  /**
   * Loads an image of available resolution (1x, 2x, ...) and scales to address the provided scale context.
   * Then wraps the image with [JBHiDPIScaledImage] if necessary.
   */
  fun loadFromUrl(path: String,
                  aClass: Class<*>?,
                  @MagicConstant(flagsFromClass = ImageLoader::class) flags: Int,
                  scaleContext: ScaleContext): Image? {
    // We can't check all 3rd party plugins and convince the authors to add @2x icons.
    // In IDE-managed HiDPI mode with a scale > 1.0 we scale images manually - pass isUpScaleNeeded = true
    val parameters = LoadIconParameters(emptyList(), scaleContext, flags and USE_DARK == USE_DARK, null, false)
    return loadImage(path = path,
                     parameters = parameters,
                     resourceClass = aClass,
                     classLoader = null,
                     flags = flags,
                     isUpScaleNeeded = !path.endsWith(".svg"))
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
    return if (w <= 0 || h <= 0 || w == targetWidth && h == targetHeight) {
      image
    }
    else {
      Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, targetWidth, targetHeight, null)
    }
  }

  @JvmStatic
  @Deprecated("Use {@link #loadFromResource(String, Class)}")
  fun loadFromResource(s: @NonNls String): Image? {
    val callerClass = ReflectionUtil.getGrandCallerClass()
    return if (callerClass == null) null else loadFromResource(s, callerClass)
  }

  @JvmStatic
  fun loadFromResource(path: @NonNls String, aClass: Class<*>): Image? {
    var flags = USE_SVG or ALLOW_FLOAT_SCALING or USE_CACHE
    val isDark = StartupUiUtil.isUnderDarcula()
    if (isDark) {
      flags = flags or USE_DARK
    }
    return loadImage(path = path,
                     parameters = defaultParameters(isDark),
                     resourceClass = aClass,
                     classLoader = null,
                     flags = flags,
                     isUpScaleNeeded = false)
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
    val iconPath = url.toString()
    val scaleContext = ScaleContext.create()
    // probably, need it implements naming conventions: filename ends with @2x => HiDPI (scale=2)
    val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
    val imageDescriptor = ImageDescriptor(iconPath, scale, StringUtilRt.endsWithIgnoreCase(iconPath, ".svg"), iconPath.contains("_dark."),
                                          iconPath.contains("_stroke."))
    val icon = ImageUtil.ensureHiDPI(
      loadByDescriptor(descriptor = imageDescriptor,
                       flags = USE_CACHE,
                       resourceClass = null,
                       classLoader = null,
                       ioMissCache = null,
                       imageCache = ImageCache.INSTANCE,
                       ioMissCacheKey = null,
                       colorPatcherProvider = null),
      scaleContext) ?: return null
    val w = icon.getWidth(null)
    val h = icon.getHeight(null)
    if (w <= 0 || h <= 0) {
      LOG.error("negative image size: w=$w, h=$h, path=$iconPath")
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

private fun adjustScaleFactor(allowFloatScaling: Boolean, scale: Float): Float {
  return if (allowFloatScaling) scale else if (isHiDPI(scale.toDouble())) 2f else 1f
}

private fun createImageDescriptorList(path: String,
                                      @MagicConstant(flagsFromClass = ImageLoader::class) flags: Int,
                                      scaleContext: ScaleContext): List<ImageDescriptor> {
  // prefer retina images for HiDPI scale, because downscaling retina images provide a better result than up-scaling non-retina images
  val pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
  val i = path.lastIndexOf('.')
  val name = if (i < 0) path else path.substring(0, i)
  val ext = if (i < 0 || i == path.length - 1) "" else path.substring(i + 1)
  val scale = adjustScaleFactor(flags and ImageLoader.ALLOW_FLOAT_SCALING == ImageLoader.ALLOW_FLOAT_SCALING, pixScale)
  val list: List<ImageDescriptor>
  if (!path.startsWith("file:") && path.contains("://")) {
    val qI = path.lastIndexOf('?')
    val isSvg = StringUtilRt.endsWithIgnoreCase(if (qI == -1) path else path.substring(0, qI), ".svg")
    list = listOf(ImageDescriptor("$name.$ext", 1f, isSvg, true, false))
  }
  else {
    val isSvg = "svg".equals(ext, ignoreCase = true)
    val isDark = flags and ImageLoader.USE_DARK == ImageLoader.USE_DARK
    val retina = isHiDPI(pixScale.toDouble())
    list = ArrayList()
    if (!isSvg && flags and ImageLoader.USE_SVG == ImageLoader.USE_SVG) {
      addFileNameVariant(retina, isDark, true, false, name, ext, scale, list)
    }
    addFileNameVariant(retina, isDark, false, isSvg, name, ext, scale, list)
    if (isDark) {
      // fallback to non-dark
      addFileNameVariant(retina, false, false, isSvg, name, ext, scale, list)
      if (!isSvg && flags and ImageLoader.USE_SVG == ImageLoader.USE_SVG) {
        addFileNameVariant(false, false, false, true, name, ext, scale, list)
      }
    }
  }
  return list
}

@Internal
class ImageCache private constructor() {
  companion object {
    val INSTANCE = ImageCache()
  }

  @JvmField
  internal val ioMissCache: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
  @JvmField
  internal val imageCache: ConcurrentMap<CacheKey, Image> = CollectionFactory.createConcurrentSoftValueMap<CacheKey, Image>()

  // https://github.com/JetBrains/intellij-community/pull/1242
  @JvmField
  internal val largeImageCache = CollectionFactory.createConcurrentWeakValueMap<CacheKey, Image>()

  fun clearCache() {
    imageCache.clear()
    largeImageCache.clear()
    ioMissCache.clear()
  }
}

internal val CACHED_IMAGE_MAX_SIZE = (SystemProperties.getFloatProperty("ide.cached.image.max.size", 1.5f) * 1024 * 1024).toLong()

// @2x is used even for SVG icons by intention
private fun addFileNameVariant(retina: Boolean,
                               isDark: Boolean,
                               isStroke: Boolean,
                               isSvg: Boolean,
                               name: String,
                               ext: String,
                               scale: Float,
                               list: MutableList<ImageDescriptor>) {
  val effectiveExt = if (isSvg) "svg" else ext
  val retinaScale = if (isSvg) scale else 2f
  val nonRetinaScale = if (isSvg) scale else 1f
  if (isStroke) {
    list.add(ImageDescriptor(name + "_stroke." + effectiveExt, if (retina) retinaScale else nonRetinaScale, isSvg, false, true))
  }
  val descriptors = if (isDark) {
    mutableListOf(ImageDescriptor("${name}@2x_dark.$effectiveExt", retinaScale, isSvg, true, false),
                  ImageDescriptor("${name}_dark@2x.$effectiveExt", retinaScale, isSvg, true, false),
                  ImageDescriptor("${name}_dark.$effectiveExt", nonRetinaScale, isSvg, true, false))
  }
  else {
    mutableListOf(ImageDescriptor("${name}@2x.$effectiveExt", retinaScale, isSvg, false, false),
                  ImageDescriptor("${name}.$effectiveExt", nonRetinaScale, isSvg, false, false))
  }
  if (!retina) {
    descriptors.reverse()
  }
  list.addAll(descriptors)
}

private fun loadByDescriptor(descriptor: ImageDescriptor,
                             @MagicConstant(flags = [USE_CACHE.toLong()]) flags: Int,
                             resourceClass: Class<*>?,
                             classLoader: ClassLoader?,
                             ioMissCache: Set<String?>?,
                             imageCache: ImageCache,
                             ioMissCacheKey: String?,
                             colorPatcherProvider: SvgElementColorPatcherProvider?): Image? {
  var cacheKey: CacheKey? = null
  var tmpPatcher = false
  var digest: ByteArray? = null
  if (colorPatcherProvider != null) {
    val colorPatcher = colorPatcherProvider.forPath(descriptor.path)
    if (colorPatcher != null) {
      digest = colorPatcher.digest()
      if (digest == null) {
        tmpPatcher = true
      }
    }
  }
  if (digest == null) {
    digest = DEFAULT_THEME_DIGEST
  }
  if ((flags and USE_CACHE) == USE_CACHE && !tmpPatcher) {
    cacheKey = CacheKey(path = descriptor.path, scale = (if (descriptor.isSvg) descriptor.scale else 0f), digest = digest)
    (imageCache.imageCache.get(cacheKey) ?: imageCache.largeImageCache.get(cacheKey))?.let {
      return it
    }
  }
  if (ioMissCache != null && ioMissCache.contains(ioMissCacheKey)) {
    return null
  }

  val image = loadByDescriptorWithoutCache(descriptor = descriptor,
                                           resourceClass = resourceClass,
                                           classLoader = classLoader,
                                           colorPatcher = colorPatcherProvider) ?: return null
  if (cacheKey != null) {
    if (4L * image.getWidth(null) * image.getHeight(null) <= CACHED_IMAGE_MAX_SIZE) {
      imageCache.imageCache.put(cacheKey, image)
    }
    else {
      imageCache.largeImageCache.put(cacheKey, image)
    }
  }
  return image
}

private fun loadByDescriptorWithoutCache(descriptor: ImageDescriptor,
                                         resourceClass: Class<*>?,
                                         classLoader: ClassLoader?,
                                         colorPatcher: SvgElementColorPatcherProvider?): Image? {
  var image: Image?
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  if (resourceClass == null && (classLoader == null || URLUtil.containsScheme(descriptor.path)) && !descriptor.path.startsWith("file://")) {
    val connection = URL(descriptor.path).openConnection()
    (connection as? HttpURLConnection)?.addRequestProperty("User-Agent", "IntelliJ")
    connection.getInputStream().use { stream ->
      image = if (descriptor.isSvg) {
        SVGLoader.load(path = descriptor.path,
                       stream = stream,
                       scale = descriptor.scale,
                       compoundCacheKey = descriptor.toSvgMapper(),
                       colorPatcherProvider = colorPatcher)
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
      SVGLoader.loadFromClassResource(resourceClass = resourceClass,
                                      classLoader = classLoader,
                                      path = descriptor.path,
                                      precomputedCacheKey = 0,
                                      scale = descriptor.scale,
                                      compoundCacheKey = descriptor.toSvgMapper(),
                                      colorPatcherProvider = colorPatcher)
    }
    else {
      ImageLoader.loadPngFromClassResource(path = descriptor.path,
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

internal class CacheKey(private val path: String, private val scale: Float, private val digest: ByteArray) {
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