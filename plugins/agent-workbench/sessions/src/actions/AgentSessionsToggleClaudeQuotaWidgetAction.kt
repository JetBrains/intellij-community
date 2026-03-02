// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaStatusBarWidgetSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal class AgentSessionsToggleClaudeQuotaWidgetAction : DumbAwareToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean = ClaudeQuotaStatusBarWidgetSettings.isEnabled()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    ClaudeQuotaStatusBarWidgetSettings.setEnabled(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
