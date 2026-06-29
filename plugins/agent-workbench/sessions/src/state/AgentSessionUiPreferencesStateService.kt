// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "AgentSessionUiPreferencesState", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
class AgentSessionUiPreferencesStateService(
  private val launchProfileStateService: AgentSessionLaunchProfileStateService = service(),
)
  : SerializablePersistentStateComponent<AgentSessionUiPreferencesStateService.UiPreferencesState>(UiPreferencesState()) {

  fun getUserLaunchProfiles(): List<AgentPromptLaunchProfile> {
    return launchProfileStateService.getUserLaunchProfiles()
  }

  fun getDefaultLaunchProfileId(): String? {
    return launchProfileStateService.getDefaultLaunchProfileId()
  }

  fun getTaskFolderAgentLaunchProfileId(): String? {
    return state.taskFolderAgentLaunchProfileId
  }

  fun setTaskFolderAgentLaunchProfileId(profileId: String?) {
    updateState { current ->
      current.copy(taskFolderAgentLaunchProfileId = profileId?.trim()?.takeIf { it.isNotEmpty() })
    }
  }

  fun getProviderPreferences(): AgentPromptLauncherBridge.ProviderPreferences {
    return AgentPromptLauncherBridge.ProviderPreferences(
      providerOptionsByProviderId = state.providerOptionsByProviderId,
      containerModeEnabled = state.containerModeEnabled,
      launchProfiles = getUserLaunchProfiles(),
      defaultLaunchProfileId = getDefaultLaunchProfileId(),
    )
  }

  fun setProviderPreferences(preferences: AgentPromptLauncherBridge.ProviderPreferences) {
    updateState { current ->
      current.copy(
        providerOptionsByProviderId = preferences.providerOptionsByProviderId,
        containerModeEnabled = preferences.containerModeEnabled,
      )
    }
    launchProfileStateService.setLaunchProfiles(
      profiles = preferences.launchProfiles,
      defaultProfileId = preferences.defaultLaunchProfileId,
    )
  }

  fun updateProviderOptionsOnLaunch(
    providerId: String,
    initialMessageRequest: AgentPromptInitialMessageRequest?,
  ) {
    val currentOptions = state.providerOptionsByProviderId
    val updatedOptions = if (initialMessageRequest != null) {
      currentOptions + (providerId to initialMessageRequest.providerOptionIds)
    }
    else {
      currentOptions
    }
    setProviderPreferences(AgentPromptLauncherBridge.ProviderPreferences(
      providerOptionsByProviderId = updatedOptions,
      containerModeEnabled = state.containerModeEnabled,
      launchProfiles = getUserLaunchProfiles(),
      defaultLaunchProfileId = getDefaultLaunchProfileId(),
    ))
  }

  @Serializable
  data class UiPreferencesState(
    @JvmField val providerOptionsByProviderId: Map<String, Set<String>> = emptyMap(),
    @JvmField val containerModeEnabled: Boolean = false,
    @JvmField val taskFolderAgentLaunchProfileId: String? = null,
  )
}
