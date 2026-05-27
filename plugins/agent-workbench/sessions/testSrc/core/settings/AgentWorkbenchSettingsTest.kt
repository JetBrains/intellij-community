// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.settings

import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchSettingsTest {
  private val settings: AgentWorkbenchSettings
    get() = AgentWorkbenchSettings.getInstance()

  @BeforeEach
  fun setUp() {
    resetLegacyDedicatedFrameSetting()
    settings.loadState(AgentWorkbenchSettings.SettingsState())
  }

  @AfterEach
  fun tearDown() {
    resetLegacyDedicatedFrameSetting()
    settings.loadState(AgentWorkbenchSettings.SettingsState())
  }

  @Test
  fun openInDedicatedFrameIsEnabledByDefault() {
    assertThat(AgentChatOpenModeSettings.openInDedicatedFrame()).isTrue()
    assertThat(settings.openInDedicatedFrame).isTrue()
    assertThat(settings.openInDedicatedFrameOverride).isNull()
  }

  @Test
  fun openInDedicatedFrameStateRoundTrips() {
    settings.loadState(AgentWorkbenchSettings.SettingsState(openInDedicatedFrame = false))

    assertThat(AgentChatOpenModeSettings.openInDedicatedFrame()).isFalse()
    assertThat(settings.openInDedicatedFrameOverride).isFalse()

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)

    assertThat(settings.openInDedicatedFrame).isTrue()
    assertThat(settings.openInDedicatedFrameOverride).isNull()

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)

    assertThat(settings.openInDedicatedFrame).isFalse()
    assertThat(settings.openInDedicatedFrameOverride).isFalse()
  }

  @Test
  fun openInDedicatedFrameMigratesLegacyAdvancedSetting(@TestDisposable disposable: Disposable) {
    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
    advancedSettings.setSetting(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID, false, disposable)
    settings.loadState(AgentWorkbenchSettings.SettingsState())

    assertThat(AgentChatOpenModeSettings.openInDedicatedFrame()).isFalse()
    assertThat(settings.openInDedicatedFrame).isFalse()
    assertThat(settings.openInDedicatedFrameOverride).isFalse()
    assertThat(AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)).isTrue()
  }

  @Test
  fun openInDedicatedFrameMigrationDoesNotStoreLegacyDefault() {
    settings.loadState(AgentWorkbenchSettings.SettingsState())

    assertThat(AgentChatOpenModeSettings.openInDedicatedFrame()).isTrue()
    assertThat(settings.openInDedicatedFrame).isTrue()
    assertThat(settings.openInDedicatedFrameOverride).isNull()
  }

  @Test
  fun openInDedicatedFrameMigrationDoesNotOverwriteNewSetting(@TestDisposable disposable: Disposable) {
    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
    advancedSettings.setSetting(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID, true, disposable)
    settings.loadState(AgentWorkbenchSettings.SettingsState(openInDedicatedFrame = false))

    assertThat(AgentChatOpenModeSettings.openInDedicatedFrame()).isFalse()
    assertThat(settings.openInDedicatedFrame).isFalse()
    assertThat(settings.openInDedicatedFrameOverride).isFalse()
    assertThat(AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)).isTrue()
  }

  @Test
  fun storedDefaultValuesNormalizeToNull() {
    settings.loadState(
      AgentWorkbenchSettings.SettingsState(
        colorTabsBySourceProject = true,
        openInDedicatedFrame = true,
        showAgentActivityInMainToolbar = false,
      )
    )

    assertThat(settings.colorTabsBySourceProject).isTrue()
    assertThat(settings.colorTabsBySourceProjectOverride).isNull()
    assertThat(settings.openInDedicatedFrame).isTrue()
    assertThat(settings.openInDedicatedFrameOverride).isNull()
    assertThat(settings.showAgentActivityInMainToolbar).isFalse()
    assertThat(settings.showAgentActivityInMainToolbarOverride).isNull()
  }

  @Test
  fun colorTabsBySourceProjectIsEnabledByDefault() {
    assertThat(settings.colorTabsBySourceProject).isTrue()
    assertThat(settings.colorTabsBySourceProjectOverride).isNull()
  }

  @Test
  fun colorTabsBySourceProjectStateRoundTrips() {
    settings.loadState(AgentWorkbenchSettings.SettingsState(colorTabsBySourceProject = false))

    assertThat(settings.colorTabsBySourceProject).isFalse()
    assertThat(settings.colorTabsBySourceProjectOverride).isFalse()

    settings.setColorTabsBySourceProject(true)

    assertThat(settings.colorTabsBySourceProject).isTrue()
    assertThat(settings.colorTabsBySourceProjectOverride).isNull()

    settings.setColorTabsBySourceProject(false)

    assertThat(settings.colorTabsBySourceProject).isFalse()
    assertThat(settings.colorTabsBySourceProjectOverride).isFalse()
  }

  @Test
  fun colorTabsBySourceProjectChangeEventFiresOnlyWhenValueChanges(@TestDisposable disposable: Disposable) {
    var events = 0
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(
      AgentWorkbenchSettingsListener.TOPIC,
      object : AgentWorkbenchSettingsListener {
        override fun colorTabsBySourceProjectChanged() {
          events++
        }
      },
    )

    settings.setColorTabsBySourceProject(true)
    assertThat(events).isZero()

    settings.setColorTabsBySourceProject(false)
    assertThat(events).isEqualTo(1)

    settings.setColorTabsBySourceProject(false)
    assertThat(events).isEqualTo(1)

    settings.setColorTabsBySourceProject(true)
    assertThat(events).isEqualTo(2)

    settings.setColorTabsBySourceProject(true)
    assertThat(events).isEqualTo(2)
    assertThat(settings.colorTabsBySourceProjectOverride).isNull()
  }

  @Test
  fun showAgentActivityInMainToolbarIsDisabledByDefault() {
    assertThat(settings.showAgentActivityInMainToolbar).isFalse()
    assertThat(settings.showAgentActivityInMainToolbarOverride).isNull()
  }

  @Test
  fun showAgentActivityInMainToolbarStateRoundTrips() {
    settings.loadState(AgentWorkbenchSettings.SettingsState(showAgentActivityInMainToolbar = true))

    assertThat(settings.showAgentActivityInMainToolbar).isTrue()
    assertThat(settings.showAgentActivityInMainToolbarOverride).isTrue()

    settings.setShowAgentActivityInMainToolbar(false)

    assertThat(settings.showAgentActivityInMainToolbar).isFalse()
    assertThat(settings.showAgentActivityInMainToolbarOverride).isNull()

    settings.setShowAgentActivityInMainToolbar(true)

    assertThat(settings.showAgentActivityInMainToolbar).isTrue()
    assertThat(settings.showAgentActivityInMainToolbarOverride).isTrue()
  }

  private fun resetLegacyDedicatedFrameSetting() {
    AdvancedSettings.setBoolean(
      OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID,
      AdvancedSettings.getDefaultBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID),
    )
  }
}
