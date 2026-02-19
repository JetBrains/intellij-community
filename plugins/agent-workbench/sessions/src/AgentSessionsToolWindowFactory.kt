// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.jetbrains.jewel.bridge.compose

internal class AgentSessionsToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(toolWindow: ToolWindow) {
    toolWindow.setStripeTitle(AgentSessionsBundle.message("toolwindow.title"))
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.title = AgentSessionsBundle.message("toolwindow.title")
    val contentFactory = ContentFactory.getInstance()
    val panel = compose { agentSessionsToolWindow(project) }
    val content = contentFactory.createContent(panel, null, false)
    toolWindow.contentManager.addContent(content)
    toolWindow.setAdditionalGearActions(ActionUtil.getActionGroup("AgentWorkbenchSessions.ToolWindow.GearActions"))
  }
}
