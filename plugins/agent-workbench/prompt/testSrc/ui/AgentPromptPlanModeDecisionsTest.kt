// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptPlanModeDecisionsTest {
  @Test
  fun codexNewTaskWithToggleEnabledUsesPlanMode() {
    assertThat(
      resolveEffectiveCodexPlanModeEnabled(
        selectedProvider = AgentSessionProvider.CODEX,
        isCodexPlanModeSelected = true,
        targetMode = PromptTargetMode.NEW_TASK,
        selectedThreadActivity = null,
      )
    ).isTrue()
  }

  @Test
  fun codexExistingReadyTaskWithToggleEnabledUsesPlanMode() {
    assertThat(
      resolveEffectiveCodexPlanModeEnabled(
        selectedProvider = AgentSessionProvider.CODEX,
        isCodexPlanModeSelected = true,
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedThreadActivity = AgentThreadActivity.READY,
      )
    ).isTrue()
  }

  @Test
  fun codexExistingActiveTaskForcesPlanModeOff() {
    assertThat(
      resolveEffectiveCodexPlanModeEnabled(
        selectedProvider = AgentSessionProvider.CODEX,
        isCodexPlanModeSelected = true,
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedThreadActivity = AgentThreadActivity.PROCESSING,
      )
    ).isFalse()

    assertThat(
      resolveEffectiveCodexPlanModeEnabled(
        selectedProvider = AgentSessionProvider.CODEX,
        isCodexPlanModeSelected = true,
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedThreadActivity = AgentThreadActivity.REVIEWING,
      )
    ).isFalse()
  }

  @Test
  fun nonCodexProviderNeverUsesPlanMode() {
    assertThat(
      resolveEffectiveCodexPlanModeEnabled(
        selectedProvider = AgentSessionProvider.CLAUDE,
        isCodexPlanModeSelected = true,
        targetMode = PromptTargetMode.NEW_TASK,
        selectedThreadActivity = null,
      )
    ).isFalse()
  }

  @Test
  fun toggleOffNeverUsesPlanMode() {
    assertThat(
      resolveEffectiveCodexPlanModeEnabled(
        selectedProvider = AgentSessionProvider.CODEX,
        isCodexPlanModeSelected = false,
        targetMode = PromptTargetMode.NEW_TASK,
        selectedThreadActivity = null,
      )
    ).isFalse()
  }
}
