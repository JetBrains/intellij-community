// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-cost-and-jbcentral-quota.spec.md

import com.intellij.agent.workbench.settings.AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID
import com.intellij.agent.workbench.settings.AgentWorkbenchCheckboxSetting
import com.intellij.agent.workbench.settings.AgentWorkbenchSettingsComponent
import com.intellij.agent.workbench.settings.AgentWorkbenchSettingsContributor

internal class ClaudeQuotaSettingsContributor : AgentWorkbenchSettingsContributor {
  override fun components(): List<AgentWorkbenchSettingsComponent> {
    return listOf(
      AgentWorkbenchSettingsComponent(
        id = AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID,
        displayName = ClaudeSessionsBundle.message("settings.agent.workbench.status.bar.widgets.group"),
        checkboxSettings = listOf(
          AgentWorkbenchCheckboxSetting(
            text = ClaudeSessionsBundle.message("settings.agent.workbench.claude.quota.status.bar.widget"),
            description = ClaudeSessionsBundle.message("settings.agent.workbench.claude.quota.status.bar.widget.description"),
            isSelected = ClaudeQuotaStatusBarWidgetSettings::isEnabled,
            setSelected = ClaudeQuotaStatusBarWidgetSettings::setEnabled,
          )
        ),
      )
    )
  }
}
