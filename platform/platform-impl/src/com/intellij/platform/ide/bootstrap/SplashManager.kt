// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UndesirableClassUsage", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.ide.bootstrap

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.FrameBoundsConverter
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.Splash
import com.intellij.ui.icons.HiDPIImage
import com.intellij.ui.icons.loadImageForStartUp
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.lang.ByteBufferCleaner
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.*
import sun.awt.image.SunWritableRaster
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.RoundRectangle2D
import java.awt.image.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.WindowConstants

@Volatile
private var PROJECT_FRAME: JFrame? = null

@Volatile
private var SPLASH_WINDOW: Splash? = null

// if hideSplash requested before we show splash, we should not try to show splash
private val splashJob = AtomicReference<Job>(CompletableDeferred<Unit>())

private val SHOW_SPLASH_LONGER = System.getProperty("idea.show.splash.longer", "true").toBoolean()

private fun isTooLateToShowSplash(): Boolean = !SHOW_SPLASH_LONGER && LoadingState.COMPONENTS_LOADED.isOccurred

internal fun CoroutineScope.scheduleShowSplashIfNeeded(initUiDeferred: Job, appInfoDeferred: Deferred<ApplicationInfo>, args: List<String>) {
  launch(CoroutineName("showSplashIfNeeded")) {
    if (!AppMode.isLightEdit() && CommandLineArgs.isSplashNeeded(args)) {
      try {
        showSplashIfNeeded(initUiDeferred = initUiDeferred, appInfoDeferred = appInfoDeferred)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger<AppStarter>().warn("Cannot show splash", e)
      }
    }
  }
}

private fun CoroutineScope.showSplashIfNeeded(initUiDeferred: Job, appInfoDeferred: Deferred<ApplicationInfo>) {
  val oldJob = splashJob.get()
  if (oldJob.isCancelled) {
    return
  }

  val newJob = launch(start = CoroutineStart.LAZY) {
    //if (showLastProjectFrameIfAvailable(initUiDeferred)) {
    //  return@launch
    //}

    // A splash instance must not be created before base LaF is created.
    // It is important on Linux, where GTK LaF must be initialized (to properly set up the scale factor).
    // https://youtrack.jetbrains.com/issue/IDEA-286544
    initUiDeferred.join()

    val appInfo = appInfoDeferred.await()
    val image = span("splash preparation") {
      assert(SPLASH_WINDOW == null)
      loadSplashImage(appInfo = appInfo)
    } ?: return@launch

    if (!isActive || isTooLateToShowSplash()) {
      return@launch
    }

    span("splash initialization", RawSwingDispatcher) {
      if (isTooLateToShowSplash()) {
        return@span
      }

      val splash = try {
        Splash(image)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger<Splash>().warn(e)
        return@span
      }

      StartUpMeasurer.addInstantEvent("splash shown")
      try {
        ensureActive()
        SPLASH_WINDOW = splash
        span("splash set visible") {
          splash.isVisible = true
        }

        val showJob = CompletableDeferred<Unit>()
        splash.addWindowListener(object : WindowAdapter() {
          override fun windowClosed(e: WindowEvent) {
            showJob.complete(Unit)
          }
        })
        withContext(Dispatchers.Default) {
          showJob.join()
        }
      }
      catch (ignore: CancellationException) {
        SPLASH_WINDOW = null
        withContext(NonCancellable) {
          splash.isVisible = false
          splash.dispose()

          StartUpMeasurer.addInstantEvent("splash hidden")
        }
      }

      SPLASH_WINDOW = null
    }
  }

  // not a real case - showSplashIfNeeded is called only once
  if (!splashJob.compareAndSet(oldJob, newJob)) {
    return
  }

  if (oldJob.isCancelled) {
    newJob.cancel()
  }
  else {
    newJob.start()
  }
}

