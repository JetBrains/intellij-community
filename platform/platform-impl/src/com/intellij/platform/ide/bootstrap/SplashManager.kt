// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UndesirableClassUsage", "JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.ide.bootstrap

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.impl.ProjectUtil.getRootFrameForWindow
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
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.icons.HiDPIImage
import com.intellij.ui.icons.loadImageForStartUp
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.lang.ByteBufferCleaner
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import sun.awt.image.SunWritableRaster
import java.awt.*
import java.awt.event.*
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
import kotlin.coroutines.coroutineContext

@Volatile
private var PROJECT_FRAME: JFrame? = null

@Volatile
private var SPLASH_WINDOW: Splash? = null

// if hideSplash requested before we show splash, we should not try to show splash
private val splashJob = AtomicReference<Job>(CompletableDeferred<Unit>())

private val SHOW_SPLASH_LONGER = System.getProperty("idea.show.splash.longer", "false").toBoolean()

private fun isTooLateToShowSplash(): Boolean = !SHOW_SPLASH_LONGER && LoadingState.COMPONENTS_LOADED.isOccurred

@Internal
fun CoroutineScope.scheduleShowSplashIfNeeded(lockSystemDirsJob: Job, initUiScale: Job, appInfoDeferred: Deferred<ApplicationInfo>, args: List<String>) {
  launch(CoroutineName("showSplashIfNeeded")) {
    if (!AppMode.isLightEdit() && !isRealRemoteDevHost(args) && CommandLineArgs.isSplashNeeded(args)) {
      lockSystemDirsJob.join()
      try {
        showSplashIfNeeded(initUiScale = initUiScale, appInfoDeferred = appInfoDeferred)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger<Splash>().warn("Cannot show splash", e)
      }
    }
  }
}

private fun isRealRemoteDevHost(args: List<String>): Boolean = AppMode.isRemoteDevHost() && args.firstOrNull() != AppMode.SPLIT_MODE_COMMAND

private fun CoroutineScope.showSplashIfNeeded(initUiScale: Job, appInfoDeferred: Deferred<ApplicationInfo>) {
  val oldJob = splashJob.get()
  if (oldJob.isCancelled) {
    return
  }

  val newJob = launch(start = CoroutineStart.LAZY) {
    if (isTooLateToShowSplash()) {
      return@launch
    }

    initUiScale.join()

    //if (showLastProjectFrameIfAvailable(initUiDeferred)) {
    //  return@launch
    //}

    /*
    Wayland doesn't have the concept of splash screens at all, so they may not appear centered.
    Avoid showing the splash screen at all in this case up until this is solved (as, for example,
    in java.awt.SplashScreen that works around the issue using some tricks and the native API).
    We check only here as isWaylandToolkit calls `Toolkit.getDefaultToolkit()` - it should be done only when initUiDeferred is completed
    */
    if (SystemInfoRt.isLinux && StartupUiUtil.isWaylandToolkit()) {
      return@launch
    }

    val appInfo = appInfoDeferred.await()

    if (isTooLateToShowSplash()) {
      return@launch
    }

    val image = span("splash preparation") {
      assert(SPLASH_WINDOW == null)
      loadSplashImage(appInfo = appInfo)
    } ?: return@launch

    if (isTooLateToShowSplash()) {
      return@launch
    }

    span("splash initialization", RawSwingDispatcher) {
      if (isTooLateToShowSplash()) {
        return@span
      }

      val splash = try {
        Splash(image, isAlwaysOnTop = SHOW_SPLASH_LONGER)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger<Splash>().warn(e)
        return@span
      }

      val deactivationListener = if (SHOW_SPLASH_LONGER) {
        // Hide if splash or IDE frame was deactivated because of focusing some other window in the OS (not IDE Frame).
        val listener = AWTEventListener { e ->
          if (e.id == WindowEvent.WINDOW_DEACTIVATED) {
            val windowEvent = e as WindowEvent
            if (getRootFrameForWindow(windowEvent.oppositeWindow) == null) {
              hideSplash()
            }
          }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
        listener
      }
      else null

      StartUpMeasurer.addInstantEvent("splash shown")
      try {
        ensureActive()
        SPLASH_WINDOW = splash
        splash.addComponentListener(object : ComponentAdapter() {
          override fun componentShown(e: ComponentEvent?) {
            FUSProjectHotStartUpMeasurer.splashBecameVisible()
            splash.removeComponentListener(this)
          }
        })
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
      catch (_: CancellationException) {
        SPLASH_WINDOW = null
        Toolkit.getDefaultToolkit().removeAWTEventListener(deactivationListener)
        splash.isVisible = false
        splash.dispose()
        StartUpMeasurer.addInstantEvent("splash hidden")
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
  // this functionality under the flag - fully correct behavior is unnecessary here (that's default is not applied if null)
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

@RequiresEdt
internal fun blockingLoadSplashImage(appInfo: ApplicationInfo): BufferedImage? {
  return runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
    loadSplashImage(appInfo)
  }
}

@OptIn(DelicateCoroutinesApi::class)
internal suspend fun loadSplashImage(appInfo: ApplicationInfo): BufferedImage? {
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

  coroutineContext.ensureActive()

  if (file != null) {
    loadImageFromCache(file = file, scale = scale, isJreHiDPIEnabled = isJreHiDPIEnabled)?.let {
      return it
    }
  }

  coroutineContext.ensureActive()

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

private suspend fun loadImageFromCache(file: Path, scale: Float, isJreHiDPIEnabled: Boolean): BufferedImage? {
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
  val appInfoData = ApplicationNamesInfo.getAppInfoData()
  if (appInfoData.isEmpty()) {
    val hasher = Hashing.komihash5_0().hashStream()
    try {
      hasher.putInt(Splash::class.java.classLoader.getResourceAsStream(path)?.use { it.available() } ?: 0)
    }
    catch (e: Throwable) {
      logger<Splash>().warn("Failed to read splash image", e)
    }

    hasher.putChars(path)

    val fileName = java.lang.Long.toUnsignedString(hasher.asLong, Character.MAX_RADIX) +
                   Integer.toUnsignedString(scale.toBits(), Character.MAX_RADIX) +
                   ".v2.ij"
    return Path.of(PathManager.getSystemPath(), "splash", fileName)
  }
  else {
    val fileName = java.lang.Long.toUnsignedString(appInfo.buildTime.toEpochSecond(), Character.MAX_RADIX) +
                   "-" +
                   Integer.toUnsignedString(path.hashCode(), Character.MAX_RADIX) +
                   "-" +
                   Integer.toUnsignedString(scale.toBits(), Character.MAX_RADIX) +
                   ".ij"
    return Path.of(PathManager.getSystemPath(), "splash", fileName)
  }
}

private suspend fun readImage(file: Path, scale: Float, isJreHiDPIEnabled: Boolean): BufferedImage? {
  val buffer = try {
    withContext(Dispatchers.IO) {
      FileChannel.open(file).use { channel ->
        channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.LITTLE_ENDIAN)
      }
    }
  }
  catch (_: NoSuchFileException) {
    return null
  }

  coroutineContext.ensureActive()

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
  catch (_: AtomicMoveNotSupportedException) {
    Files.move(tempFile, file)
  }
}
