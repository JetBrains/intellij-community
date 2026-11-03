// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
@file:OptIn(LowLevelLocalMachineAccess::class)
package com.intellij.platform.ide.bootstrap

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.idea.AppMode
import com.intellij.idea.WellKnownCommand
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.progress.ModalTaskOwner.guess
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.icons.HiDPIImage
import com.intellij.ui.icons.loadImageForStartUp
import com.intellij.ui.paint.use
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.RawSwingDispatcher
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import sun.awt.image.SunWritableRaster
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Point
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.name

@Volatile
private var SPLASH_WINDOW: Splash? = null

// if hideSplash requested before we show splash, we should not try to show splash
private val splashJob = AtomicReference<Job>(CompletableDeferred<Unit>())

private fun isTooLateToShowSplash(): Boolean = LoadingState.COMPONENTS_LOADED.isOccurred

private val LE_INT: ValueLayout.OfInt = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)

// don't use `error` on it, because it can crash the application
private fun logger(): Logger = Logger.getInstance("com.intellij.platform.ide.bootstrap.SplashManager")

@ApiStatus.Internal
fun scheduleShowSplashIfNeeded(
  scope: CoroutineScope,
  lockSystemDirsJob: Job,
  initUiScale: Job,
  appInfoDeferred: Deferred<ApplicationInfo>,
  args: List<String>,
) {
  scope.launch(CoroutineName("showSplashIfNeeded")) {
    if (!AppMode.isLightEdit() && !isRealRemoteDevHost(args) && CommandLineArgs.isSplashNeeded(args)) {
      lockSystemDirsJob.join()
      try {
        showSplashIfNeeded(scope, initUiScale, appInfoDeferred)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger().warn("Cannot show splash", e)
      }
    }
  }
}

private fun isRealRemoteDevHost(args: List<String>): Boolean = AppMode.isRemoteDevHost() && args.firstOrNull() != WellKnownCommand.SPLIT_MODE

private fun showSplashIfNeeded(scope: CoroutineScope, initUiScale: Job, appInfoDeferred: Deferred<ApplicationInfo>) {
  val oldJob = splashJob.get()
  if (oldJob.isCancelled) {
    return
  }

  val newJob = scope.launch(start = CoroutineStart.LAZY) {
    if (isTooLateToShowSplash()) {
      return@launch
    }

    initUiScale.join()

    // Wayland doesn't have the concept of splash screens at all, so they may not appear centered.
    // Avoid showing the splash screen at all in this case up until this is solved (as, for example,
    // in java.awt.SplashScreen that works around the issue using some tricks and the native API).
    // We check only here as isWaylandToolkit calls `Toolkit.getDefaultToolkit()` - it should be done only when initUiDeferred is completed
    if (OS.CURRENT == OS.Linux && StartupUiUtil.isWaylandToolkit() && !CommandLineArgs.isSplashForced()) {
      return@launch
    }

    val appInfo = appInfoDeferred.await()

    if (isTooLateToShowSplash()) {
      return@launch
    }

    val image = span("splash preparation") {
      assert(SPLASH_WINDOW == null)
      loadSplashImage(appInfo)
    } ?: return@launch

    if (isTooLateToShowSplash()) {
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
        logger().warn(e)
        return@span
      }

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
      catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
        SPLASH_WINDOW = null
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

@ApiStatus.Internal
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

@ApiStatus.Internal
fun hideSplash() {
  splashJob.get().cancel("hideSplash")
}

/**
 * Creates a splash window with the splash image of [appInfo]. The window is not visible.
 */
@ApiStatus.Internal
@RequiresEdt
fun blockingCreateSplash(appInfo: ApplicationInfo): Window {
  val splashImage = runWithModalProgressBlocking(guess(), title = "") {
    loadSplashImage(appInfo)
  }!!
  return Splash(splashImage)
}

private suspend fun loadSplashImage(appInfo: ApplicationInfo): BufferedImage? {
  val splashImagePath = appInfo.splashImageUrl?.let { if (it.startsWith('/')) it.substring(1) else it } ?: return null

  val isJreHiDPIEnabled = JreHiDpiUtil.isJreHiDPIEnabled()
  val scale = if (isJreHiDPIEnabled) JBUIScale.sysScale() * JBUIScale.scale(1f) else JBUIScale.scale(1f)
  val [cacheFile, prefixHash] = try {
    getCacheFile(scale, splashImagePath, appInfo.build.asString())
  }
  catch (e: Throwable) {
    logger().warn(e)
    null to ""
  }

  currentCoroutineContext().ensureActive()

  if (cacheFile != null) {
    loadRaster(cacheFile, scale, isJreHiDPIEnabled)?.let {
      return it
    }
  }

  currentCoroutineContext().ensureActive()

  val raster = rasterize(splashImagePath, scale, isJreHiDPIEnabled)
               ?: throw IllegalStateException("Cannot find image: ${appInfo.splashImageUrl}")

  if (cacheFile != null) {
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(Dispatchers.IO) {
      try {
        val rawImage = (if (raster is JBHiDPIScaledImage) raster.delegate else raster) as BufferedImage
        writeRaster(cacheFile, rawImage, prefixHash)
      }
      catch (e: Throwable) {
        logger().warn("Cannot save splash image", e)
      }
    }
  }

  return raster
}

private fun getCacheFile(scale: Float, path: String, build: String): Pair<Path, String> {
  val hasher = Hashing.komihash5_0().hashStream()
  hasher.putChars(build)
  hasher.putInt(Splash::class.java.classLoader.getResourceAsStream(path)?.use { it.available() } ?: 0)
  hasher.putChars(path)
  val hashStr = java.lang.Long.toUnsignedString(hasher.asLong, Character.MAX_RADIX)
  val scaleStr = Integer.toUnsignedString(scale.toBits(), Character.MAX_RADIX)
  return PathManager.getSystemDir().resolve("splash", "${hashStr}_${scaleStr}.v3.ij") to hashStr
}

private suspend fun loadRaster(file: Path, scale: Float, isJreHiDPIEnabled: Boolean): BufferedImage? {
  try {
    Arena.ofShared().use { arena ->
      val segment = try {
        withContext(Dispatchers.IO) {
          FileChannel.open(file).use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena)
          }
        }
      }
      catch (_: NoSuchFileException) {
        return null
      }

      currentCoroutineContext().ensureActive()

      val w = segment.get(LE_INT, 0)
      val h = segment.get(LE_INT, Int.SIZE_BYTES.toLong())
      val dataBuffer = DataBufferInt(w * h)
      MemorySegment.copy(segment, LE_INT, 2L * Int.SIZE_BYTES, SunWritableRaster.stealData(dataBuffer, 0), 0, w * h)
      SunWritableRaster.makeTrackable(dataBuffer)
      val colorModel = ColorModel.getRGBdefault() as DirectColorModel
      val raster = Raster.createPackedRaster(dataBuffer, w, h, w, colorModel.masks, Point(0, 0))
      val rawImage = BufferedImage(colorModel, raster, /*isRasterPremultiplied =*/ false, /*properties =*/ null)
      return createHiDpiAwareImage(rawImage, scale, isJreHiDPIEnabled)
    }
  }
  catch (e: CancellationException) { throw e }
  catch (e: Throwable) {
    logger().warn("Failed to load splash image", e)
    return null
  }
}

