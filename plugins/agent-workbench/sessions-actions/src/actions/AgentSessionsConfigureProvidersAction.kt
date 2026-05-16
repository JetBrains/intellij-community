// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.settings.AGENT_WORKBENCH_SETTINGS_CONFIGURABLE_ID
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsConfigureProvidersAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, AGENT_WORKBENCH_SETTINGS_CONFIGURABLE_ID)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
