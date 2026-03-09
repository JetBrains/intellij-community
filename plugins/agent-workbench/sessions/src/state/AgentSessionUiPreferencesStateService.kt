// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
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
internal class AgentSessionUiPreferencesStateService
  : SerializablePersistentStateComponent<AgentSessionUiPreferencesStateService.UiPreferencesState>(UiPreferencesState()) {

  private val _lastUsedProviderFlow = MutableStateFlow(getLastUsedProvider())
  val lastUsedProviderFlow: StateFlow<AgentSessionProvider?> = _lastUsedProviderFlow.asStateFlow()

  fun getLastUsedProvider(): AgentSessionProvider? {
    val id = state.lastUsedProvider ?: return null
    return AgentSessionProvider.fromOrNull(id)
  }

  fun setLastUsedProvider(provider: AgentSessionProvider) {
    if (state.lastUsedProvider == provider.value) {
      _lastUsedProviderFlow.value = provider
      return
    }
    updateState { current -> current.copy(lastUsedProvider = provider.value) }
    _lastUsedProviderFlow.value = provider
  }

  override fun loadState(state: UiPreferencesState) {
    super.loadState(state)
    _lastUsedProviderFlow.value = state.lastUsedProvider?.let(AgentSessionProvider::fromOrNull)
  }

  @Serializable
  internal data class UiPreferencesState(
    @JvmField val lastUsedProvider: String? = null,
  )
}
