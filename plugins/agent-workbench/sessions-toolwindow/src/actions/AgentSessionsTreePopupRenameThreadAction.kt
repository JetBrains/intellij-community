// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionRenameService
import com.intellij.agent.workbench.sessions.service.showRenameThreadDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

internal class AgentSessionsTreePopupRenameThreadAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val canRenameThread: (SessionActionTarget.Thread) -> Boolean
  private val renameThread: (SessionActionTarget.Thread, String) -> Unit
  private val promptForName: (Project, String) -> String?

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    canRenameThread = { target -> service<AgentSessionRenameService>().canRenameThreadInTree(target) }
    renameThread = { target, requestedName -> service<AgentSessionRenameService>().renameThreadFromTree(target, requestedName) }
    promptForName = ::showRenameThreadDialog
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    canRenameThread: (SessionActionTarget.Thread) -> Boolean,
    renameThread: (SessionActionTarget.Thread, String) -> Unit,
    promptForName: (Project, String) -> String?,
  ) {
    this.resolveContext = resolveContext
    this.canRenameThread = canRenameThread
    this.renameThread = renameThread
    this.promptForName = promptForName
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val target = context?.target as? SessionActionTarget.Thread
    e.presentation.isVisible = target != null
    e.presentation.isEnabled = target != null && canRenameThread(target)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val target = context.target as? SessionActionTarget.Thread ?: return
    if (!canRenameThread(target)) {
      return
    }

    val requestedName = promptForName(context.project, target.title) ?: return
    renameThread(target, requestedName)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