@Suppress("unused")
private suspend fun showLastProjectFrameIfAvailable(initUiDeferred: Job): Boolean {
  lateinit var backgroundColor: Color
  var extendedState = 0
  val savedBounds: Rectangle = span("splash as project frame initialization") {
    val infoFile = Path.of(PathManager.getSystemPath(), "lastProjectFrameInfo")
    val buffer = try {
      withContext(Dispatchers.IO) {
        Files.newByteChannel(infoFile).use { channel ->
          val buffer = ByteBuffer.allocate(channel.size().toInt())
          do {
            channel.read(buffer)
          }
          while (buffer.hasRemaining())
          buffer.flip()
          if (buffer.getShort().toInt() != 0) {
            return@withContext null
          }

          buffer
        }
      } ?: return@span null
    }
    catch (ignore: NoSuchFileException) {
      return@span null
    }

    val savedBounds = Rectangle(buffer.getInt(), buffer.getInt(), buffer.getInt(), buffer.getInt())

    @Suppress("UseJBColor")
    backgroundColor = Color(buffer.getInt(), true)

    @Suppress("UNUSED_VARIABLE")
    val isFullScreen = buffer.get().toInt() == 1
    extendedState = buffer.getInt()
    savedBounds
  } ?: return false

  initUiDeferred.join()
  span("splash as project frame creation") {
    withContext(RawSwingDispatcher) {
      PROJECT_FRAME = doShowFrame(savedBounds = savedBounds, backgroundColor = backgroundColor, extendedState = extendedState)
    }
  }
  return true
}

internal fun getAndUnsetSplashProjectFrame(): JFrame? {
  val frame = PROJECT_FRAME
  PROJECT_FRAME = null
  return frame
}

fun hideSplashBeforeShow(window: Window) {
  if (splashJob.get().isCompleted) {
    return
  }

  window.addWindowListener(object : WindowAdapter() {
    override fun windowOpened(e: WindowEvent) {
      window.removeWindowListener(this)
      hideSplash()
    }
  })
}

internal fun hasSplash(): Boolean = SPLASH_WINDOW != null

fun hideSplash() {
  splashJob.get().cancel("hideSplash")
}

private fun doShowFrame(savedBounds: Rectangle, backgroundColor: Color, extendedState: Int): IdeFrameImpl {
  val frame = IdeFrameImpl()
  frame.isAutoRequestFocus = false
  frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
  val devicePair = FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(savedBounds)
  // this functionality under the flag - fully correct behavior is not needed here (that's default is not applied if null)
  if (devicePair != null) {
    frame.bounds = devicePair.first
  }
  frame.extendedState = extendedState
  frame.minimumSize = Dimension(340, frame.minimumSize.getHeight().toInt())
  frame.background = backgroundColor
  frame.contentPane.background = backgroundColor
  if (SystemInfoRt.isMac) {
    frame.iconImage = null
  }
  StartUpMeasurer.addInstantEvent("frame shown")
  val activity = StartUpMeasurer.startActivity("frame set visible")
  frame.isVisible = true
  activity.end()
  return frame
}

@OptIn(DelicateCoroutinesApi::class)
internal fun loadSplashImage(appInfo: ApplicationInfo): BufferedImage? {
  val splashImagePath = appInfo.splashImageUrl?.let { if (it.startsWith('/')) it.substring(1) else it } ?: return null

  val isJreHiDPIEnabled = JreHiDpiUtil.isJreHiDPIEnabled()
  val scale = if (isJreHiDPIEnabled) JBUIScale.sysScale() * JBUIScale.scale(1f) else JBUIScale.scale(1f)
  val file = try {
    getCacheFile(scale = scale, appInfo = appInfo, path = splashImagePath)
  }
  catch (e: Throwable) {
    logger<Splash>().warn(e)
    null
  }

  if (file != null) {
    loadImageFromCache(file = file, scale = scale, isJreHiDPIEnabled = isJreHiDPIEnabled)?.let {
      return it
    }
  }

  val path = appInfo.splashImageUrl
  val result = doLoadImage(path = splashImagePath, scale = scale, isJreHiDPIEnabled = isJreHiDPIEnabled)
               ?: throw IllegalStateException("Cannot find image: $path")
  if (file != null) {
    GlobalScope.launch(Dispatchers.IO) {
      try {
        val rawImage = (if (result is JBHiDPIScaledImage) result.delegate else result) as BufferedImage
        writeImage(file = file, image = rawImage)
      }
      catch (e: Throwable) {
        logger<Splash>().warn("Cannot save splash image", e)
      }
    }
  }
  return result
}

