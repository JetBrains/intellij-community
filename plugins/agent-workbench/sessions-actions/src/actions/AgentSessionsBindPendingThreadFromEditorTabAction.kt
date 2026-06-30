// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.thread.view.AgentThreadViewEditorTabActionContext
import com.intellij.agent.workbench.thread.view.AgentThreadViewPendingTabRebindRequest
import com.intellij.agent.workbench.thread.view.AgentThreadViewTabRebindTarget
import com.intellij.agent.workbench.thread.view.resolveAgentThreadViewEditorTabActionContext
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class AgentSessionsBindPendingThreadFromEditorTabAction @JvmOverloads constructor(
  private val resolveTarget: (AgentThreadViewEditorTabActionContext, AgentSessionProvider) -> AgentThreadViewTabRebindTarget? = { context, provider ->
    service<AgentSessionReadService>().resolvePendingThreadRebindTarget(context, provider)
  },
  private val rebindPendingTab: (AgentSessionProvider, Map<String, List<AgentThreadViewPendingTabRebindRequest>>) -> Unit =
    { provider, requestsByPath ->
      service<AgentSessionRefreshService>().rebindPendingTabsInBackground(provider, requestsByPath)
    },
  private val resolveProvider: (AgentThreadViewEditorTabActionContext) -> AgentSessionProvider? = ::resolvePendingRebindProvider,
  resolveContext: (AnActionEvent) -> AgentThreadViewEditorTabActionContext? = ::resolveAgentThreadViewEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val provider = resolveProvider(context) ?: return
    if (context.tabKey.isBlank()) {
      return
    }
    val target = resolveTarget(context, provider) ?: return
    val request = AgentThreadViewPendingTabRebindRequest(
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

private fun resolvePendingRebindProvider(context: AgentThreadViewEditorTabActionContext): AgentSessionProvider? {
  val threadCoordinates = context.threadCoordinates ?: return null
  if (!threadCoordinates.isPending || !threadCoordinates.participatesInPendingThreadLifecycle) {
    return null
  }

  val provider = threadCoordinates.provider
  val descriptor = AgentSessionProviders.find(provider) ?: return null
  if (!descriptor.supportsPendingEditorTabRebind) {
    return null
  }
  return provider
}
