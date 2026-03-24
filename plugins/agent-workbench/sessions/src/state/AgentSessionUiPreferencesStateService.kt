// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "AgentSessionUiPreferencesState", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
class AgentSessionUiPreferencesStateService
  : SerializablePersistentStateComponent<AgentSessionUiPreferencesStateService.UiPreferencesState>(UiPreferencesState()) {

  private val _lastUsedProviderFlow = MutableStateFlow(getLastUsedProvider())
  val lastUsedProviderFlow: StateFlow<AgentSessionProvider?> = _lastUsedProviderFlow.asStateFlow()

  fun getLastUsedProvider(): AgentSessionProvider? {
    val id = state.lastUsedProvider ?: return null
    return AgentSessionProvider.fromOrNull(id)
  }

  fun getLastUsedLaunchMode(): AgentSessionLaunchMode? {
    return state.launchMode
  }

  fun setLastUsedProvider(provider: AgentSessionProvider) {
    if (state.lastUsedProvider == provider.value) {
      _lastUsedProviderFlow.value = provider
      return
    }
    updateState { current -> current.copy(lastUsedProvider = provider.value) }
    _lastUsedProviderFlow.value = provider
  }

  fun getProviderPreferences(): AgentPromptLauncherBridge.ProviderPreferences {
    return AgentPromptLauncherBridge.ProviderPreferences(
      providerId = state.lastUsedProvider,
      launchMode = state.launchMode,
      providerOptionsByProviderId = state.providerOptionsByProviderId,
    )
  }

  fun setProviderPreferences(preferences: AgentPromptLauncherBridge.ProviderPreferences) {
    updateState { current ->
      current.copy(
        lastUsedProvider = preferences.providerId ?: current.lastUsedProvider,
        launchMode = preferences.launchMode,
        providerOptionsByProviderId = preferences.providerOptionsByProviderId,
      )
    }
    val provider = preferences.providerId?.let(AgentSessionProvider::fromOrNull)
    if (provider != null) {
      _lastUsedProviderFlow.value = provider
    }
  }

  fun updateProviderPreferencesOnLaunch(
    provider: AgentSessionProvider,
    launchMode: AgentSessionLaunchMode,
    initialMessageRequest: AgentPromptInitialMessageRequest?,
  ) {
    val currentOptions = state.providerOptionsByProviderId
    val updatedOptions = if (initialMessageRequest != null) {
      currentOptions + (provider.value to initialMessageRequest.providerOptionIds)
    }
    else {
      currentOptions
    }
    setProviderPreferences(AgentPromptLauncherBridge.ProviderPreferences(
      providerId = provider.value,
      launchMode = launchMode,
      providerOptionsByProviderId = updatedOptions,
    ))
  }

  override fun loadState(state: UiPreferencesState) {
    super.loadState(state)
    _lastUsedProviderFlow.value = state.lastUsedProvider?.let(AgentSessionProvider::fromOrNull)
  }

  @Serializable
  data class UiPreferencesState(
    @JvmField val lastUsedProvider: String? = null,
    @JvmField val launchMode: AgentSessionLaunchMode? = null,
    @JvmField val providerOptionsByProviderId: Map<String, Set<String>> = emptyMap(),
  )
}
