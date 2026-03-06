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
  fun claudeQuotaHintDefaultsToDisabledAndUnacknowledged() {
    val preferences = AgentSessionUiPreferencesStateService()

    assertThat(preferences.state.claudeQuotaHintEligible).isFalse()
    assertThat(preferences.state.claudeQuotaHintAcknowledged).isFalse()
    assertThat(preferences.claudeQuotaHintEligibleFlow.value).isFalse()
    assertThat(preferences.claudeQuotaHintAcknowledgedFlow.value).isFalse()
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

  @Test
  fun claudeQuotaHintStateRoundTrip() {
    val original = AgentSessionUiPreferencesStateService()
    original.setLastUsedProvider(AgentSessionProvider.CLAUDE)
    original.markClaudeQuotaHintEligible()
    original.acknowledgeClaudeQuotaHint()

    val reloaded = AgentSessionUiPreferencesStateService()
    reloaded.loadState(original.state)

    assertThat(reloaded.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(reloaded.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(reloaded.state.claudeQuotaHintEligible).isTrue()
    assertThat(reloaded.claudeQuotaHintEligibleFlow.value).isTrue()
    assertThat(reloaded.state.claudeQuotaHintAcknowledged).isTrue()
    assertThat(reloaded.claudeQuotaHintAcknowledgedFlow.value).isTrue()
  }
}
