// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-task-cost-profiles.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildBuiltInLaunchProfiles
import com.intellij.agent.workbench.sessions.core.providers.effectiveLaunchProfiles
import com.intellij.agent.workbench.sessions.core.providers.launchProfileMatchesBuiltIn
import com.intellij.agent.workbench.sessions.core.providers.normalizedUserLaunchProfile
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.agent.workbench.sessions.state.AgentSessionLaunchProfileStateService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresEdt

internal class AgentPromptLaunchProfileManager(
  private val project: Project,
  private val providersProvider: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
) {
  private val launchProfileStateService = service<AgentSessionLaunchProfileStateService>()
  private val modelCatalogService = project.service<AgentPromptGenerationModelCatalogService>()
  private val sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptProviderSelector::class.java.classLoader)

  @RequiresEdt
  fun openOrFocus() {
    ApplicationManager.getApplication().service<AgentPromptLaunchProfileEditorWindowService>().openOrFocus(createRequest())
  }

  private fun createRequest(): AgentPromptLaunchProfileEditorRequest {
    val providers = enabledPromptProviders()
    val providerEntries = providerEntries(providers)
    val builtInProfiles = builtInProfiles(providers)
    return AgentPromptLaunchProfileEditorRequest(
      project = project,
      profiles = effectiveLaunchProfiles(builtInProfiles, launchProfileStateService.getUserLaunchProfiles()),
      activeProfileId = launchProfileStateService.getActiveLaunchProfileId(),
      defaultProfileId = launchProfileStateService.getActiveLaunchProfileId(),
      builtInProfiles = builtInProfiles,
      providerEntries = providerEntries,
      modelCatalogProvider = ::loadedModelCatalog,
      modelCatalogStateProvider = modelCatalogService::catalogState,
      requestModelCatalogRefresh = { providerId, onStateChanged ->
        val provider = providerEntries.firstOrNull { entry -> entry.bridge.provider.value == providerId }
        if (provider != null) {
          modelCatalogService.requestStateRefresh(provider.bridge, project, onStateChanged)
        }
      },
      newUserProfileId = ::newUserProfileId,
      onCreateProfile = ::saveNewProfile,
      onUpdateProfile = { profile -> saveProfile(profile, builtInProfiles) },
      onDeleteProfile = { profile -> deleteProfile(profile, builtInProfiles) },
      onSetDefaultProfile = ::setDefaultProfile,
    )
  }

  private fun enabledPromptProviders(): List<AgentSessionProviderDescriptor> {
    return service<AgentSessionProviderSettingsService>().enabledProviders(providersProvider().filter { provider -> provider.supportsPromptLaunch })
  }

  private fun providerEntries(providers: List<AgentSessionProviderDescriptor>): List<ProviderEntry> {
    val availabilityService = project.service<AgentSessionProviderAvailabilityService>()
    availabilityService.requestRefresh(providers)
    val availabilityByProvider = availabilityService.availabilitySnapshot(providers)
    return providers.map { provider ->
      ProviderEntry(
        bridge = provider,
        displayName = sessionsMessageResolver.resolve(provider.displayNameKey, provider) ?: provider.displayNameFallback,
        isCliAvailable = availabilityByProvider[provider.provider] == true,
        icon = provider.icon,
      )
    }
  }

  private fun builtInProfiles(providers: List<AgentSessionProviderDescriptor>): List<AgentPromptLaunchProfile> {
    val availabilityService = project.service<AgentSessionProviderAvailabilityService>()
    val menuModel = buildAgentSessionProviderMenuModel(providers, availabilityService.availabilitySnapshot(providers))
    return buildBuiltInLaunchProfiles(menuModel, ::launchProfileLabel)
  }

  private fun launchProfileLabel(item: AgentSessionProviderMenuItem): @NlsSafe String {
    return sessionsMessageResolver.resolve(item.labelKey, item.bridge) ?: item.bridge.displayNameFallback
  }

  private fun loadedModelCatalog(providerId: String): List<AgentPromptGenerationModel>? {
    return modelCatalogService.catalogState(providerId)?.modelsOrNull()
  }

  private fun saveNewProfile(profile: AgentPromptLaunchProfile) {
    val profiles = launchProfileStateService.getUserLaunchProfiles() + normalizedUserLaunchProfile(profile)
    launchProfileStateService.setLaunchProfiles(profiles, launchProfileStateService.getActiveLaunchProfileId())
  }

  private fun saveProfile(profile: AgentPromptLaunchProfile, builtInProfiles: List<AgentPromptLaunchProfile>): Boolean {
    val currentProfiles = launchProfileStateService.getUserLaunchProfiles()
    val builtInProfile = builtInProfiles.firstOrNull { item -> item.id == profile.id }
    val updatedProfiles = when {
      builtInProfile != null && launchProfileMatchesBuiltIn(profile, builtInProfile) -> {
        currentProfiles.filterNot { item -> item.id == profile.id }
      }
      builtInProfile != null -> upsertProfile(currentProfiles, normalizedUserLaunchProfile(profile))
      profile.kind == AgentPromptLaunchProfileKind.USER && currentProfiles.any { item -> item.id == profile.id } -> {
        upsertProfile(currentProfiles, normalizedUserLaunchProfile(profile))
      }
      else -> return false
    }
    launchProfileStateService.setLaunchProfiles(updatedProfiles, launchProfileStateService.getActiveLaunchProfileId())
    return true
  }

  private fun deleteProfile(profile: AgentPromptLaunchProfile, builtInProfiles: List<AgentPromptLaunchProfile>): Boolean {
    val currentProfiles = launchProfileStateService.getUserLaunchProfiles()
    if (currentProfiles.none { item -> item.id == profile.id }) return false

    val resetsBuiltInProfile = builtInProfiles.any { item -> item.id == profile.id }
    val message = if (resetsBuiltInProfile) {
      AgentPromptBundle.message("popup.profile.reset.message", profile.name)
    }
    else {
      AgentPromptBundle.message("popup.profile.delete.message", profile.name)
    }
    val title = if (resetsBuiltInProfile) AgentPromptBundle.message("popup.profile.reset.title")
    else AgentPromptBundle.message("popup.profile.delete.title")
    if (Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon()) != Messages.YES) {
      return false
    }

    val activeProfileId = launchProfileStateService.getActiveLaunchProfileId()
      .takeUnless { profileId -> !resetsBuiltInProfile && profileId == profile.id }
    launchProfileStateService.setLaunchProfiles(
      profiles = currentProfiles.filterNot { item -> item.id == profile.id },
      activeProfileId = activeProfileId,
    )
    return true
  }

  private fun setDefaultProfile(profile: AgentPromptLaunchProfile) {
    launchProfileStateService.setLaunchProfiles(launchProfileStateService.getUserLaunchProfiles(), profile.id)
  }

  private fun upsertProfile(
    profiles: List<AgentPromptLaunchProfile>,
    profile: AgentPromptLaunchProfile,
  ): List<AgentPromptLaunchProfile> {
    return if (profiles.any { item -> item.id == profile.id }) {
      profiles.map { item -> if (item.id == profile.id) profile else item }
    }
    else {
      profiles + profile
    }
  }

  private fun newUserProfileId(): String {
    val existingProfiles = launchProfileStateService.getUserLaunchProfiles()
    val base = "user:${System.currentTimeMillis()}"
    if (existingProfiles.none { profile -> profile.id == base }) return base
    var suffix = 2
    while (existingProfiles.any { profile -> profile.id == "$base:$suffix" }) {
      suffix++
    }
    return "$base:$suffix"
  }
}
