// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "ReplacePutWithAssignment")

package com.intellij.ui.icons

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.LazyIcon
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.scale.*
import com.intellij.ui.svg.renderSvg
import com.intellij.util.ImageLoader
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.ResourceUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.imgscalr.Scalr
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Image
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.image.FilteredImageSource
import java.awt.image.ImageFilter
import java.awt.image.RGBImageFilter
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.swing.Icon
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private val LOG: Logger
  get() = logger<ImageUtil>()

private val cleaners = CopyOnWriteArrayList<() -> Unit>()

internal const val FILE_SCHEME_PREFIX = "file:/"

internal fun registerIconCacheCleaner(cleaner: () -> Unit) {
  cleaners.add(cleaner)
}

internal fun clearCacheOnUpdateTransform() {
  for (cleaner in cleaners) {
    cleaner()
  }
  // iconCache is not cleared because it contains an original icon (instance that will delegate to)
}

internal fun updateTransform(updater: (IconTransform) -> IconTransform) {
  var prev: IconTransform
  var next: IconTransform
  do {
    prev = pathTransform.get()
    next = updater(prev)
  }
  while (!pathTransform.compareAndSet(prev, next))

  pathTransformGlobalModCount.incrementAndGet()
  if (prev == next) {
    return
  }

  clearCacheOnUpdateTransform()
}

@Internal
fun copyIcon(icon: Icon, ancestor: Component?, deepCopy: Boolean): Icon {
  if (icon is CopyableIcon) {
    return if (deepCopy) icon.deepCopy() else icon.copy()
  }

  val image = ImageUtil.createImage(ancestor?.graphicsConfiguration, icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    icon.paintIcon(ancestor, g, 0, 0)
  }
  finally {
    g.dispose()
  }

  return object : JBImageIcon(image) {
    val originalWidth = icon.iconWidth
    val originalHeight = icon.iconHeight
    override fun getIconWidth(): Int = originalWidth

    override fun getIconHeight(): Int = originalHeight
  }
}

internal fun getOriginIcon(icon: RetrievableIcon): Icon {
  val maxDeep = 10
  var origin = icon.retrieveIcon()
  var level = 0
  while (origin is RetrievableIcon && level < maxDeep) {
    ++level
    origin = origin.retrieveIcon()
  }
  if (origin is RetrievableIcon) {
    LOG.error("can't calculate origin icon (too deep in hierarchy), src: $icon")
  }
  return origin
}

/**
 * For internal usage. Converts the icon to 1x scale when applicable.
 */
@Internal
fun getMenuBarIcon(icon: Icon, dark: Boolean): Icon {
  var effectiveIcon = icon
  if (effectiveIcon is RetrievableIcon) {
    effectiveIcon = getOriginIcon(effectiveIcon)
  }

  if (effectiveIcon is CachedImageIcon) {
    return effectiveIcon.getMenuBarIcon(dark)
  }
  else {
    return if (effectiveIcon is MenuBarIconProvider) effectiveIcon.getMenuBarIcon(dark) else effectiveIcon
  }
}

@Internal
fun convertImage(image: Image,
                 filters: List<ImageFilter>,
                 scaleContext: ScaleContext,
                 isUpScaleNeeded: Boolean,
                 imageScale: Float): Image {
  var result = image
  if (isUpScaleNeeded) {
    var scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
    if (imageScale > 1) {
      // compensate the image original scale
      scale /= imageScale
    }
    result = ImageLoader.scaleImage(result, scale.toDouble())
  }
  result = filterImage(image = result, filters = filters)
  val sysScale = scaleContext.getScale(ScaleType.SYS_SCALE)
  if (isHiDPIEnabledAndApplicable(sysScale.toFloat())) {
    // The {originalUserSize} can contain calculation inaccuracy. If we use it to derive the HiDPI image scale
    // in JBHiDPIScaledImage, the derived scale will also be inaccurate and this will cause distortions
    // when the image is painted on a scaled (hidpi) screen graphics, see
    // StartupUiUtil.drawImage(Graphics, Image, Rectangle, Rectangle, BufferedImageOp, ImageObserver).
    //
    // To avoid that, we instead directly use the provided ScaleContext, which contains correct ScaleContext.SYS_SCALE,
    // JBHiDPIScaledImage will then derive the image user space size (it is assumed the derived size is equal to
    // {originalUserSize} * DerivedScaleType.EFF_USR_SCALE, taking into account calculation accuracy).
    return JBHiDPIScaledImage(image = result, sysScale = sysScale)
  }
  else {
    return result
  }
}

@Internal
fun filterImage(image: Image, filters: List<ImageFilter>): Image {
  var result = image
  if (!filters.isEmpty()) {
    val toolkit = Toolkit.getDefaultToolkit()
    for (filter in filters) {
      result = toolkit.createImage(FilteredImageSource(ImageUtil.toBufferedImage(result, false).source, filter))
    }
  }
  return result
}

@Internal
fun loadPngFromClassResource(path: String, classLoader: ClassLoader?, resourceClass: Class<*>? = null): BufferedImage? {
  val data = getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader) ?: return null
  return loadRasterImage(stream = ByteArrayInputStream(data))
}

