// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.frame

// @spec community/plugins/agent-workbench/spec/frame/agent-dedicated-frame.spec.md

import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettings
import com.intellij.openapi.options.advanced.AdvancedSettings

internal const val OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID: String = "agent.workbench.chat.open.in.dedicated.frame"

object AgentChatOpenModeSettings {
  fun openInDedicatedFrame(): Boolean {
    val settings = AgentWorkbenchSettings.getInstance()
    val persistedValue = settings.openInDedicatedFrameOverride
    if (persistedValue != null) {
      resetLegacyAdvancedSettingIfNeeded()
      return persistedValue
    }

    val migratedValue = AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)
    val effectiveValue = settings.migrateOpenInDedicatedFrame(migratedValue)
    resetLegacyAdvancedSettingIfNeeded()
    return effectiveValue
  }

  fun setOpenInDedicatedFrame(enabled: Boolean) {
    AgentWorkbenchSettings.getInstance().setOpenInDedicatedFrame(enabled)
    resetLegacyAdvancedSettingIfNeeded()
  }

  private fun resetLegacyAdvancedSettingIfNeeded() {
    val defaultValue = AdvancedSettings.getDefaultBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)
    if (AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID) != defaultValue) {
      AdvancedSettings.setBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID, defaultValue)
    }
  }
}
