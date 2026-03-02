// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptSubmitValidationDecisionsTest {
  @Test
  fun emptyPromptIsValidationErrorInNewTaskMode() {
    assertThat(
      resolveSubmitValidationErrorMessageKey(
        targetMode = PromptTargetMode.NEW_TASK,
        prompt = "   ",
        selectedProvider = AgentSessionProvider.CODEX,
        isProviderCliAvailable = true,
        hasProjectPath = true,
        hasLauncher = true,
        selectedExistingTaskId = null,
      )
    ).isEqualTo("popup.error.empty.prompt")
  }

  @Test
  fun missingExistingTaskSelectionIsValidationErrorInExistingTaskMode() {
    assertThat(
      resolveSubmitValidationErrorMessageKey(
        targetMode = PromptTargetMode.EXISTING_TASK,
        prompt = "fix this",
        selectedProvider = AgentSessionProvider.CODEX,
        isProviderCliAvailable = true,
        hasProjectPath = true,
        hasLauncher = true,
        selectedExistingTaskId = null,
      )
    ).isEqualTo("popup.error.existing.select.task")
  }

  @Test
  fun returnsNullWhenValidationPasses() {
    assertThat(
      resolveSubmitValidationErrorMessageKey(
        targetMode = PromptTargetMode.EXISTING_TASK,
        prompt = "fix this",
        selectedProvider = AgentSessionProvider.CODEX,
        isProviderCliAvailable = true,
        hasProjectPath = true,
        hasLauncher = true,
        selectedExistingTaskId = "thread-1",
      )
    ).isNull()
  }
}
