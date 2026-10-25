// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector

import com.intellij.devkit.icons.inspector.ui.IconInspectorWindows
import com.intellij.devkit.icons.inspector.ui.InspectorMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/** Must match the id of the action registered in `intellij.devkit.icons.inspector.xml`. */
internal const val DETACH_ACTION_ID: String = "IconInspector.Detach"

/** Moves the Icon Inspector from its tool window to the detached window ([InspectorMode.DETACHED]). */
internal class DetachIconInspectorAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isApplicable(e.project, InspectorMode.DOCKED)
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<IconInspectorWindows>()?.detach()
  }
}

/** Closes the detached window and shows the Icon Inspector tool window ([InspectorMode.DOCKED]). */
internal class DockIconInspectorAction : DumbAwareAction() {
  init {
    // the detached window is usable while a modal dialog is open
    isEnabledInModalContext = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isApplicable(e.project, InspectorMode.DETACHED)
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<IconInspectorWindows>()?.dock()
  }
}

private fun isApplicable(project: Project?, mode: InspectorMode): Boolean =
  ApplicationManager.getApplication().isInternal && project != null && project.service<IconInspectorWindows>().mode == mode

/** Shows the detached window instead of the tool window when the user opens the tool window in [InspectorMode.DETACHED]. */
internal class IconInspectorToolWindowListener(private val project: Project) : ToolWindowManagerListener {
  override fun toolWindowShown(toolWindow: ToolWindow) {
    if (toolWindow.id != ICON_INSPECTOR_TOOLWINDOW_ID || !ApplicationManager.getApplication().isInternal) return
    project.service<IconInspectorWindows>().toolWindowShown(toolWindow)
  }
}
