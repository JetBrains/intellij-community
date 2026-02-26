// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

internal class AgentSessionsGoToSourceProjectFromEditorTabAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?
  private val isDedicatedProject: (Project) -> Boolean
  private val openProject: (String) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentChatEditorTabActionContext
    isDedicatedProject = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject
    openProject = { path -> service<AgentSessionsService>().openOrFocusProject(path) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?,
    isDedicatedProject: (Project) -> Boolean,
    openProject: (String) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.isDedicatedProject = isDedicatedProject
    this.openProject = openProject
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    if (!isDedicatedProject(context.project)) {
      return
    }
    if (AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(context.path)) {
      return
    }
    openProject(context.path)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val enabled =
      context != null &&
      isDedicatedProject(context.project) &&
      !AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(context.path)
    e.presentation.isEnabledAndVisible = enabled
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
