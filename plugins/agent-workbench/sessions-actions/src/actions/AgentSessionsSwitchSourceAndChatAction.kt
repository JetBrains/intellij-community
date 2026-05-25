// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.service.AgentSourceChatSwitching
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

internal class AgentSessionsSwitchSourceAndChatAction @JvmOverloads constructor(
  private val isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  private val selectedSourceProjectPath: (Project) -> String? = AgentSourceChatSwitching::selectedOpenableSourceProjectPath,
  private val switchSourceAndChat: (Project, AgentWorkbenchEntryPoint) -> Boolean = AgentSourceChatSwitching::switchSourceAndChat,
) : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (isDedicatedProject(project) && selectedSourceProjectPath(project) == null) return

    switchSourceAndChat(project, AgentWorkbenchEntryPoint.WINDOW_MENU)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.text = AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndChat.text")
    if (!isDedicatedProject(project)) {
      e.presentation.description = AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndChat.to.chat.description")
      e.presentation.isEnabledAndVisible = true
      return
    }

    val sourceProjectPath = selectedSourceProjectPath(project)
    e.presentation.isVisible = true
    if (sourceProjectPath == null) {
      e.presentation.isEnabled = false
      e.presentation.description = AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndChat.empty.description")
      return
    }

    e.presentation.isEnabled = true
    e.presentation.description =
      AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndChat.to.source.description", sourceProjectPath)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
