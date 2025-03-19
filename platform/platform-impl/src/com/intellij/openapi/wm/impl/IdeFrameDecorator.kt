// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ui.UISettings
import com.intellij.idea.AppMode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScreenUtil
import com.intellij.ui.mac.MacMainFrameDecorator
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.JBR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.Frame
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JRootPane

private val LOG: Logger
  get() = logger<IdeFrameDecorator>()

private val isCustomDecorationActiveCache = AtomicReference<Boolean>()

private val defaultCustomDecorationState: Boolean
  get() = SystemInfoRt.isWindows && mergeMainMenuWithWindowTitleOverrideValue != false

internal abstract class IdeFrameDecorator protected constructor(@JvmField protected val frame: IdeFrameImpl) {
  companion object {
    internal const val FULL_SCREEN = "ide.frame.full.screen"

    fun decorate(frame: IdeFrameImpl, glassPane: IdeGlassPane, coroutineScope: CoroutineScope): IdeFrameDecorator? {
      try {
        return when {
          SystemInfoRt.isMac -> MacMainFrameDecorator(frame, glassPane, coroutineScope)
          SystemInfoRt.isWindows -> WinMainFrameDecorator(frame)
          StartupUiUtil.isXToolkit() && X11UiUtil.isFullScreenSupported() -> EWMHFrameDecorator(frame)
          StartupUiUtil.isWaylandToolkit() && UIUtil.isFullScreenSupportedByDefaultGD() -> WLFrameDecorator(frame)
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

    internal val isCustomDecorationAvailable: Boolean
      get() = (SystemInfoRt.isMac || SystemInfo.isWin8OrNewer) && JBR.isWindowDecorationsSupported()

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
        mergeMainMenuWithWindowTitleOverrideValue?.let {
          return@updateAndGet it
        }
        UISettings.getInstance().mergeMainMenuWithWindowTitle
      }
    }
  }

  abstract val isInFullScreen: Boolean

  open fun setStoredFullScreen(state: Boolean) {
    notifyFrameComponents(state)
  }

  open fun setProject() {
  }

  open val isTabbedWindow: Boolean
    get() = false

  /**
   * Returns applied state or rejected promise if it cannot be applied.
   */
  abstract suspend fun toggleFullScreen(state: Boolean): Boolean?

  protected fun notifyFrameComponents(state: Boolean) {
    frame.rootPane.putClientProperty(FULL_SCREEN, state)
    frame.jMenuBar?.putClientProperty(FULL_SCREEN, state)
  }

  open fun appClosing() {}
}

// AWT-based decorator
private class WinMainFrameDecorator(frame: IdeFrameImpl) : IdeFrameDecorator(frame) {
  override val isInFullScreen: Boolean
    get() = ClientProperty.isTrue(frame, FULL_SCREEN)

  override suspend fun toggleFullScreen(state: Boolean): Boolean? {
    return withContext(RawSwingDispatcher) {
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
        return@withContext null
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
        rootPane.putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, true)
        frame.dispose()
        frame.isUndecorated = state
      }
      finally {
        if (state) {
          frame.bounds = defaultBounds
        }
        else {
          frame.normalBounds?.let {
            frame.bounds = it
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
      EventQueue.invokeLater { frame.togglingFullScreenInProgress = false }
      state
    }
  }
}

// Extended WM Hints-based decorator
private class EWMHFrameDecorator(frame: IdeFrameImpl) : IdeFrameDecorator(frame) {

  override val isInFullScreen: Boolean
    get() = ClientProperty.isTrue(frame, FULL_SCREEN)

  override suspend fun toggleFullScreen(state: Boolean): Boolean {
    X11UiUtil.toggleFullScreenMode(frame)
    val menuBar = frame.jMenuBar
    if (menuBar is IdeJMenuBar) {
      menuBar.onToggleFullScreen(state)
    }
    withContext(Dispatchers.EDT) {
      notifyFrameComponents(state)
    }
    return state
  }
}

private class WLFrameDecorator(frame: IdeFrameImpl) : IdeFrameDecorator(frame) {
  override val isInFullScreen: Boolean
    get() = ClientProperty.isTrue(frame, FULL_SCREEN)

  override suspend fun toggleFullScreen(state: Boolean): Boolean {
    withContext(RawSwingDispatcher) {
      val gd = frame.graphicsConfiguration.device
      gd.fullScreenWindow = if (isInFullScreen) null else frame
      notifyFrameComponents(state)

      val menuBar = frame.jMenuBar
      if (menuBar is IdeJMenuBar) {
        menuBar.onToggleFullScreen(state)
      }
    }
    return state
  }
}

internal const val MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY: String = "ide.win.frame.decoration"

private val mergeMainMenuWithWindowTitleOverrideValue: Boolean? = if (AppMode.isRemoteDevHost()) false else System.getProperty(MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY)?.toBoolean()
internal val isMergeMainMenuWithWindowTitleOverridden: Boolean
  get() = mergeMainMenuWithWindowTitleOverrideValue != null