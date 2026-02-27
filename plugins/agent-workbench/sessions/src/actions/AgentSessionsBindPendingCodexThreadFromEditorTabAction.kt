// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget
import com.intellij.agent.workbench.chat.rebindOpenPendingCodexTabs
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.service.PendingCodexRebindTargetResolver
import com.intellij.agent.workbench.sessions.service.isPendingCodexEditorContext
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

internal class AgentSessionsBindPendingCodexThreadFromEditorTabAction @JvmOverloads constructor(
  private val resolveTarget: (AgentChatEditorTabActionContext) -> AgentChatPendingTabRebindTarget? = { context ->
    service<PendingCodexRebindTargetResolver>().resolve(context)
  },
  private val rebindPendingTab: (Map<String, List<AgentChatPendingCodexTabRebindRequest>>) -> AgentChatPendingCodexTabRebindReport =
    ::rebindOpenPendingCodexTabs,
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    if (!isPendingCodexEditorContext(context)) {
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
    if (context == null || !isPendingCodexEditorContext(context)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = resolveTarget(context) != null
  }
}
