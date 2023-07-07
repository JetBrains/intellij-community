// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.mergeMainMenuWithWindowTitleOverrideValue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScreenUtil
import com.intellij.ui.mac.MacMainFrameDecorator
import com.intellij.util.awaitCancellationAndInvoke
import com.jetbrains.JBR
import kotlinx.coroutines.*
import java.awt.EventQueue
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.lang.Runnable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JRootPane
import javax.swing.SwingUtilities

private val LOG: Logger
  get() = logger<IdeFrameDecorator>()

abstract class IdeFrameDecorator protected constructor(protected @JvmField val frame: IdeFrameImpl) {
  companion object {
    const val FULL_SCREEN = "ide.frame.full.screen"

    fun decorate(frame: IdeFrameImpl, glassPane: IdeGlassPane, coroutineScope: CoroutineScope): IdeFrameDecorator? {
      try {
        return when {
          SystemInfoRt.isMac -> MacMainFrameDecorator(frame, glassPane, parentDisposable)
          SystemInfoRt.isWindows -> WinMainFrameDecorator(frame)
          SystemInfoRt.isXWindow && X11UiUtil.isFullScreenSupported() -> EWMHFrameDecorator(frame, coroutineScope)
          else -> null
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.warn("Failed to initialize IdeFrameDecorator", e)
      }
      return null
    }

    val isCustomDecorationAvailable: Boolean
      get() = (SystemInfoRt.isMac || SystemInfo.isWin8OrNewer) && JBR.isWindowDecorationsSupported()

    private val isCustomDecorationActiveCache = AtomicReference<Boolean>()

    fun isCustomDecorationActive(): Boolean {
      if (!LoadingState.COMPONENTS_REGISTERED.isOccurred) {
        // true by default if no settings are available (e.g., during the initial IDE setup wizard) and not overridden (only for Windows)
        return isCustomDecorationAvailable && defaultCustomDecorationState
      }

      // Cache the initial value received from settings, because this value doesn't support change in runtime (we can't redraw frame headers
      // of frames already created, and changing this setting during any frame lifetime will cause weird effects).
      return isCustomDecorationActiveCache.updateAndGet { cached ->
        if (cached != null) {
          return@updateAndGet cached
        }
        if (!isCustomDecorationAvailable) {
          return@updateAndGet false
        }
        val override = mergeMainMenuWithWindowTitleOverrideValue
        if (override != null) {
          return@updateAndGet override
        }
        UISettings.getInstance().mergeMainMenuWithWindowTitle
      }
    }

    private val defaultCustomDecorationState: Boolean
      get() = SystemInfoRt.isWindows && mergeMainMenuWithWindowTitleOverrideValue != false
  }

  abstract val isInFullScreen: Boolean

  open fun setStoredFullScreen() {
    notifyFrameComponents(state = true)
  }

  open fun setProject() {
  }

  open val isTabbedWindow: Boolean
    get() = false

  /**
   * Returns applied state or rejected promise if it cannot be applied.
   */
  abstract fun toggleFullScreen(state: Boolean): Deferred<Boolean?>

  protected fun notifyFrameComponents(state: Boolean) {
    frame.rootPane.putClientProperty(FULL_SCREEN, state)
    frame.jMenuBar?.putClientProperty(FULL_SCREEN, state)
  }

  open fun appClosing() {}

  init {
    this.frame = frame
  }
}

// AWT-based decorator
private class WinMainFrameDecorator(frame: IdeFrameImpl) : IdeFrameDecorator(frame) {
  override val isInFullScreen: Boolean
    get() = ClientProperty.isTrue(frame, FULL_SCREEN)

