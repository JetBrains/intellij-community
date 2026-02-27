// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.frame

// @spec community/plugins/agent-workbench/spec/agent-dedicated-frame.spec.md

import com.intellij.openapi.options.advanced.AdvancedSettings

internal const val OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID: String = "agent.workbench.chat.open.in.dedicated.frame"

internal object AgentChatOpenModeSettings {
  fun openInDedicatedFrame(): Boolean {
    return AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)
  }

  fun setOpenInDedicatedFrame(enabled: Boolean) {
    AdvancedSettings.setBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID, enabled)
  }
}
