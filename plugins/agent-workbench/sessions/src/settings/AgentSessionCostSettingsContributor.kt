// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.sessions.AgentSessionCostPresentationSettings
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchCheckboxSetting
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsContributor

internal class AgentSessionCostSettingsContributor : AgentWorkbenchSettingsContributor {
  override fun checkboxSettings(): List<AgentWorkbenchCheckboxSetting> {
    return listOf(
      AgentWorkbenchCheckboxSetting(
        text = AgentSessionsBundle.message("settings.agent.workbench.session.cost"),
        description = AgentSessionsBundle.message("settings.agent.workbench.session.cost.description"),
        isSelected = AgentSessionCostPresentationSettings::isEnabled,
        setSelected = AgentSessionCostPresentationSettings::setEnabled,
      )
    )
  }
}
