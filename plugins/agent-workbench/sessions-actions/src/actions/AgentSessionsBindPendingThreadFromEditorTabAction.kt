// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class AgentSessionsBindPendingThreadFromEditorTabAction @JvmOverloads constructor(
  private val resolveTarget: (AgentChatEditorTabActionContext, AgentSessionProvider) -> AgentChatTabRebindTarget? = { context, provider ->
    service<AgentSessionReadService>().resolvePendingThreadRebindTarget(context, provider)
  },
  private val rebindPendingTab: (AgentSessionProvider, Map<String, List<AgentChatPendingTabRebindRequest>>) -> Unit =
    { provider, requestsByPath ->
      service<AgentSessionRefreshService>().rebindPendingTabsInBackground(provider, requestsByPath)
    },
  private val resolveProvider: (AgentChatEditorTabActionContext) -> AgentSessionProvider? = ::resolvePendingRebindProvider,
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val provider = resolveProvider(context) ?: return
    if (context.tabKey.isBlank()) {
      return
    }
    val target = resolveTarget(context, provider) ?: return
    val request = AgentChatPendingTabRebindRequest(
      pendingTabKey = context.tabKey,
      pendingThreadIdentity = context.threadIdentity,
      target = target,
    )
    rebindPendingTab(provider, mapOf(context.path to listOf(request)))
  }

  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContext(e)
    val provider = context?.let(resolveProvider)
    if (context == null || provider == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = resolveTarget(context, provider) != null
  }
}

private fun resolvePendingRebindProvider(context: AgentChatEditorTabActionContext): AgentSessionProvider? {
  val threadCoordinates = context.threadCoordinates ?: return null
  if (!threadCoordinates.isPending) {
    return null
  }

  val provider = threadCoordinates.provider
  val descriptor = AgentSessionProviders.find(provider) ?: return null
  if (!descriptor.supportsPendingEditorTabRebind) {
    return null
  }
  if (AgentWorkbenchActionIds.Sessions.BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB !in descriptor.editorTabActionIds) {
    return null
  }
  return provider
}
