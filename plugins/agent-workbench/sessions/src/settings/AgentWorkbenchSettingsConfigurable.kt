// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.settings.AGENT_WORKBENCH_CHAT_SETTINGS_COMPONENT_ID
import com.intellij.agent.workbench.sessions.core.settings.AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchCheckboxSetting
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettings
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsComponent
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsContributors
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.sleep.AgentSleepPreventionSettings
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.AbstractButton

const val AGENT_WORKBENCH_SETTINGS_CONFIGURABLE_ID: String = "com.intellij.agent.workbench.settings"
const val AGENT_WORKBENCH_PROVIDERS_SETTINGS_CONFIGURABLE_ID: String = "com.intellij.agent.workbench.settings.providers"

internal class AgentWorkbenchSettingsConfigurable : BoundSearchableConfigurable(
  displayName = AgentSessionsBundle.message("settings.agent.workbench.name"),
  helpTopic = "settings.agent.workbench",
  _id = ID,
) {
  override fun createPanel(): DialogPanel {
    val components = settingsComponents()
    return panel {
      for (component in components.filterNot(AgentWorkbenchSettingsComponent::isStatusBarWidgetsComponent)) {
        renderSettingsComponent(component)
      }

      group(AgentSessionsBundle.message("settings.agent.workbench.general.group")) {
        renderCheckboxSetting(mainToolbarActivitySetting())
        renderCheckboxSetting(sleepPreventionSetting())
        renderCheckboxSettings(AgentWorkbenchSettingsContributors.checkboxSettings())
      }

      components.firstOrNull(AgentWorkbenchSettingsComponent::isStatusBarWidgetsComponent)?.let { component ->
        renderSettingsComponent(component)
      }
    }
  }

  companion object {
    const val ID: String = AGENT_WORKBENCH_SETTINGS_CONFIGURABLE_ID
  }
}

internal class AgentWorkbenchProvidersSettingsConfigurable : BoundSearchableConfigurable(
  displayName = AgentSessionsBundle.message("settings.agent.workbench.providers.name"),
  helpTopic = "settings.agent.workbench.providers",
  _id = ID,
) {
  override fun createPanel(): DialogPanel {
    val providerSettings = service<AgentSessionProviderSettingsService>()
    return panel {
      for (provider in AgentSessionProviders.allProviders()) {
        lateinit var providerEnabledCheckbox: Cell<AbstractButton>
        row {
          providerEnabledCheckbox = checkBox(AgentSessionsBundle.message(provider.displayNameKey))
            .bindSelected(
              { providerSettings.isProviderEnabled(provider.provider) },
              { enabled -> providerSettings.setProviderEnabled(provider.provider, enabled) },
            )
        }
        renderProviderCheckboxSettings(provider.providerSettings, providerEnabledCheckbox)
      }
    }
  }

  companion object {
    const val ID: String = AGENT_WORKBENCH_PROVIDERS_SETTINGS_CONFIGURABLE_ID
  }
}

private fun settingsComponents(): List<AgentWorkbenchSettingsComponent> {
  return mergeSettingsComponents(listOf(chatSettingsComponent()) + AgentWorkbenchSettingsContributors.components())
}

private fun AgentWorkbenchSettingsComponent.isStatusBarWidgetsComponent(): Boolean {
  return id == AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID
}

private fun mergeSettingsComponents(components: List<AgentWorkbenchSettingsComponent>): List<AgentWorkbenchSettingsComponent> {
  val merged = LinkedHashMap<String, AgentWorkbenchSettingsComponent>()
  for (component in components) {
    val previous = merged[component.id]
    if (previous == null) {
      merged[component.id] = component
    }
    else {
      merged[component.id] = previous.copy(checkboxSettings = previous.checkboxSettings + component.checkboxSettings)
    }
  }
  return merged.values.toList()
}

private fun chatSettingsComponent(): AgentWorkbenchSettingsComponent {
  return AgentWorkbenchSettingsComponent(
    id = AGENT_WORKBENCH_CHAT_SETTINGS_COMPONENT_ID,
    displayName = AgentSessionsBundle.message("settings.agent.workbench.chat.group"),
    checkboxSettings = listOf(
      AgentWorkbenchCheckboxSetting(
        text = AgentSessionsBundle.message("advanced.setting.agent.workbench.chat.open.in.dedicated.frame"),
        description = AgentSessionsBundle.message("advanced.setting.agent.workbench.chat.open.in.dedicated.frame.description"),
        isSelected = AgentChatOpenModeSettings::openInDedicatedFrame,
        setSelected = AgentChatOpenModeSettings::setOpenInDedicatedFrame,
      )
    ),
  )
}

private fun mainToolbarActivitySetting(): AgentWorkbenchCheckboxSetting {
  return AgentWorkbenchCheckboxSetting(
    text = AgentSessionsBundle.message("settings.agent.workbench.show.activity.in.main.toolbar"),
    description = AgentSessionsBundle.message("settings.agent.workbench.show.activity.in.main.toolbar.description"),
    isSelected = { AgentWorkbenchSettings.getInstance().showAgentActivityInMainToolbar },
    setSelected = { enabled ->
      AgentWorkbenchSettings.getInstance().setShowAgentActivityInMainToolbar(enabled)
      ActivityTracker.getInstance().inc()
    },
  )
}

private fun sleepPreventionSetting(): AgentWorkbenchCheckboxSetting {
  return AgentWorkbenchCheckboxSetting(
    text = AgentSessionsBundle.message("advanced.setting.agent.workbench.prevent.system.sleep.while.working"),
    description = AgentSessionsBundle.message("advanced.setting.agent.workbench.prevent.system.sleep.while.working.description"),
    isSelected = AgentSleepPreventionSettings::isEnabled,
    setSelected = AgentSleepPreventionSettings::setEnabled,
  )
}

private fun Panel.renderSettingsComponent(component: AgentWorkbenchSettingsComponent) {
  group(component.displayName) {
    renderCheckboxSettings(component.checkboxSettings)
  }
}

private fun Panel.renderCheckboxSettings(settings: List<AgentWorkbenchCheckboxSetting>) {
  for (setting in settings) {
    renderCheckboxSetting(setting)
  }
}

private fun Panel.renderProviderCheckboxSettings(
  settings: List<AgentWorkbenchCheckboxSetting>,
  providerEnabledCheckbox: Cell<AbstractButton>,
) {
  if (settings.isEmpty()) {
    return
  }
  indent {
    renderCheckboxSettings(settings)
  }.enabledIf(providerEnabledCheckbox.selected)
}

private fun Panel.renderCheckboxSetting(setting: AgentWorkbenchCheckboxSetting) {
  val row = row {
    checkBox(setting.text).bindSelected(setting.isSelected, setting.setSelected)
  }
  setting.description?.let { row.rowComment(it) }
}
