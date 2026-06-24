// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.settings.AgentWorkbenchCheckboxSetting
import com.intellij.agent.workbench.settings.AgentWorkbenchSettingsContributor

private val PI_AGENT_SESSION_PROVIDER: AgentSessionProvider = AgentSessionProvider.from("pi")

internal class PiProviderSettingsContributor : AgentWorkbenchSettingsContributor {
  override fun providerCheckboxSettings(provider: AgentSessionProvider): List<AgentWorkbenchCheckboxSetting> {
    if (provider != PI_AGENT_SESSION_PROVIDER) {
      return emptyList()
    }
    return listOf(
      AgentWorkbenchCheckboxSetting(
        text = AgentSessionsBundle.message("settings.agent.workbench.provider.pi.omlx.models"),
        description = AgentSessionsBundle.message("settings.agent.workbench.provider.pi.omlx.models.description"),
        isSelected = PiOmlxSupportSettings::isEnabled,
        setSelected = PiOmlxSupportSettings::setEnabled,
      ),
      AgentWorkbenchCheckboxSetting(
        text = AgentSessionsBundle.message("settings.agent.workbench.provider.pi.jbcentral.models"),
        description = AgentSessionsBundle.message("settings.agent.workbench.provider.pi.jbcentral.models.description"),
        isSelected = PiJbCentralSupportSettings::isEnabled,
        setSelected = PiJbCentralSupportSettings::setEnabled,
      )
    )
  }
}
