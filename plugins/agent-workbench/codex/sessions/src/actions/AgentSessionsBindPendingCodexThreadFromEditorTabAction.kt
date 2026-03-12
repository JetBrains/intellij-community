// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabActionBase
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.agent.workbench.sessions.service.isPendingEditorContext
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class AgentSessionsBindPendingCodexThreadFromEditorTabAction @JvmOverloads constructor(
  private val resolveTarget: (AgentChatEditorTabActionContext) -> AgentChatTabRebindTarget? = { context ->
    service<AgentSessionReadService>().resolvePendingThreadRebindTarget(context, AgentSessionProvider.CODEX)
  },
  private val rebindPendingTab: (Map<String, List<AgentChatPendingCodexTabRebindRequest>>) -> Unit =
    { requestsByPath ->
      service<AgentSessionRefreshService>().rebindPendingTabsInBackground(AgentSessionProvider.CODEX, requestsByPath)
    },
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    if (!isPendingEditorContext(context, AgentSessionProvider.CODEX)) {
      return
    }
    if (context.tabKey.isBlank()) {
      return
    }
    val target = resolveTarget(context) ?: return
    val request = AgentChatPendingCodexTabRebindRequest(
      pendingTabKey = context.tabKey,
      pendingThreadIdentity = context.threadIdentity,
      target = target,
    )
    rebindPendingTab(mapOf(context.path to listOf(request)))
  }

  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContext(e)
    if (context == null || !isPendingEditorContext(context, AgentSessionProvider.CODEX)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = resolveTarget(context) != null
  }
}
