// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.settings

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.Nls

const val AGENT_WORKBENCH_CHAT_SETTINGS_COMPONENT_ID: String = "agent.workbench.chat"
const val AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID: String = "agent.workbench.status.bar.widgets"
const val AGENT_WORKBENCH_SETTINGS_CONFIGURABLE_ID: String = "com.intellij.agent.workbench.settings"
const val AGENT_WORKBENCH_PROVIDERS_SETTINGS_CONFIGURABLE_ID: String = "com.intellij.agent.workbench.settings.providers"

data class AgentWorkbenchCheckboxSetting(
  @JvmField val text: @Nls String,
  @JvmField val description: @Nls String?,
  @JvmField val isSelected: () -> Boolean,
  @JvmField val setSelected: (Boolean) -> Unit,
)

data class AgentWorkbenchSettingsComponent(
  @JvmField val id: String,
  @JvmField val displayName: @Nls String,
  @JvmField val checkboxSettings: List<AgentWorkbenchCheckboxSetting>,
)

interface AgentWorkbenchSettingsContributor {
  fun checkboxSettings(): List<AgentWorkbenchCheckboxSetting> = emptyList()

  fun components(): List<AgentWorkbenchSettingsComponent> = emptyList()

  fun providerCheckboxSettings(provider: AgentSessionProvider): List<AgentWorkbenchCheckboxSetting> = emptyList()
}

object AgentWorkbenchSettingsContributors {
  val EP_NAME: ExtensionPointName<AgentWorkbenchSettingsContributor> =
    ExtensionPointName("com.intellij.agent.workbench.settingsContributor")

  fun all(): List<AgentWorkbenchSettingsContributor> = EP_NAME.extensionList

  fun checkboxSettings(): List<AgentWorkbenchCheckboxSetting> = all().flatMap { it.checkboxSettings() }

  fun components(): List<AgentWorkbenchSettingsComponent> = all().flatMap { it.components() }

  fun providerCheckboxSettings(provider: AgentSessionProvider): List<AgentWorkbenchCheckboxSetting> {
    return all().flatMap { contributor -> contributor.providerCheckboxSettings(provider) }
  }
}
