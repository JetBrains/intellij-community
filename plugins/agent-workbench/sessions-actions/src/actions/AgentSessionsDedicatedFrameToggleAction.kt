// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/agent-dedicated-frame.spec.md

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAwareToggleAction

internal class AgentSessionsDedicatedFrameToggleAction : DumbAwareToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    AdvancedSettings.setBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID, state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private const val OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID = "agent.workbench.chat.open.in.dedicated.frame"
