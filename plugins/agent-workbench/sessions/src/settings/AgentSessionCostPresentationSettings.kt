// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.openapi.options.advanced.AdvancedSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val AGENT_SESSION_COST_PRESENTATION_SETTING_ID: String = "agent.workbench.sessions.thread.cost.enabled"

object AgentSessionCostPresentationSettings {
  private val _enabledFlow = MutableStateFlow(readEnabled())
  private val enabledStateFlow = _enabledFlow.asStateFlow()
  val enabledFlow: StateFlow<Boolean>
    get() {
      syncEnabledState()
      return enabledStateFlow
    }

  fun isEnabled(): Boolean {
    syncEnabledState()
    return _enabledFlow.value
  }

  fun setEnabled(enabled: Boolean) {
    if (_enabledFlow.value == enabled && readEnabled() == enabled) return
    AdvancedSettings.setBoolean(AGENT_SESSION_COST_PRESENTATION_SETTING_ID, enabled)
    _enabledFlow.value = enabled
  }

  fun syncEnabledState() {
    _enabledFlow.value = readEnabled()
  }

  private fun readEnabled(): Boolean {
    return AdvancedSettings.getBoolean(AGENT_SESSION_COST_PRESENTATION_SETTING_ID)
  }
}
