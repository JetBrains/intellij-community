// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.service.AgentSessionsService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

internal class AgentSessionsGoToSourceProjectFromEditorTabAction @JvmOverloads constructor(
  private val isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  private val openProject: (String) -> Unit = { path -> service<AgentSessionsService>().openOrFocusProject(path) },
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    if (!isDedicatedProject(context.project)) {
      return
    }
    if (AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(context.path)) {
      return
    }
    openProject(context.path)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContext(e)
    val enabled =
      context != null &&
      isDedicatedProject(context.project) &&
      !AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(context.path)
    e.presentation.isEnabledAndVisible = enabled
  }
}
