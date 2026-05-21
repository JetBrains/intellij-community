// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
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
  fun colorTabsBySourceProjectIsEnabledByDefault() {
    assertThat(settings.colorTabsBySourceProject).isTrue()
  }

  @Test
  fun colorTabsBySourceProjectStateRoundTrips() {
    settings.loadState(AgentWorkbenchSettings.SettingsState(colorTabsBySourceProject = false))

    assertThat(settings.colorTabsBySourceProject).isFalse()

    settings.setColorTabsBySourceProject(true)

    assertThat(settings.colorTabsBySourceProject).isTrue()
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
  }
}
