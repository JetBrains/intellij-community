// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentThreadQuickStartService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

internal class AgentChatNewThreadFromEditorTabAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?
  private val quickStartService: () -> AgentThreadQuickStartService?

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentChatEditorTabActionContext
    quickStartService = AgentThreadQuickStartService::getInstance
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?,
    quickStartService: () -> AgentThreadQuickStartService?,
  ) {
    this.resolveContext = resolveContext
    this.quickStartService = quickStartService
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val quickStart = quickStartService() ?: return
    if (!quickStart.isVisible(context.project) || !quickStart.isEnabled(context.project)) {
      return
    }
    quickStart.startNewThread(path = context.path, project = context.project)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val quickStart = quickStartService()
    if (context == null || quickStart == null || !quickStart.isVisible(context.project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = quickStart.isEnabled(context.project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal data class AgentChatEditorTabActionContext(
  val project: Project,
  val path: String,
)

internal fun resolveAgentChatEditorTabActionContext(event: AnActionEvent): AgentChatEditorTabActionContext? {
  val project = event.project ?: return null
  val selectedChatTab = project.service<AgentChatTabSelectionService>().selectedChatTab.value ?: return null
  return AgentChatEditorTabActionContext(
    project = project,
    path = normalizeAgentWorkbenchPath(selectedChatTab.projectPath),
  )
}
