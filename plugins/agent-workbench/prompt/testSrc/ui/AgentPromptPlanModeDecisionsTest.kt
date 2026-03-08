// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptPlanModeDecisionsTest {
  @Test
  fun codexNewTaskWithToggleEnabledUsesPlanMode() {
    assertThat(
      resolveEffectivePlanModeEnabled(
        supportsPlanMode = true,
        isPlanModeSelected = true,
        targetMode = PromptTargetMode.NEW_TASK,
        selectedThreadActivity = null,
      )
    ).isTrue()
  }

  @Test
  fun codexExistingReadyTaskWithToggleEnabledUsesPlanMode() {
    assertThat(
      resolveEffectivePlanModeEnabled(
        supportsPlanMode = true,
        isPlanModeSelected = true,
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedThreadActivity = AgentThreadActivity.READY,
      )
    ).isTrue()
  }

  @Test
  fun codexExistingActiveTaskForcesPlanModeOff() {
    assertThat(
      resolveEffectivePlanModeEnabled(
        supportsPlanMode = true,
        isPlanModeSelected = true,
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedThreadActivity = AgentThreadActivity.PROCESSING,
      )
    ).isFalse()

    assertThat(
      resolveEffectivePlanModeEnabled(
        supportsPlanMode = true,
        isPlanModeSelected = true,
        targetMode = PromptTargetMode.EXISTING_TASK,
        selectedThreadActivity = AgentThreadActivity.REVIEWING,
      )
    ).isFalse()
  }

  @Test
  fun providerWithoutPlanModeNeverUsesPlanMode() {
    assertThat(
      resolveEffectivePlanModeEnabled(
        supportsPlanMode = false,
        isPlanModeSelected = true,
        targetMode = PromptTargetMode.NEW_TASK,
        selectedThreadActivity = null,
      )
    ).isFalse()
  }

  @Test
  fun toggleOffNeverUsesPlanMode() {
    assertThat(
      resolveEffectivePlanModeEnabled(
        supportsPlanMode = true,
        isPlanModeSelected = false,
        targetMode = PromptTargetMode.NEW_TASK,
        selectedThreadActivity = null,
      )
    ).isFalse()
  }

  @Test
  fun claudeNewTaskWithToggleEnabledUsesPlanMode() {
    assertThat(
      resolveEffectivePlanModeEnabled(
        supportsPlanMode = true,
        isPlanModeSelected = true,
        targetMode = PromptTargetMode.NEW_TASK,
        selectedThreadActivity = null,
      )
    ).isTrue()
  }
}
