// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.thread.view.AgentThreadViewEditorTabActionContext
import com.intellij.agent.workbench.thread.view.resolveAgentThreadViewEditorTabActionContext
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionRenameService
import com.intellij.agent.workbench.sessions.service.showRenameThreadDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

internal class AgentSessionsEditorTabRenameThreadAction @JvmOverloads constructor(
  private val canRenameThread: (AgentThreadViewEditorTabActionContext, SessionActionTarget.Thread) -> Boolean = { context, target ->
    service<AgentSessionRenameService>().canRenameThreadInEditorTab(context, target)
  },
  private val renameThread: (AgentThreadViewEditorTabActionContext, SessionActionTarget.Thread, String) -> Unit = { context, target, requestedName ->
    service<AgentSessionRenameService>().renameThreadFromEditorTab(context, target, requestedName)
  },
  private val promptForName: (Project, String) -> String? = ::showRenameThreadDialog,
  resolveContext: (AnActionEvent) -> AgentThreadViewEditorTabActionContext? = ::resolveAgentThreadViewEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContextOrHide(e) ?: return
    val target = context.sessionActionTarget as? SessionActionTarget.Thread
    e.presentation.text = templatePresentation.textWithMnemonic
    e.presentation.isVisible = target != null
    e.presentation.isEnabled = target != null && canRenameThread(context, target)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val target = context.sessionActionTarget as? SessionActionTarget.Thread ?: return
    if (!canRenameThread(context, target)) {
      return
    }

    val requestedName = promptForName(context.project, target.title) ?: return
    renameThread(context, target, requestedName)
  }
}
