// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ImageLoader
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.ResourceUtil
import com.intellij.util.lang.ByteBufferCleaner
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus.Internal
import sun.awt.image.SunWritableRaster
import java.awt.Component
import java.awt.Image
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.*
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.swing.Icon

private val LOG: Logger
  get() = logger<ImageUtil>()

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
  return if (effectiveIcon is MenuBarIconProvider) effectiveIcon.getMenuBarIcon(dark) else effectiveIcon
}

@Internal
fun convertImage(image: Image,
                 filters: List<ImageFilter>,
                 scaleContext: ScaleContext,
                 isUpScaleNeeded: Boolean,
                 isHiDpiNeeded: Boolean = StartupUiUtil.isJreHiDPI(scaleContext),
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

@Internal
fun loadPngFromClassResource(path: String, classLoader: ClassLoader?, scale: Float, resourceClass: Class<*>? = null): BufferedImage? {
  val data = getResourceData(path = path, resourceClass = resourceClass, classLoader = classLoader) ?: return null
  return loadPng(stream = ByteArrayInputStream(data), scale = scale, originalUserSize = null)
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

@Internal
fun readImage(file: Path, scaleContextProvider: () -> ScaleContext): BufferedImage? {
  val buffer = try {
    FileChannel.open(file).use { channel ->
      channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.LITTLE_ENDIAN)
    }
  }
  catch (ignore: NoSuchFileException) {
    return null
  }

  try {
    val intBuffer = buffer.asIntBuffer()
    val w = intBuffer.get()
    val h = intBuffer.get()

    val scaleContext = scaleContextProvider()

    val currentSysScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
    val imageScale = java.lang.Float.intBitsToFloat(intBuffer.get())
    if (currentSysScale != imageScale) {
      LOG.warn("Image cache is not used as scale differs (current=$currentSysScale, image=$imageScale, file=$file)")
      return null
    }

    val dataBuffer = DataBufferInt(w * h)
    intBuffer.get(SunWritableRaster.stealData(dataBuffer, 0))
    SunWritableRaster.makeTrackable(dataBuffer)
    val colorModel = ColorModel.getRGBdefault() as DirectColorModel
    val raster = Raster.createPackedRaster(dataBuffer, w, h, w, colorModel.masks, Point(0, 0))

    @Suppress("UndesirableClassUsage")
    val rawImage = BufferedImage(colorModel, raster, false, null)
    return ImageUtil.ensureHiDPI(rawImage, scaleContext) as BufferedImage
  }
  finally {
    ByteBufferCleaner.unmapBuffer(buffer)
  }
}

@Internal
fun writeImage(file: Path, image: BufferedImage, scale: Float) {
  val parent = file.parent
  Files.createDirectories(parent)
  val tempFile = Files.createTempFile(parent, file.fileName.toString(), ".ij")
  FileChannel.open(tempFile, EnumSet.of(StandardOpenOption.WRITE)).use { channel ->
    val imageData = (image.raster.dataBuffer as DataBufferInt).data

    val buffer = ByteBuffer.allocateDirect(imageData.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    try {
      buffer.putInt(image.width)
      buffer.putInt(image.height)
      buffer.putInt(scale.toBits())
      buffer.flip()
      do {
        channel.write(buffer)
      }
      while (buffer.hasRemaining())

      buffer.clear()

      buffer.asIntBuffer().put(imageData)
      buffer.position(0)
      do {
        channel.write(buffer)
      }
      while (buffer.hasRemaining())
    }
    finally {
      ByteBufferCleaner.unmapBuffer(buffer)
    }
  }

  try {
    Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE)
  }
  catch (e: AtomicMoveNotSupportedException) {
    Files.move(tempFile, file)
  }
}