// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.buildAgentSessionProviderMenuActions
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderActionModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderActionModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.generationSettingsForPlanMode
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.core.providers.initialMessageRequestForLaunchProfile
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

fun createNewThreadViaService(
  path: String,
  provider: AgentSessionProvider,
  mode: AgentSessionLaunchMode,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  service<AgentSessionLaunchService>().createNewSession(path, provider, mode, entryPoint, currentProject)
}

fun createNewThreadViaService(
  path: String,
  profile: AgentPromptLaunchProfile,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  val provider = AgentSessionProvider.fromOrNull(profile.providerId) ?: return
  service<AgentSessionLaunchService>().createNewSession(
    path = path,
    provider = provider,
    mode = profile.launchMode,
    entryPoint = entryPoint,
    currentProject = currentProject,
    initialMessageRequest = initialMessageRequestForLaunchProfile(profile),
    generationSettings = generationSettingsForPlanMode(
      generationSettings = profile.generationSettings,
      startInPlanMode = false,
    ),
  )
}

fun buildNewThreadMenuModel(
  bridges: List<AgentSessionProviderDescriptor>,
  project: Project,
): AgentSessionProviderMenuModel {
  val enabledBridges = service<AgentSessionProviderSettingsService>().enabledProviders(bridges)
  return buildAgentSessionProviderMenuModel(enabledBridges, providerAvailabilitySnapshot(enabledBridges, project))
}

fun buildNewThreadActionModel(
  bridges: List<AgentSessionProviderDescriptor>,
  lastUsedProvider: AgentSessionProvider?,
  lastUsedLaunchMode: AgentSessionLaunchMode? = null,
  project: Project,
): AgentSessionProviderActionModel {
  val enabledBridges = service<AgentSessionProviderSettingsService>().enabledProviders(bridges)
  return buildAgentSessionProviderActionModel(
    bridges = enabledBridges,
    lastUsedProvider = lastUsedProvider,
    lastUsedLaunchMode = lastUsedLaunchMode,
    availabilityByProvider = providerAvailabilitySnapshot(enabledBridges, project),
  )
}

/**
 * Synchronous action updates and tree renderers cannot call [AgentSessionProviderDescriptor.isCliAvailable]
 * directly. They read the project-level availability cache instead and request a background refresh when
 * the cache has not been populated yet. Prominent providers are treated as enabled so first paint does not
 * disable every provider while startup prewarm is still running; discoverable providers stay hidden until
 * a background probe resolves them as available.
 */
fun providerAvailabilitySnapshot(
  bridges: List<AgentSessionProviderDescriptor>,
  project: Project,
): Map<AgentSessionProvider, Boolean> {
  val availabilityService = project.service<AgentSessionProviderAvailabilityService>()
  availabilityService.requestRefresh(bridges)
  return availabilityService.availabilitySnapshot(bridges)
}

internal fun quickStartActionText(item: AgentSessionProviderMenuItem): @Nls String {
  return AgentSessionsBundle.message(item.bridge.quickStartActionTextKey, quickStartLabel(item))
}

internal fun quickStartActionDescription(item: AgentSessionProviderMenuItem): @Nls String {
  return AgentSessionsBundle.message(item.bridge.quickStartActionDescriptionKey, quickStartLabel(item))
}

internal fun quickStartLabel(item: AgentSessionProviderMenuItem): @Nls String {
  val labelKey = if (item.mode == AgentSessionLaunchMode.STANDARD) item.bridge.quickStartLabelKey else item.labelKey
  return AgentSessionsBundle.message(labelKey)
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
