// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.settings

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionProviderSettingsServiceTest {
  private val service: AgentSessionProviderSettingsService
    get() = service()

  @AfterEach
  fun resetProviderSettings() {
    service.setProviderEnabled(AgentSessionProvider.from("codex"), true)
    service.setProviderEnabled(AgentSessionProvider.from("claude"), true)
    service.setProviderFeatureEnabled(AgentSessionProvider.from("codex"), TEST_PROVIDER_FEATURE_ID, true)
  }

  @Test
  fun providersAreEnabledByDefault() {
    assertThat(service.isProviderEnabled(AgentSessionProvider.from("codex"))).isTrue()
  }

  @Test
  fun providerFeaturesAreEnabledByDefault() {
    assertThat(service.isProviderFeatureEnabled(AgentSessionProvider.from("codex"), TEST_PROVIDER_FEATURE_ID)).isTrue()
  }

  @Test
  fun disabledProviderFeaturesAreTrackedIndependentlyFromProviderEnabledState() {
    service.setProviderFeatureEnabled(AgentSessionProvider.from("codex"), TEST_PROVIDER_FEATURE_ID, false)
    service.setProviderEnabled(AgentSessionProvider.from("codex"), false)

    assertThat(service.isProviderEnabled(AgentSessionProvider.from("codex"))).isFalse()
    assertThat(service.isProviderFeatureEnabled(AgentSessionProvider.from("codex"), TEST_PROVIDER_FEATURE_ID)).isFalse()

    service.setProviderFeatureEnabled(AgentSessionProvider.from("codex"), TEST_PROVIDER_FEATURE_ID, true)

    assertThat(service.isProviderEnabled(AgentSessionProvider.from("codex"))).isFalse()
    assertThat(service.isProviderFeatureEnabled(AgentSessionProvider.from("codex"), TEST_PROVIDER_FEATURE_ID)).isTrue()
  }

  companion object {
    private const val TEST_PROVIDER_FEATURE_ID = "test.feature"
  }
}
