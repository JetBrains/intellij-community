// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "AgentSessionLaunchProfileStateV2", storages = [Storage("agentWorkbenchLaunchProfilesV2.xml")])
class AgentSessionLaunchProfileStateService
  : SerializablePersistentStateComponent<AgentSessionLaunchProfileStateService.LaunchProfileState>(LaunchProfileState()) {

  private val _launchProfileStateFlow = MutableStateFlow(state)
  val launchProfileStateFlow: StateFlow<LaunchProfileState> = _launchProfileStateFlow.asStateFlow()

  fun getUserLaunchProfiles(): List<AgentPromptLaunchProfile> {
    return state.launchProfiles
  }

  fun getActiveLaunchProfileId(): String? {
    return state.activeLaunchProfileId
  }

  fun getActiveVcsMergeLaunchProfileId(): String? {
    return state.activeVcsMergeLaunchProfileId
  }

  fun setLaunchProfiles(profiles: List<AgentPromptLaunchProfile>, activeProfileId: String?) {
    updateState { current ->
      current.copy(
        launchProfiles = profiles,
        activeLaunchProfileId = activeProfileId,
      )
    }
    _launchProfileStateFlow.value = state
  }

  fun setActiveVcsMergeLaunchProfileId(profileId: String?) {
    updateState { current -> current.copy(activeVcsMergeLaunchProfileId = profileId) }
    _launchProfileStateFlow.value = state
  }

  override fun loadState(state: LaunchProfileState) {
    super.loadState(state)
    _launchProfileStateFlow.value = state
  }

  @Serializable
  data class LaunchProfileState(
    @JvmField val launchProfiles: List<AgentPromptLaunchProfile> = emptyList(),
    @JvmField val activeLaunchProfileId: String? = null,
    @JvmField val activeVcsMergeLaunchProfileId: String? = null,
  )
}
