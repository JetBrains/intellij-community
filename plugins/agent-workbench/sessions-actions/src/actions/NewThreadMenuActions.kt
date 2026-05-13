// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
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
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.action.TerminalAgentsAvailabilityService
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode

fun createNewThreadViaService(
  path: String,
  provider: AgentSessionProvider,
  mode: AgentSessionLaunchMode,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  service<AgentSessionLaunchService>().createNewSession(path, provider, mode, entryPoint, currentProject)
}

fun buildNewThreadMenuModel(
  bridges: List<AgentSessionProviderDescriptor>,
  project: Project,
): AgentSessionProviderMenuModel {
  return buildAgentSessionProviderMenuModel(bridges, providerAvailabilityFromTerminalCache(bridges, project))
}

fun buildNewThreadActionModel(
  bridges: List<AgentSessionProviderDescriptor>,
  lastUsedProvider: AgentSessionProvider?,
  lastUsedLaunchMode: AgentSessionLaunchMode? = null,
  project: Project,
): AgentSessionProviderActionModel {
  return buildAgentSessionProviderActionModel(
    bridges = bridges,
    lastUsedProvider = lastUsedProvider,
    lastUsedLaunchMode = lastUsedLaunchMode,
    availabilityByProvider = providerAvailabilityFromTerminalCache(bridges, project),
  )
}

/**
 * Reads the cached snapshot from `TerminalAgentsAvailabilityService.getAvailableAgents()` and maps it
 * onto each bridge's [AgentSessionProviderDescriptor.terminalAgentKey]. Synchronous surfaces (BGT action
 * `update()`) use this; the cache is prewarmed at terminal tool window initialization, refreshed when the
 * terminal agents popup is shown, and after a failed launch (see `TerminalAgentsAvailabilityService`).
 *
 * The cache is checked first; if it has no entry for a provider's [AgentSessionProviderDescriptor.terminalAgentKey] (e.g., before the
 * project-level prewarm has populated it, or when the terminal plugin's registry flag is disabled), the
 * helper falls back to `runBlocking { bridge.isCliAvailable() }` so the menu doesn't render every
 * provider as "CLI not found" simply because the cache hasn't filled in yet.
 */
fun providerAvailabilityFromTerminalCache(
  bridges: List<AgentSessionProviderDescriptor>,
  project: Project,
): Map<AgentSessionProvider, Boolean> {
  val cached = TerminalAgentsAvailabilityService.getInstance(project).getAvailableAgents()
  val cachedKeys = cached.mapTo(HashSet()) { it.agentKey }
  val runnable = cached.filter { it.mode == TerminalAgentMode.RUN }.mapTo(HashSet()) { it.agentKey }
  return bridges.associate { bridge ->
    val agentKey = bridge.terminalAgentKey?.let(TerminalAgent::AgentKey)
    val available = when (agentKey) {
        null -> runBlockingCancellable { bridge.isCliAvailable() }
        in cachedKeys -> agentKey in runnable
        else -> runBlockingCancellable { bridge.isCliAvailable() }
    }
    bridge.provider to available
  }
}

internal fun quickStartActionText(item: AgentSessionProviderMenuItem): @Nls String {
  return AgentSessionsBundle.message("action.AgentWorkbenchSessions.NewThreadQuick.text", quickStartLabel(item))
}

internal fun quickStartActionDescription(item: AgentSessionProviderMenuItem): @Nls String {
  return AgentSessionsBundle.message("action.AgentWorkbenchSessions.NewThreadQuick.description", quickStartLabel(item))
}

private fun quickStartLabel(item: AgentSessionProviderMenuItem): @Nls String {
  return AgentSessionsBundle.message(item.labelKey)
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
