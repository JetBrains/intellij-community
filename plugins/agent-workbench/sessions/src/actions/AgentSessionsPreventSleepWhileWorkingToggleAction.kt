// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/agent-sessions-sleep-prevention.spec.md

import com.intellij.agent.workbench.sessions.sleep.AgentSleepPreventionSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal class AgentSessionsPreventSleepWhileWorkingToggleAction : DumbAwareToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    return AgentSleepPreventionSettings.isEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    AgentSleepPreventionSettings.setEnabled(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
