// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchCheckboxSetting
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsContributor
import com.intellij.agent.workbench.sessions.jbcentral.JbCentralQuotaStatusBarWidgetSettings

internal class JbCentralQuotaSettingsContributor : AgentWorkbenchSettingsContributor {
  override fun checkboxSettings(): List<AgentWorkbenchCheckboxSetting> {
    return listOf(
      AgentWorkbenchCheckboxSetting(
        text = AgentSessionsBundle.message("settings.agent.workbench.jbcentral.quota.status.bar.widget"),
        description = AgentSessionsBundle.message("settings.agent.workbench.jbcentral.quota.status.bar.widget.description"),
        isSelected = JbCentralQuotaStatusBarWidgetSettings::isEnabled,
        setSelected = JbCentralQuotaStatusBarWidgetSettings::setEnabled,
      )
    )
  }
}
