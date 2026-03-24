// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

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
@State(name = "ClaudeQuotaHintState", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
internal class ClaudeQuotaHintStateService : SerializablePersistentStateComponent<ClaudeQuotaHintStateService.State>(State()) {
  private val _eligibleFlow = MutableStateFlow(state.eligible)
  val eligibleFlow: StateFlow<Boolean> = _eligibleFlow.asStateFlow()

  private val _acknowledgedFlow = MutableStateFlow(state.acknowledged)
  val acknowledgedFlow: StateFlow<Boolean> = _acknowledgedFlow.asStateFlow()

  fun markEligible() {
    if (state.eligible) return
    updateState { current -> current.copy(eligible = true) }
    _eligibleFlow.value = true
  }

  fun acknowledge() {
    if (state.acknowledged) return
    updateState { current -> current.copy(acknowledged = true) }
    _acknowledgedFlow.value = true
  }

  override fun loadState(state: State) {
    super.loadState(state)
    _eligibleFlow.value = state.eligible
    _acknowledgedFlow.value = state.acknowledged
  }

  @Serializable
  internal data class State(
    @JvmField val eligible: Boolean = false,
    @JvmField val acknowledged: Boolean = false,
  )
}