  override fun toggleFullScreen(state: Boolean): CompletableFuture<Boolean?> {
    val promise = CompletableFuture<Boolean?>()
    SwingUtilities.invokeLater(Runnable {
      val bounds = frame.bounds
      val extendedState = frame.extendedState
      val rootPane = frame.rootPane
      if (state && extendedState == Frame.NORMAL) {
        frame.normalBounds = bounds
        if (IDE_FRAME_EVENT_LOG.isDebugEnabled()) { // avoid unnecessary concatenation
          IDE_FRAME_EVENT_LOG.debug("Saved normal bounds of the frame before entering full screen: " + frame.normalBounds)
        }
      }
      val device = ScreenUtil.getScreenDevice(bounds)
      if (device == null) {
        promise.complete(null)
        return@Runnable
      }
      val toFocus = frame.mostRecentFocusOwner
      val defaultBounds = device.defaultConfiguration.bounds
      if (state) {
        frame.screenBounds = defaultBounds
        if (IDE_FRAME_EVENT_LOG.isDebugEnabled()) { // avoid unnecessary concatenation
          IDE_FRAME_EVENT_LOG.debug("Saved screen bounds of the frame before entering full screen: " + frame.screenBounds)
        }
      }
      try {
        frame.togglingFullScreenInProgress = true
        rootPane.putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, java.lang.Boolean.TRUE)
        frame.dispose()
        frame.isUndecorated = state
      }
      finally {
        if (state) {
          frame.bounds = defaultBounds
        }
        else {
          val o = frame.normalBounds
          if (o != null) {
            frame.bounds = o
          }
        }
        frame.isVisible = true
        rootPane.putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, null)
        if (!state && extendedState and Frame.MAXIMIZED_BOTH != 0) {
          frame.setExtendedState(extendedState)
        }
        notifyFrameComponents(state)
        if (toFocus != null && toFocus !is JRootPane) {
          // Window 'forgets' last focused component on disposal, so we need to restore it explicitly.
          // Special case is toggling fullscreen mode from a menu.
          // In this case menu UI moves focus to the root pane before performing the action.
          // We shouldn't explicitly request focus in this case - menu UI will restore the focus without our help.
          toFocus.requestFocusInWindow()
        }
      }
      EventQueue.invokeLater(Runnable { frame.togglingFullScreenInProgress = false })
      promise.complete(state)
    })
    return promise
  }
}

// Extended WM Hints-based decorator
private class EWMHFrameDecorator(frame: IdeFrameImpl, coroutineScope: CoroutineScope) : IdeFrameDecorator(frame) {
  private var requestedState: Boolean? = null

  override val isInFullScreen: Boolean
    get() = X11UiUtil.isInFullScreenMode(frame)

  init {
    val frameResizeListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        if (requestedState != null) {
          notifyFrameComponents(requestedState!!)
          requestedState = null
        }
      }
    }
    frame.addComponentListener(frameResizeListener)
    executeOnCancelInEdt(coroutineScope) {
      frame.removeComponentListener(frameResizeListener)
    }

    if (SystemInfo.isKDE && DialogWrapperPeerImpl.isDisableAutoRequestFocus()) {
      // KDE sends an unexpected MapNotify event if a window is deiconified.
      // suppress.focus.stealing fix handles the MapNotify event differently
      // if the application is not active
      val listener = object : WindowAdapter() {
        override fun windowDeiconified(event: WindowEvent) {
          frame.toFront()
        }
      }
      frame.addWindowListener(listener)
      executeOnCancelInEdt(coroutineScope) {
        frame.removeWindowListener(listener)
      }
    }
  }

  override fun toggleFullScreen(state: Boolean): Deferred<Boolean?> {
    requestedState = state
    X11UiUtil.toggleFullScreenMode(frame)
    val menuBar = frame.jMenuBar
    if (menuBar is IdeMenuBar) {
      menuBar.onToggleFullScreen(state)
    }
    return CompletableDeferred(value = state)
  }
}

private fun executeOnCancelInEdt(coroutineScope: CoroutineScope, task: () -> Unit) {
  coroutineScope.launch {
    awaitCancellationAndInvoke {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        task()
      }
    }
  }
}
