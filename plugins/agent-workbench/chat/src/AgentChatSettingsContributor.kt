// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.settings.AgentWorkbenchCheckboxSetting
import com.intellij.agent.workbench.settings.AGENT_WORKBENCH_CHAT_SETTINGS_COMPONENT_ID
import com.intellij.agent.workbench.settings.AgentWorkbenchSettings
import com.intellij.agent.workbench.settings.AgentWorkbenchSettingsComponent
import com.intellij.agent.workbench.settings.AgentWorkbenchSettingsContributor

internal class AgentChatSettingsContributor : AgentWorkbenchSettingsContributor {
  override fun components(): List<AgentWorkbenchSettingsComponent> {
    return listOf(
      AgentWorkbenchSettingsComponent(
        id = AGENT_WORKBENCH_CHAT_SETTINGS_COMPONENT_ID,
        displayName = AgentChatBundle.message("settings.agent.workbench.chat.group"),
        checkboxSettings = listOf(
          AgentWorkbenchCheckboxSetting(
            text = AgentChatBundle.message("settings.agent.workbench.chat.color.tabs.by.source.project"),
            description = AgentChatBundle.message("settings.agent.workbench.chat.color.tabs.by.source.project.description"),
            isSelected = { AgentWorkbenchSettings.getInstance().colorTabsBySourceProject },
            setSelected = { enabled -> AgentWorkbenchSettings.getInstance().setColorTabsBySourceProject(enabled) },
          )
        ),
      )
    )
  }
}
