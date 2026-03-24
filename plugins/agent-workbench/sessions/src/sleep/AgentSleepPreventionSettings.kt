// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.sleep

// @spec community/plugins/agent-workbench/spec/agent-sessions-sleep-prevention.spec.md

import com.intellij.openapi.options.advanced.AdvancedSettings

internal const val PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID: String = "agent.workbench.prevent.system.sleep.while.working"

internal object AgentSleepPreventionSettings {
  fun isEnabled(): Boolean {
    return AdvancedSettings.getBoolean(PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID)
  }

  fun setEnabled(enabled: Boolean) {
    AdvancedSettings.setBoolean(PREVENT_SYSTEM_SLEEP_WHILE_WORKING_SETTING_ID, enabled)
  }
}
