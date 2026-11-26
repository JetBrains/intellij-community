// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.platform.jbr.JdkEx
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.ui.mac.MacWinTabsHandlerV2
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane

/**
 * Creates the parts of the project tabs for the current OS.
 */
@ApiStatus.Internal
object ProjectTabsManager {
  /**
   * Creates the project tabs handler for the frame decorator of [frame].
   * Returns empty implementation when the OS does not support the project tabs.
   */
  fun createFrameHandler(frame: IdeFrameImpl, coroutineScope: CoroutineScope): ProjectTabsHandler {
    if (SystemInfoRt.isMac) {
      return if (MacWinTabsHandler.isVersion2()) MacWinTabsHandlerV2(frame, coroutineScope) else MacWinTabsHandler(frame, coroutineScope)
    }
    if (WindowTabsManager.enabled) {
      WindowTabsManager.addFrame(frame, coroutineScope)
      return WindowTabsManager
    }
    return object : ProjectTabsHandler {
      override fun appClosing() {
      }
    }
  }

  /**
   * Creates the component that shows the project tabs in [rootPane].
   * Returns `null` when the OS does not support the project tabs.
   */
  fun createRootPaneComponent(rootPane: JRootPane): JComponent? {
    if (SystemInfoRt.isMac) {
      if (JdkEx.isTabbingModeAvailable()) {
        return MacWinTabsHandler.createAndInstallHandlerComponent(rootPane)
      }
      return null
    }
    if (WindowTabsManager.enabled) {
      return WindowTabsManager.createAndInstallHandlerComponent(rootPane)
    }
    return null
  }

  fun fastInit(frame: IdeFrameImpl) {
    if (SystemInfoRt.isMac) {
      MacWinTabsHandler.fastInit(frame)
    }
  }

  /**
   * Returns `true` when [frame] shares the project tabs with one or more other frames.
   */
  fun isTabbedWindow(frame: JFrame): Boolean {
    if (SystemInfoRt.isMac) {
      return MacWinTabsHandler.isTabbedWindow(frame)
    }
    if (WindowTabsManager.enabled) {
      return WindowTabsManager.isTabbedWindow(frame)
    }
    return false
  }

  fun switchFrameIfPossible(frame: JFrame, next: Boolean) {
    if (SystemInfoRt.isMac) {
      MacWinTabsHandler.switchFrameIfPossible(frame, next)
    }
    else if (WindowTabsManager.enabled) {
      WindowTabsManager.switchFrameIfPossible(frame, next)
    }
  }

  /**
   * Makes [window] the current frame of the project tabs when the project tabs hide it.
   * Call it before you bring [window] to the front, because a hidden frame cannot come to the front.
   * Does nothing on macOS and when [WindowTabsManager] is off.
   */
  fun selectTab(window: Window?) {
    if (window is IdeFrameImpl && !SystemInfoRt.isMac && WindowTabsManager.enabled) {
      WindowTabsManager.selectTab(window)
    }
  }
}
