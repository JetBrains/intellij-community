// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.plugin

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
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

    assertTrue(AgentSessionProvider.from("codex") in registeredProviderIds)
    assertTrue(AgentSessionProvider.from("claude") in registeredProviderIds)
    assertTrue(AgentSessionProvider.from("junie") in registeredProviderIds)
    assertTrue(AgentSessionProvider.from("opencode") in registeredProviderIds)
    assertTrue(AgentSessionProvider.from("pi") in registeredProviderIds)
    assertTrue(AgentSessionProvider.from("terminal") in registeredProviderIds)

    val standardMenuProviders = buildAgentSessionProviderMenuModel(
      providers,
      providers.associate { it.provider to true },
    ).standardItems.map { it.bridge.provider }
    assertTrue(AgentSessionProvider.from("junie") in standardMenuProviders)
    assertTrue(AgentSessionProvider.from("opencode") in standardMenuProviders)
    assertTrue(AgentSessionProvider.from("pi") in standardMenuProviders)

    val yoloMenuProviders = buildAgentSessionProviderMenuModel(
      providers,
      providers.associate { it.provider to true },
    ).yoloItems.map { it.bridge.provider }
    assertFalse(AgentSessionProvider.from("opencode") in yoloMenuProviders)
  }
}