@Suppress("UndesirableClassUsage", "UseJBColor")
private fun rasterize(path: String, scale: Float, isJreHiDPIEnabled: Boolean): BufferedImage? {
  val originalImage = loadImageForStartUp(path, scale, Splash::class.java.classLoader) ?: return null
  val w = originalImage.width
  val h = originalImage.height
  val resultImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
  resultImage.createGraphics().use { g2 ->
    g2.composite = AlphaComposite.Src
    ImageUtil.applyQualityRenderingHints(g2)
    g2.color = Color.WHITE
    val cornerRadius = 8 * scale
    g2.fill(RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius))
    g2.composite = AlphaComposite.SrcIn
    g2.drawImage(originalImage, 0, 0, null)
  }
  return createHiDpiAwareImage(resultImage, scale, isJreHiDPIEnabled)
}

private fun createHiDpiAwareImage(rawImage: BufferedImage, scale: Float, isJreHiDPIEnabled: Boolean): BufferedImage = when {
  isJreHiDPIEnabled -> {
    val width = rawImage.width.toDouble() / scale
    val height = rawImage.height.toDouble() / scale
    HiDPIImage(rawImage, width, height, BufferedImage.TYPE_INT_ARGB)
  }
  else -> rawImage
}

private fun writeRaster(file: Path, image: BufferedImage, prefixHash: String) {
  val cacheDir = NioFiles.createDirectories(file.parent)
  val tempFile = Files.createTempFile(cacheDir, file.fileName.toString(), ".tmp")
  try {
    val imageData = (image.raster.dataBuffer as DataBufferInt).data
    val size = (2L + imageData.size) * Int.SIZE_BYTES
    FileChannel.open(tempFile, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
      Arena.ofConfined().use { arena ->
        val segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, size, arena)
        segment.set(LE_INT, 0, image.width)
        segment.set(LE_INT, Int.SIZE_BYTES.toLong(), image.height)
        MemorySegment.copy(imageData, 0, segment, LE_INT, 2L * Int.SIZE_BYTES, imageData.size)
      }
    }

    try {
      Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE)
    }
    catch (_: AtomicMoveNotSupportedException) {
      Files.move(tempFile, file)
    }

    deleteStaleRasterFiles(cacheDir, prefixHash + '_')
  }
  finally {
    Files.deleteIfExists(tempFile)
  }
}

private fun deleteStaleRasterFiles(cacheDir: Path, prefix: String) {
  try {
    Files.newDirectoryStream(cacheDir).use { dirStream ->
      dirStream.forEach { file ->
        if (!file.name.startsWith(prefix)) {
          Files.deleteIfExists(file)
        }
      }
    }
  }
  catch (e: IOException) {
    logger().warn("A problem by cleaning splash cache directory ${cacheDir}", e)
  }
}
