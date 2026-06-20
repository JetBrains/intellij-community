// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.frame

// @spec community/plugins/agent-workbench/spec/frame/agent-dedicated-frame.spec.md

import com.intellij.agent.workbench.chat.AGENT_CHAT_THREAD_OUTLINE_TOOL_WINDOW_ID
import com.intellij.agent.workbench.chat.AgentChatThreadOutlinePanel
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.settings.AgentWorkbenchSettingsListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

internal class AgentChatThreadOutlineToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(toolWindow: ToolWindow) {
    toolWindow.setStripeTitle(AgentSessionsBundle.message("toolwindow.thread.outline.title"))
  }

  override fun shouldBeAvailable(project: Project): Boolean {
    return isAgentChatThreadOutlineToolWindowAvailable(project)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.title = AgentSessionsBundle.message("toolwindow.thread.outline.title")
    val panel = AgentChatThreadOutlinePanel(project)
    val content = ContentFactory.getInstance().createContent(panel, null, false)
    content.setDisposer(panel)
    toolWindow.contentManager.addContent(content)
  }
}

internal fun isAgentChatThreadOutlineToolWindowAvailable(project: Project): Boolean {
  return AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project) || !AgentChatOpenModeSettings.openInDedicatedFrame()
}

internal class AgentChatThreadOutlineAvailabilitySettingsListener : AgentWorkbenchSettingsListener {
  override fun openInDedicatedFrameChanged() {
    refreshAgentChatThreadOutlineToolWindowAvailability()
  }
}

internal fun refreshAgentChatThreadOutlineToolWindowAvailability(
  projects: Array<Project> = ProjectManager.getInstance().openProjects,
  toolWindowProvider: (Project) -> ToolWindow? = { project ->
    ToolWindowManager.getInstance(project).getToolWindow(AGENT_CHAT_THREAD_OUTLINE_TOOL_WINDOW_ID)
  },
  setAvailable: (ToolWindow, Boolean) -> Unit = { toolWindow, available -> toolWindow.setAvailable(available, null) },
): Int {
  var updatedToolWindows = 0
  projects.forEach { project ->
    if (project.isDisposed) return@forEach
    val toolWindow = toolWindowProvider(project) ?: return@forEach
    setAvailable(toolWindow, isAgentChatThreadOutlineToolWindowAvailable(project))
    updatedToolWindows++
  }
  return updatedToolWindows
}
