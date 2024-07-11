// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.console

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindowManager

internal class ShowVcsConsoleTabAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
    if (toolWindow == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val consoleTabService = project.serviceIfCreated<VcsConsoleTabService>()
    e.presentation.isEnabledAndVisible = consoleTabService != null &&
                                         consoleTabService.hadMessages() &&
                                         !consoleTabService.isConsoleVisible()
  }

  override fun actionPerformed(e: AnActionEvent) {
    VcsConsoleTabService.getInstance(e.project!!).showConsoleTabAndScrollToTheEnd()
  }
}
