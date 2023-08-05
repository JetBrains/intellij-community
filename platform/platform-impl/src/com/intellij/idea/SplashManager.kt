// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.FrameBoundsConverter
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.Splash
import com.intellij.ui.loadSplashImage
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.*
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.WindowConstants

@Volatile
private var PROJECT_FRAME: JFrame? = null

@Volatile
private var SPLASH_WINDOW: Splash? = null

// if hideSplash requested before we show splash, we should not try to show splash
@Volatile
private var splashJob: AtomicReference<Job> = AtomicReference(CompletableDeferred<Unit>())

internal suspend fun showSplashIfNeeded(initUiDeferred: Job, appInfoDeferred: Deferred<ApplicationInfoEx>) {
  // A splash instance must not be created before base LaF is created.
  // It is important on Linux, where GTK LaF must be initialized (to properly set up the scale factor).
  // https://youtrack.jetbrains.com/issue/IDEA-286544
  initUiDeferred.join()

  val appInfo = appInfoDeferred.await()
  try {
    if (splashJob.get().isCancelled) {
      return
    }

    if (showLastProjectFrameIfAvailable()) {
      return
    }

    coroutineScope {
      val oldJob = splashJob.getAndSet(launch {
        val image = span("splash preparation") {
          assert(SPLASH_WINDOW == null)
          loadSplashImage(appInfo = appInfo)
        }

        if (!isActive || LoadingState.COMPONENTS_LOADED.isOccurred) {
          return@launch
        }

        // by intention out of withContext(RawSwingDispatcher) - measure "schedule" time
        span("splash initialization") {
          if (LoadingState.COMPONENTS_LOADED.isOccurred) {
            return@span
          }

          withContext(RawSwingDispatcher) {
            if (LoadingState.COMPONENTS_LOADED.isOccurred) {
              return@withContext
            }

            val splash = Splash(image)
            StartUpMeasurer.addInstantEvent("splash shown")
            SPLASH_WINDOW = splash
            span("splash set visible") {
              splash.isVisible = true
            }
            splash.toFront()
          }
        }
      })
      if (oldJob.isCancelled) {
        splashJob.get().cancel()
      }
    }
  }
  catch (ignore: CancellationException) {
    val window = SPLASH_WINDOW
    if (window != null) {
      SPLASH_WINDOW = null
      withContext(NonCancellable + RawSwingDispatcher) {
        window.isVisible = false
        window.dispose()
      }
    }
  }
  catch (e: Throwable) {
    logger<StartupUiUtil>().warn("Cannot show splash", e)
  }
}

private suspend fun showLastProjectFrameIfAvailable(): Boolean {
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
  splashJob.get().cancel()
  if (SPLASH_WINDOW != null || PROJECT_FRAME != null) {
    window.addWindowListener(object : WindowAdapter() {
      override fun windowOpened(e: WindowEvent) {
        hideSplash()
        window.removeWindowListener(this)
      }
    })
  }
}

fun hideSplash() {
  splashJob.get().cancel()

  var window: Window? = SPLASH_WINDOW
  if (window == null) {
    window = PROJECT_FRAME ?: return
    PROJECT_FRAME = null
  }
  else {
    SPLASH_WINDOW = null
  }

  StartUpMeasurer.addInstantEvent("splash hidden")
  window.isVisible = false
  window.dispose()
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