// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettings
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class AgentChatSettingsContributorTest {
  private val settings: AgentWorkbenchSettings
    get() = AgentWorkbenchSettings.getInstance()

  @BeforeEach
  fun setUp() {
    settings.loadState(AgentWorkbenchSettings.SettingsState())
  }

  @AfterEach
  fun tearDown() {
    settings.loadState(AgentWorkbenchSettings.SettingsState())
  }

  @Test
  fun contributesColorTabsBySourceProjectCheckbox() {
    val setting = AgentChatSettingsContributor().checkboxSettings().single()

    assertThat(setting.text).isEqualTo(AgentChatBundle.message("settings.agent.workbench.chat.color.tabs.by.source.project"))
    assertThat(setting.description).isEqualTo(AgentChatBundle.message("settings.agent.workbench.chat.color.tabs.by.source.project.description"))
    assertThat(setting.isSelected()).isTrue()

    setting.setSelected(false)

    assertThat(settings.colorTabsBySourceProject).isFalse()
  }
}
