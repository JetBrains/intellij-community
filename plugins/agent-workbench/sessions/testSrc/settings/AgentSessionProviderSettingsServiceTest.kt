// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.settings

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.TestAgentSessionProviderDescriptor
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionProviderSettingsServiceTest {
  private val service: AgentSessionProviderSettingsService
    get() = service()

  @AfterEach
  fun resetProviderSettings() {
    service.setProviderEnabled(AgentSessionProvider.CODEX, true)
    service.setProviderEnabled(AgentSessionProvider.CLAUDE, true)
  }

  @Test
  fun providersAreEnabledByDefault() {
    assertThat(service.isProviderEnabled(AgentSessionProvider.CODEX)).isTrue()
  }

  @Test
  fun disabledProvidersAreFilteredFromDescriptorsAndSources() {
    val codex = descriptor(AgentSessionProvider.CODEX)
    val claude = descriptor(AgentSessionProvider.CLAUDE)

    service.setProviderEnabled(AgentSessionProvider.CODEX, false)

    assertThat(service.enabledProviders(listOf(codex, claude))).containsExactly(claude)
    assertThat(service.enabledSessionSources(listOf(codex.sessionSource, claude.sessionSource))).containsExactly(claude.sessionSource)
  }

  private fun descriptor(provider: AgentSessionProvider): TestAgentSessionProviderDescriptor {
    return TestAgentSessionProviderDescriptor(
      provider = provider,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
  }
}
