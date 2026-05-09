// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.sleep.AgentSleepPreventionSettings
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

internal class AgentWorkbenchSettingsConfigurable : BoundSearchableConfigurable(
  displayName = AgentSessionsBundle.message("settings.agent.workbench.name"),
  helpTopic = "settings.agent.workbench",
  _id = ID,
) {
  override fun createPanel(): DialogPanel {
    return panel {
      group(AgentSessionsBundle.message("settings.agent.workbench.general.group")) {
        row {
          checkBox(AgentSessionsBundle.message("advanced.setting.agent.workbench.chat.open.in.dedicated.frame"))
            .bindSelected(AgentChatOpenModeSettings::openInDedicatedFrame, AgentChatOpenModeSettings::setOpenInDedicatedFrame)
        }.rowComment(AgentSessionsBundle.message("advanced.setting.agent.workbench.chat.open.in.dedicated.frame.description"))

        row {
          checkBox(AgentSessionsBundle.message("advanced.setting.agent.workbench.prevent.system.sleep.while.working"))
            .bindSelected(AgentSleepPreventionSettings::isEnabled, AgentSleepPreventionSettings::setEnabled)
        }.rowComment(AgentSessionsBundle.message("advanced.setting.agent.workbench.prevent.system.sleep.while.working.description"))
      }
    }
  }

  companion object {
    const val ID: String = "com.intellij.agent.workbench.settings"
  }
}