private fun doLoadImage(path: String, scale: Float, isJreHiDPIEnabled: Boolean): BufferedImage? {
  val originalImage = loadImageForStartUp(requestedPath = path, scale = scale, classLoader = Splash::class.java.classLoader) ?: return null
  val w = originalImage.width
  val h = originalImage.height
  val resultImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
  val g2 = resultImage.createGraphics()
  g2.composite = AlphaComposite.Src
  ImageUtil.applyQualityRenderingHints(g2)
  @Suppress("UseJBColor")
  g2.color = Color.WHITE
  val cornerRadius = 8 * scale
  g2.fill(RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius))
  g2.composite = AlphaComposite.SrcIn
  g2.drawImage(originalImage, 0, 0, null)
  g2.dispose()
  return createHiDpiAwareImage(rawImage = resultImage, scale = scale, isJreHiDPIEnabled = isJreHiDPIEnabled)
}

private fun loadImageFromCache(file: Path, scale: Float, isJreHiDPIEnabled: Boolean): BufferedImage? {
  try {
    return readImage(file = file, scale = scale, isJreHiDPIEnabled = isJreHiDPIEnabled)
  }
  catch (e: Throwable) {
    // don't use `error`, because it can crash application
    logger<Splash>().warn("Failed to load splash image", e)
  }
  return null
}

private fun getCacheFile(scale: Float, appInfo: ApplicationInfo, path: String): Path {
  val buildTime = appInfo.buildUnixTimeInMillis
  if (buildTime == 0L) {
    val hasher = Hashing.komihash5_0().hashStream()
    val appInfoData = ApplicationNamesInfo.getAppInfoData()
    if (appInfoData.isEmpty()) {
      try {
        hasher.putInt(Splash::class.java.classLoader.getResourceAsStream(path)?.use { it.available() } ?: 0)
        hasher.putString("")
      }
      catch (e: Throwable) {
        logger<Splash>().warn("Failed to read splash image", e)
      }
    }
    else {
      hasher.putInt(0)
      hasher.putChars(appInfoData)
    }
    hasher.putChars(path)

    val fileName = java.lang.Long.toUnsignedString(hasher.asLong, Character.MAX_RADIX) +
                   Integer.toUnsignedString(scale.toBits(), Character.MAX_RADIX) +
                   ".v2.ij"
    return Path.of(PathManager.getSystemPath(), "splash", fileName)
  }
  else {
    val fileName = java.lang.Long.toUnsignedString(buildTime, Character.MAX_RADIX) +
                   "-" +
                   Integer.toUnsignedString(path.hashCode(), Character.MAX_RADIX) +
                   "-" +
                   Integer.toUnsignedString(scale.toBits(), Character.MAX_RADIX) +
                   ".ij"
    return Path.of(PathManager.getSystemPath(), "splash", fileName)
  }
}

private fun readImage(file: Path, scale: Float, isJreHiDPIEnabled: Boolean): BufferedImage? {
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

    val dataBuffer = DataBufferInt(w * h)
    intBuffer.get(SunWritableRaster.stealData(dataBuffer, 0))
    SunWritableRaster.makeTrackable(dataBuffer)
    val colorModel = ColorModel.getRGBdefault() as DirectColorModel
    val raster = Raster.createPackedRaster(dataBuffer, w, h, w, colorModel.masks, Point(0, 0))

    @Suppress("UndesirableClassUsage")
    val rawImage = BufferedImage(colorModel, raster, false, null)
    return createHiDpiAwareImage(rawImage = rawImage, scale = scale, isJreHiDPIEnabled = isJreHiDPIEnabled)
  }
  finally {
    ByteBufferCleaner.unmapBuffer(buffer)
  }
}

private fun createHiDpiAwareImage(rawImage: BufferedImage, scale: Float, isJreHiDPIEnabled: Boolean): BufferedImage {
  if (isJreHiDPIEnabled) {
    return HiDPIImage(image = rawImage,
                      width = rawImage.width.toDouble() / scale,
                      height = rawImage.height.toDouble() / scale,
                      type = BufferedImage.TYPE_INT_ARGB)
  }
  return rawImage
}

private fun writeImage(file: Path, image: BufferedImage) {
  val parent = file.parent
  Files.createDirectories(parent)
  val tempFile = Files.createTempFile(parent, file.fileName.toString(), ".ij")
  FileChannel.open(tempFile, EnumSet.of(StandardOpenOption.WRITE)).use { channel ->
    val imageData = (image.raster.dataBuffer as DataBufferInt).data

    val buffer = ByteBuffer.allocateDirect(imageData.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    try {
      buffer.putInt(image.width)
      buffer.putInt(image.height)
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
