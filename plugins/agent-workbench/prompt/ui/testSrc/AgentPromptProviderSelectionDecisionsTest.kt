package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptProviderSelectionDecisionsTest {
  private val availableProviders = listOf(AgentSessionProvider.CODEX, AgentSessionProvider.CLAUDE)

  @Test
  fun draftProviderTakesPrecedenceOverGlobalPreference() {
    assertThat(resolveRestoredPromptProvider("claude", AgentSessionProvider.CODEX, availableProviders)).isEqualTo(AgentSessionProvider.CLAUDE)
  }

  @Test
  fun globalPreferenceIsUsedWhenDraftProviderIsMissing() {
    assertThat(resolveRestoredPromptProvider(null, AgentSessionProvider.CLAUDE, availableProviders)).isEqualTo(AgentSessionProvider.CLAUDE)
  }

  @Test
  fun invalidDraftProviderFallsBackToGlobalPreference() {
    assertThat(resolveRestoredPromptProvider("unknown", AgentSessionProvider.CODEX, availableProviders)).isEqualTo(AgentSessionProvider.CODEX)
  }
}
