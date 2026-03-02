// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/agent-dedicated-frame.spec.md

import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal class AgentSessionsDedicatedFrameToggleAction : DumbAwareToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return AgentChatOpenModeSettings.openInDedicatedFrame()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    AgentChatOpenModeSettings.setOpenInDedicatedFrame(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
