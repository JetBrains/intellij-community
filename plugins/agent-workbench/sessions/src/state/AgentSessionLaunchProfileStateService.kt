// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.serialization.Serializable

@Service(Service.Level.APP)
@State(name = "AgentSessionLaunchProfileStateV2", storages = [Storage("agentWorkbenchLaunchProfilesV2.xml")])
class AgentSessionLaunchProfileStateService
  : SerializablePersistentStateComponent<AgentSessionLaunchProfileStateService.LaunchProfileState>(LaunchProfileState()) {

  fun getUserLaunchProfiles(): List<AgentPromptLaunchProfile> {
    return state.launchProfiles
  }

  fun getActiveLaunchProfileId(): String? {
    return state.activeLaunchProfileId
  }

  fun setLaunchProfiles(profiles: List<AgentPromptLaunchProfile>, activeProfileId: String?) {
    updateState { current ->
      current.copy(
        launchProfiles = profiles,
        activeLaunchProfileId = activeProfileId,
      )
    }
  }

  @Serializable
  data class LaunchProfileState(
    @JvmField val launchProfiles: List<AgentPromptLaunchProfile> = emptyList(),
    @JvmField val activeLaunchProfileId: String? = null,
  )
}
