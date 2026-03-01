// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptFooterHintDecisionsTest {
  @Test
  fun codexExistingModeUsesCodexFooterHint() {
    assertThat(
      resolveDefaultFooterHintMessageKey(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedProvider = AgentSessionProvider.CODEX,
      )
    ).isEqualTo("popup.footer.hint.existing.codex")
  }

  @Test
  fun nonCodexOrNonExistingModeUsesDefaultFooterHint() {
    assertThat(
      resolveDefaultFooterHintMessageKey(
        targetMode = PromptTargetMode.NEW_TASK,
        selectedProvider = AgentSessionProvider.CODEX,
      )
    ).isEqualTo("popup.footer.hint")

    assertThat(
      resolveDefaultFooterHintMessageKey(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedProvider = AgentSessionProvider.CLAUDE,
      )
    ).isEqualTo("popup.footer.hint")

    assertThat(
      resolveDefaultFooterHintMessageKey(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedProvider = null,
      )
    ).isEqualTo("popup.footer.hint")
  }

  @Test
  fun existingTaskSelectionHintIsHiddenForCodexExistingMode() {
    assertThat(
      shouldShowExistingTaskSelectionHint(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedExistingTaskId = null,
        selectedProvider = AgentSessionProvider.CODEX,
      )
    ).isFalse()
  }

  @Test
  fun existingTaskSelectionHintRequiresNoSelectionInExistingNonCodexMode() {
    assertThat(
      shouldShowExistingTaskSelectionHint(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedExistingTaskId = null,
        selectedProvider = AgentSessionProvider.CLAUDE,
      )
    ).isTrue()

    assertThat(
      shouldShowExistingTaskSelectionHint(
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedExistingTaskId = "thread-1",
        selectedProvider = AgentSessionProvider.CLAUDE,
      )
    ).isFalse()

    assertThat(
      shouldShowExistingTaskSelectionHint(
        targetMode = PromptTargetMode.NEW_TASK,
        selectedExistingTaskId = null,
        selectedProvider = AgentSessionProvider.CLAUDE,
      )
    ).isFalse()
  }
}
