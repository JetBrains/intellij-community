// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.state.AgentSessionsTreeUiStateService
import com.intellij.agent.workbench.sessions.ui.providerIcon
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

internal class AgentSessionsEditorTabNewThreadQuickAction @JvmOverloads constructor(
  private val allBridges: () -> List<AgentSessionProviderBridge> = AgentSessionProviderBridges::allBridges,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project) -> Unit = ::createNewThreadViaService,
  private val lastUsedProvider: () -> AgentSessionProvider? = { service<AgentSessionsTreeUiStateService>().getLastUsedProvider() },
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {
  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContext(e)
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider())
    val isVisible = context != null && actionModel.quickStartItem != null
    e.presentation.isEnabledAndVisible = isVisible
    e.presentation.icon = actionModel.quickStartItem?.let { providerIcon(it.bridge.provider) } ?: templatePresentation.icon
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val actionModel = buildNewThreadActionModel(allBridges(), lastUsedProvider())
    launchQuickStartThread(context.path, context.project, actionModel.quickStartItem, createNewSession)
  }
}

internal class AgentSessionsEditorTabNewThreadPopupGroup @JvmOverloads constructor(
  private val resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? =
    ::resolveAgentChatEditorTabActionContext,
  private val allBridges: () -> List<AgentSessionProviderBridge> = AgentSessionProviderBridges::allBridges,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project) -> Unit = ::createNewThreadViaService,
) : ActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    val menuModel = buildNewThreadMenuModel(allBridges())
    if (context == null || !menuModel.hasEntries()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    e.presentation.isPopupGroup = true
    e.presentation.isPerformGroup = false
    e.presentation.icon = templatePresentation.icon
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val context = e?.let(resolveContext) ?: return emptyArray()
    return buildNewThreadMenuActions(
      path = context.path,
      project = context.project,
      menuModel = buildNewThreadMenuModel(allBridges()),
      createNewSession = createNewSession,
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