@Internal
internal fun getResourceData(path: String, resourceClass: Class<*>?, classLoader: ClassLoader?): ByteArray? {
  assert(resourceClass != null || classLoader != null || path.startsWith(FILE_SCHEME_PREFIX))
  if (classLoader != null) {
    val isAbsolute = path.startsWith('/')
    val data = ResourceUtil.getResourceAsBytes(if (isAbsolute) path.substring(1) else path, classLoader, true)
    if (data != null || isAbsolute) {
      return data
    }
  }

  resourceClass?.getResourceAsStream(path)?.use { stream -> return stream.readAllBytes() }
  if (path.startsWith(FILE_SCHEME_PREFIX)) {
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
fun loadRasterImage(stream: InputStream): BufferedImage {
  val start = StartUpMeasurer.getCurrentTimeIfEnabled()
  var image: BufferedImage
  MemoryCacheImageInputStream(stream).use { imageInputStream ->
    val reader = ImageIO.getImageReaders(imageInputStream).takeIf { it.hasNext() }?.next()
                 ?: ImageIO.getImageReadersByFormatName("png").next()
    try {
      reader.setInput(imageInputStream, true, true)
      image = reader.read(0, null)
    }
    finally {
      reader.dispose()
    }
  }
  if (start != -1L) {
    IconLoadMeasurer.pngDecoding.end(start)
  }
  return image
}

internal fun loadCustomIcon(url: URL): Image? {
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
  val icon = ImageUtil.ensureHiDPI(loadByDescriptor(rawPathWithoutExt = rawPathWithoutExt, ext = ext, descriptor = imageDescriptor),
                                   scaleContext) ?: return null
  val w = icon.getWidth(null)
  val h = icon.getHeight(null)
  if (w <= 0 || h <= 0) {
    LOG.error("negative image size: w=$w, h=$h, path=$path")
    return null
  }

  if (w > EmptyIcon.ICON_18.iconWidth || h > EmptyIcon.ICON_18.iconHeight) {
    val s = EmptyIcon.ICON_18.iconWidth / w.coerceAtLeast(h).toDouble()
    return ImageLoader.scaleImage(icon, s)
  }
  return icon
}

@Internal
fun loadImageForStartUp(requestedPath: String, scale: Float, classLoader: ClassLoader): BufferedImage? {
  val descriptors = createImageDescriptorList(path = requestedPath, isDark = false, isStroke = false, pixScale = scale)
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
        val image = loadRasterImage(stream = ByteArrayInputStream(data))
        // compensate the image original scale
        val effectiveScale = if (descriptor.scale > 1) scale / descriptor.scale else scale
        return doScaleImage(image = image, scale = effectiveScale.toDouble()) as BufferedImage
      }
    }
    catch (ignore: IOException) {
    }
  }
  return null
}

internal fun doScaleImage(image: Image, scale: Double): Image {
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

@Internal
fun toRetinaAwareIcon(image: BufferedImage, sysScale: Float = JBUIScale.sysScale()): Icon {
  return JBImageIcon(if (isHiDPIEnabledAndApplicable(sysScale)) JBHiDPIScaledImage(image, sysScale.toDouble()) else image)
}

internal fun checkIconSize(icon: Icon): Boolean {
  if (icon.iconWidth <= 0 || icon.iconHeight <= 0) {
    LOG.error("Icon $icon has incorrect size: ${icon.iconWidth}x${icon.iconHeight}")
    return false
  }
  return true
}

private val standardDisablingFilter = object : RgbImageFilterSupplier {
  override fun getFilter() = UIUtil.getGrayFilter()
}

// contains mapping between icons and disabled icons
private val iconToDisabledIcon: LoadingCache<Icon, Icon> = Caffeine.newBuilder()
  .maximumSize(1024)
  .executor(Dispatchers.Default.asExecutor())
  .expireAfterAccess(10.minutes.toJavaDuration())
  .build<Icon, Icon>(CacheLoader { icon ->
    if (icon is CachedImageIcon) {
      icon.createWithFilter(standardDisablingFilter)
    }
    else {
      FilteredIcon(baseIcon = icon, filterSupplier = standardDisablingFilter)
    }
  })
  .also {
    registerIconCacheCleaner(it::invalidateAll)
  }

// contains mapping between icons and disabled icons
private val iconToIconWithCustomFilter = CollectionFactory.createSoftMap<RgbImageFilterSupplier, Cache<Icon, Icon>>()
  .also {
    registerIconCacheCleaner(it::clear)
  }

// used as a map key - lambda cannot be used
@Internal
interface RgbImageFilterSupplier {
  fun getFilter(): RGBImageFilter
}

@Internal
fun getDisabledIcon(icon: Icon, disableFilter: RgbImageFilterSupplier?): Icon {
  if (!isIconActivated || icon is EmptyIcon) {
    return icon
  }

  val effectiveIcon = if (icon is LazyIcon) icon.getOrComputeIcon() else icon

  if (disableFilter == null) {
    return iconToDisabledIcon.get(effectiveIcon)
  }

  val filter = disableFilter /* returns laf-aware instance */

  return iconToIconWithCustomFilter.computeIfAbsent(filter) {
    Caffeine.newBuilder()
      .maximumSize(512)
      .executor(Dispatchers.Default.asExecutor())
      .expireAfterAccess(10.minutes.toJavaDuration())
      .build()
  }
    .get(effectiveIcon) { baseIcon ->
      if (baseIcon is CachedImageIcon) {
        baseIcon.createWithFilter(filter)
      }
      else {
        FilteredIcon(baseIcon = baseIcon, filterSupplier = filter)
      }
    }
}