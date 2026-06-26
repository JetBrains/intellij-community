// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.settings.AgentWorkbenchSettings
import com.intellij.agent.workbench.settings.AgentWorkbenchSettingsListener
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
    settings.loadState(AgentWorkbenchSettings.SettingsState())
  }

  @AfterEach
  fun tearDown() {
    settings.loadState(AgentWorkbenchSettings.SettingsState())
  }

  @Test
  fun openInDedicatedFrameIsDisabledByDefault() {
    assertThat(AgentChatOpenModeSettings.openInDedicatedFrame()).isFalse()
    assertThat(settings.openInDedicatedFrame).isFalse()
    assertThat(settings.openInDedicatedFrameOverride).isNull()
  }

  @Test
  fun openInDedicatedFrameStateRoundTrips() {
    settings.loadState(AgentWorkbenchSettings.SettingsState(openInDedicatedFrame = true))

    assertThat(AgentChatOpenModeSettings.openInDedicatedFrame()).isTrue()
    assertThat(settings.openInDedicatedFrameOverride).isTrue()

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)

    assertThat(settings.openInDedicatedFrame).isFalse()
    assertThat(settings.openInDedicatedFrameOverride).isNull()

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)

    assertThat(settings.openInDedicatedFrame).isTrue()
    assertThat(settings.openInDedicatedFrameOverride).isTrue()
  }

  @Test
  fun storedDefaultValuesNormalizeToNull() {
    settings.loadState(
      AgentWorkbenchSettings.SettingsState(
        colorTabsBySourceProject = true,
        openInDedicatedFrame = false,
        showAgentActivityInMainToolbar = false,
      )
    )

    assertThat(settings.colorTabsBySourceProject).isTrue()
    assertThat(settings.colorTabsBySourceProjectOverride).isNull()
    assertThat(settings.openInDedicatedFrame).isFalse()
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
  fun openInDedicatedFrameChangeEventFiresOnlyWhenValueChanges(@TestDisposable disposable: Disposable) {
    var events = 0
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(
      AgentWorkbenchSettingsListener.TOPIC,
      object : AgentWorkbenchSettingsListener {
        override fun openInDedicatedFrameChanged() {
          events++
        }
      },
    )

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)
    assertThat(events).isZero()

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)
    assertThat(events).isEqualTo(1)

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)
    assertThat(events).isEqualTo(1)

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)
    assertThat(events).isEqualTo(2)

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)
    assertThat(events).isEqualTo(2)
    assertThat(settings.openInDedicatedFrameOverride).isNull()
  }

  @Test
  fun agentThreadsCurrentProjectOnlyFollowsDedicatedFrameByDefault() {
    assertThat(AgentThreadsProjectScopeSettings.isCurrentProjectOnly()).isTrue()
    assertThat(settings.agentThreadsCurrentProjectOnlyOverride).isNull()

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)

    assertThat(AgentThreadsProjectScopeSettings.isCurrentProjectOnly()).isFalse()
    assertThat(settings.agentThreadsCurrentProjectOnlyOverride).isNull()
  }

  @Test
  fun agentThreadsCurrentProjectOnlyOverrideSurvivesDedicatedFrameChanges() {
    AgentThreadsProjectScopeSettings.setCurrentProjectOnly(false)

    assertThat(AgentThreadsProjectScopeSettings.isCurrentProjectOnly()).isFalse()
    assertThat(settings.agentThreadsCurrentProjectOnlyOverride).isFalse()

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)

    assertThat(AgentThreadsProjectScopeSettings.isCurrentProjectOnly()).isFalse()
    assertThat(settings.agentThreadsCurrentProjectOnlyOverride).isFalse()

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)

    assertThat(AgentThreadsProjectScopeSettings.isCurrentProjectOnly()).isFalse()
    assertThat(settings.agentThreadsCurrentProjectOnlyOverride).isFalse()
  }

  @Test
  fun agentThreadsCurrentProjectOnlyClearsOverrideWhenValueMatchesDefault() {
    AgentThreadsProjectScopeSettings.setCurrentProjectOnly(false)
    assertThat(settings.agentThreadsCurrentProjectOnlyOverride).isFalse()

    AgentThreadsProjectScopeSettings.setCurrentProjectOnly(true)
    assertThat(settings.agentThreadsCurrentProjectOnlyOverride).isNull()

    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)
    AgentThreadsProjectScopeSettings.setCurrentProjectOnly(true)
    assertThat(settings.agentThreadsCurrentProjectOnlyOverride).isTrue()

    AgentThreadsProjectScopeSettings.setCurrentProjectOnly(false)
    assertThat(settings.agentThreadsCurrentProjectOnlyOverride).isNull()
  }

  @Test
  fun agentThreadsCurrentProjectOnlyChangeEventFiresOnlyWhenStoredOverrideChanges(@TestDisposable disposable: Disposable) {
    var events = 0
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(
      AgentWorkbenchSettingsListener.TOPIC,
      object : AgentWorkbenchSettingsListener {
        override fun agentThreadsCurrentProjectOnlyChanged() {
          events++
        }
      },
    )

    AgentThreadsProjectScopeSettings.setCurrentProjectOnly(true)
    assertThat(events).isZero()

    AgentThreadsProjectScopeSettings.setCurrentProjectOnly(false)
    assertThat(events).isEqualTo(1)

    AgentThreadsProjectScopeSettings.setCurrentProjectOnly(false)
    assertThat(events).isEqualTo(1)

    AgentThreadsProjectScopeSettings.setCurrentProjectOnly(true)
    assertThat(events).isEqualTo(2)
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
}
