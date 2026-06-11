// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.settings.AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchCheckboxSetting
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsComponent
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsContributor

internal class JbCentralQuotaSettingsContributor : AgentWorkbenchSettingsContributor {
  override fun components(): List<AgentWorkbenchSettingsComponent> {
    return listOf(
      AgentWorkbenchSettingsComponent(
        id = AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID,
        displayName = AgentSessionsBundle.message("settings.agent.workbench.status.bar.widgets.group"),
        checkboxSettings = listOf(
          AgentWorkbenchCheckboxSetting(
            text = AgentSessionsBundle.message("settings.agent.workbench.jbcentral.quota.status.bar.widget"),
            description = AgentSessionsBundle.message("settings.agent.workbench.jbcentral.quota.status.bar.widget.description"),
            isSelected = JbCentralQuotaStatusBarWidgetSettings::isEnabled,
            setSelected = JbCentralQuotaStatusBarWidgetSettings::setEnabled,
          )
        ),
      )
    )
  }
}
