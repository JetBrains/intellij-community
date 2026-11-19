// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.platform.jbr.JdkEx
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.ui.mac.MacWinTabsHandlerV2
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
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
    return object : ProjectTabsHandler {
      override fun setProject() {
      }

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
    return SystemInfoRt.isMac && MacWinTabsHandler.isTabbedWindow(frame)
  }

  fun switchFrameIfPossible(frame: JFrame, next: Boolean) {
    if (SystemInfoRt.isMac) {
      MacWinTabsHandler.switchFrameIfPossible(frame, next)
    }
  }
}
