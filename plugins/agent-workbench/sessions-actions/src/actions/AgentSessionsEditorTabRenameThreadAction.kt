// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionRenameService
import com.intellij.agent.workbench.sessions.service.showRenameThreadDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

internal class AgentSessionsEditorTabRenameThreadAction @JvmOverloads constructor(
  private val canRenameProvider: (AgentSessionProvider) -> Boolean = { provider ->
    service<AgentSessionRenameService>().canRenameProvider(provider)
  },
  private val renameThread: (SessionActionTarget.Thread, String) -> Unit = { target, requestedName ->
    service<AgentSessionRenameService>().renameThread(target, requestedName)
  },
  private val promptForName: (Project, String) -> String? = ::showRenameThreadDialog,
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContextOrHide(e) ?: return
    val target = context.sessionActionTarget as? SessionActionTarget.Thread
    e.presentation.text = templatePresentation.textWithMnemonic
    e.presentation.isVisible = target != null
    e.presentation.isEnabled = target != null && canRenameProvider(target.provider)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val target = context.sessionActionTarget as? SessionActionTarget.Thread ?: return
    if (!canRenameProvider(target.provider)) {
      return
    }

    val requestedName = promptForName(context.project, target.title) ?: return
    renameThread(target, requestedName)
  }
}
