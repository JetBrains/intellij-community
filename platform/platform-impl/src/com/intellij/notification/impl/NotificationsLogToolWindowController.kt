// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.notification.NotificationsLogController
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager

internal class NotificationsLogToolWindowController(private val project: Project) : NotificationsLogController {
  override fun show() {
    findToolWindow(project)?.show()
  }

  override fun activate(focus: Boolean) {
    findToolWindow(project)?.activate(null, focus)
  }

  override fun toggle() {
    val toolWindow = findToolWindow(project) ?: return
    if (toolWindow.isVisible) {
      toolWindow.hide()
    }
    else {
      toolWindow.activate(null)
    }
  }

  private fun findToolWindow(project: Project): ToolWindow? {
    return ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID)
  }
}