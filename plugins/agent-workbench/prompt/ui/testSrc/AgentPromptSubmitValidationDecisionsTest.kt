// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchPromptBlockedReason
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetryEvent
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetryProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptSubmitValidationDecisionsTest {
  @Test
  fun promptSubmitBlockedTelemetryMapsValidationKeys() {
    val telemetryEvents = mutableListOf<AgentWorkbenchTelemetryEvent>()
    val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

    try {
      listOf(
        "popup.error.empty.prompt",
        "popup.error.no.providers",
        "popup.error.provider.unavailable",
        "popup.error.project.path",
        "popup.error.no.launcher",
        "popup.error.existing.select.task",
      ).forEach { messageKey ->
        reportPromptSubmitBlocked(
          validationErrorKey = messageKey,
          provider = AgentSessionProvider.CODEX,
          launchMode = AgentSessionLaunchMode.YOLO,
        )
      }

      assertThat(telemetryEvents).containsExactly(
        AgentWorkbenchTelemetryEvent(
          id = AgentWorkbenchTelemetry.PROMPT_SUBMIT_BLOCKED_EVENT_ID,
          provider = AgentWorkbenchTelemetryProvider.CODEX,
          launchMode = AgentSessionLaunchMode.YOLO,
          blockedReason = AgentWorkbenchPromptBlockedReason.EMPTY_PROMPT,
        ),
        AgentWorkbenchTelemetryEvent(
          id = AgentWorkbenchTelemetry.PROMPT_SUBMIT_BLOCKED_EVENT_ID,
          provider = AgentWorkbenchTelemetryProvider.CODEX,
          launchMode = AgentSessionLaunchMode.YOLO,
          blockedReason = AgentWorkbenchPromptBlockedReason.NO_PROVIDERS,
        ),
        AgentWorkbenchTelemetryEvent(
          id = AgentWorkbenchTelemetry.PROMPT_SUBMIT_BLOCKED_EVENT_ID,
          provider = AgentWorkbenchTelemetryProvider.CODEX,
          launchMode = AgentSessionLaunchMode.YOLO,
          blockedReason = AgentWorkbenchPromptBlockedReason.PROVIDER_UNAVAILABLE,
        ),
        AgentWorkbenchTelemetryEvent(
          id = AgentWorkbenchTelemetry.PROMPT_SUBMIT_BLOCKED_EVENT_ID,
          provider = AgentWorkbenchTelemetryProvider.CODEX,
          launchMode = AgentSessionLaunchMode.YOLO,
          blockedReason = AgentWorkbenchPromptBlockedReason.PROJECT_PATH,
        ),
        AgentWorkbenchTelemetryEvent(
          id = AgentWorkbenchTelemetry.PROMPT_SUBMIT_BLOCKED_EVENT_ID,
          provider = AgentWorkbenchTelemetryProvider.CODEX,
          launchMode = AgentSessionLaunchMode.YOLO,
          blockedReason = AgentWorkbenchPromptBlockedReason.NO_LAUNCHER,
        ),
        AgentWorkbenchTelemetryEvent(
          id = AgentWorkbenchTelemetry.PROMPT_SUBMIT_BLOCKED_EVENT_ID,
          provider = AgentWorkbenchTelemetryProvider.CODEX,
          launchMode = AgentSessionLaunchMode.YOLO,
          blockedReason = AgentWorkbenchPromptBlockedReason.EXISTING_TASK_NOT_SELECTED,
        ),
      )
    }
    finally {
      token.finish()
    }
  }

  @Test
  fun projectPathValidationRetriesWithoutReportingBlockedTelemetry() {
    val telemetryEvents = mutableListOf<AgentWorkbenchTelemetryEvent>()
    val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

    try {
      val retried = shouldRetrySubmitAfterWorkingProjectPathSelection(
        validationErrorKey = "popup.error.project.path",
        requestWorkingProjectPathSelection = { true },
      )

      if (!retried) {
        reportPromptSubmitBlocked(
          validationErrorKey = "popup.error.project.path",
          provider = AgentSessionProvider.CODEX,
          launchMode = AgentSessionLaunchMode.STANDARD,
        )
      }

      assertThat(retried).isTrue()
      assertThat(telemetryEvents).isEmpty()
    }
    finally {
      token.finish()
    }
  }
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
