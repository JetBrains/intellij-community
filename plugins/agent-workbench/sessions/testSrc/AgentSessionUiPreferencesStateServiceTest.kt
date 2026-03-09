package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionUiPreferencesStateServiceTest {
  @Test
  fun lastUsedProviderDefaultsToNull() {
    val preferences = AgentSessionUiPreferencesStateService()
    assertThat(preferences.getLastUsedProvider()).isNull()
    assertThat(preferences.lastUsedProviderFlow.value).isNull()
  }

  @Test
  fun setAndGetLastUsedProvider() {
    val preferences = AgentSessionUiPreferencesStateService()

    preferences.setLastUsedProvider(AgentSessionProvider.CLAUDE)
    assertThat(preferences.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(preferences.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CLAUDE)

    preferences.setLastUsedProvider(AgentSessionProvider.CODEX)
    assertThat(preferences.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(preferences.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CODEX)
  }
}
