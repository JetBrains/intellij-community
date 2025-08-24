// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.demo.dialog

import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.devkit.compose.demo.JewelIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.idea.devkit.util.PsiUtil

private const val TOOLWINDOW_ID = "JewelDemo"

internal class JewelToolwindowAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && PsiUtil.isPluginProject(e.project!!)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(TOOLWINDOW_ID)

    if (toolWindow == null) {
      registerToolwindow(toolWindowManager).activate { }
    }
    else {
      toolWindow.activate { }
    }
  }

  private fun registerToolwindow(toolWindowManager: ToolWindowManager): ToolWindow {
    val toolWindow = toolWindowManager.registerToolWindow(
      RegisterToolWindowTask(
        id = TOOLWINDOW_ID,
        anchor = ToolWindowAnchor.BOTTOM,
        component = null,
        icon = JewelIcons.ToolWindowIcon,
        contentFactory = com.intellij.devkit.compose.demo.JewelDemoToolWindowFactory(),
        stripeTitle = DevkitComposeBundle.messagePointer("jewel.toolwindow.title"),
      )
    )
    return toolWindow
  }
}
