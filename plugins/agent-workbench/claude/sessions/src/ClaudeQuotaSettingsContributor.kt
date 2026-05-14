// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsContributor
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchCheckboxSetting

internal class ClaudeQuotaSettingsContributor : AgentWorkbenchSettingsContributor {
  override fun checkboxSettings(): List<AgentWorkbenchCheckboxSetting> {
    return listOf(
      AgentWorkbenchCheckboxSetting(
        text = ClaudeSessionsBundle.message("settings.agent.workbench.claude.quota.status.bar.widget"),
        description = ClaudeSessionsBundle.message("settings.agent.workbench.claude.quota.status.bar.widget.description"),
        isSelected = ClaudeQuotaStatusBarWidgetSettings::isEnabled,
        setSelected = ClaudeQuotaStatusBarWidgetSettings::setEnabled,
      )
    )
  }
}
