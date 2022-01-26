// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.ExperimentalUI

private class ToolwindowToolbarListener(val project: Project) : ToolWindowManagerListener {
  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    if (!ExperimentalUI.isNewToolWindowsStripes() || toolWindowManager !is ToolWindowManagerImpl) {
      return
    }
    toolWindowManager.updateSquareButtons()
  }

  override fun toolWindowShown(toolWindow: ToolWindow) {
    if (!ExperimentalUI.isNewToolWindowsStripes() || toolWindow.isVisibleOnLargeStripe) {
      return
    }

    toolWindow.isVisibleOnLargeStripe = true
    toolWindow.setLargeStripeAnchor(toolWindow.largeStripeAnchor, -1)
  }
}
