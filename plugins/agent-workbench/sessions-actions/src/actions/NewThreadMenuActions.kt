// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.buildAgentSessionProviderMenuActions
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderActionModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderActionModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

fun createNewThreadViaService(
  path: String,
  provider: AgentSessionProvider,
  mode: AgentSessionLaunchMode,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  service<AgentSessionLaunchService>().createNewSession(path, provider, mode, entryPoint, currentProject)
}

fun buildNewThreadMenuModel(bridges: List<AgentSessionProviderDescriptor>): AgentSessionProviderMenuModel {
  return buildAgentSessionProviderMenuModel(bridges)
}

fun buildNewThreadActionModel(
    bridges: List<AgentSessionProviderDescriptor>,
    lastUsedProvider: AgentSessionProvider?,
    lastUsedLaunchMode: AgentSessionLaunchMode? = null,
): AgentSessionProviderActionModel {
  return buildAgentSessionProviderActionModel(bridges, lastUsedProvider, lastUsedLaunchMode)
}

fun launchQuickStartThread(
  path: String,
  project: Project,
  quickStartItem: AgentSessionProviderMenuItem?,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
) {
  val item = quickStartItem ?: return
  createNewSession(path, item.bridge.provider, item.mode, project, entryPoint)
}

fun buildNewThreadMenuActions(
  path: String,
  project: Project,
  menuModel: AgentSessionProviderMenuModel,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
): Array<AnAction> {
  if (!menuModel.hasEntries()) {
    return emptyArray()
  }

  return buildAgentSessionProviderMenuActions(menuModel) { item ->
    createNewSession(path, item.bridge.provider, item.mode, project, entryPoint)
  }
}
