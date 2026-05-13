// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.sessions.model.AgentSessionArchivedRangePreset
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AgentSessionThreadViewState(
  @JvmField val mode: AgentSessionThreadViewMode = AgentSessionThreadViewMode.ACTIVE,
  @JvmField val archivedRangePreset: AgentSessionArchivedRangePreset = AgentSessionArchivedRangePreset.ALL,
)

@Service(Service.Level.APP)
class AgentSessionThreadViewStateService {
  private val mutableState = MutableStateFlow(AgentSessionThreadViewState())
  val state: StateFlow<AgentSessionThreadViewState> = mutableState.asStateFlow()

  fun setMode(mode: AgentSessionThreadViewMode) {
    mutableState.value = mutableState.value.copy(mode = mode)
  }

  fun setArchivedRangePreset(preset: AgentSessionArchivedRangePreset) {
    mutableState.value = mutableState.value.copy(archivedRangePreset = preset)
  }
}
