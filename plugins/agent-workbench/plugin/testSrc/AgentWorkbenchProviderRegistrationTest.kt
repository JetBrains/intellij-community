// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.plugin

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchProviderRegistrationTest {
  @Test
  fun builtInProvidersAreRegisteredForProviderMenus() {
    val providers = AgentSessionProviders.allProviders()
    val registeredProviderIds = providers.map { it.provider }

    assertTrue(AgentSessionProvider.CODEX in registeredProviderIds)
    assertTrue(AgentSessionProvider.CLAUDE in registeredProviderIds)
    assertTrue(AgentSessionProvider.JUNIE in registeredProviderIds)
    assertTrue(AgentSessionProvider.PI in registeredProviderIds)
    assertTrue(AgentSessionProvider.TERMINAL in registeredProviderIds)

    val standardMenuProviders = buildAgentSessionProviderMenuModel(
      providers,
      providers.associate { it.provider to true },
    ).standardItems.map { it.bridge.provider }
    assertTrue(AgentSessionProvider.JUNIE in standardMenuProviders)
    assertTrue(AgentSessionProvider.PI in standardMenuProviders)
  }
}
