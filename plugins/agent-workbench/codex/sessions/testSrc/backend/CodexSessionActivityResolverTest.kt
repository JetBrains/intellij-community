// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.codex.common.CodexAppServerStartedThread
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActiveFlag
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexSessionActivityResolverTest {
  @Test
  fun threadStatusKindsMapToExpectedSessionActivities() {
    assertThat(thread(statusKind = CodexThreadStatusKind.IDLE).toCodexSessionActivity())
      .isEqualTo(CodexSessionActivity.READY)
    assertThat(thread(statusKind = CodexThreadStatusKind.SYSTEM_ERROR).toCodexSessionActivity())
      .isEqualTo(CodexSessionActivity.READY)
    assertThat(thread(statusKind = CodexThreadStatusKind.ACTIVE).toCodexSessionActivity())
      .isEqualTo(CodexSessionActivity.PROCESSING)
  }

  @Test
  fun responseRequiredFlagsAreRecognizedAndMapToNeedsInput() {
    assertThat(emptyList<CodexThreadActiveFlag>().isResponseRequired()).isFalse()
    assertThat(listOf(CodexThreadActiveFlag.WAITING_ON_APPROVAL).isResponseRequired()).isTrue()
    assertThat(listOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT).isResponseRequired()).isTrue()

    assertThat(
      thread(
        statusKind = CodexThreadStatusKind.ACTIVE,
        activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_APPROVAL),
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
    assertThat(
      thread(
        statusKind = CodexThreadStatusKind.ACTIVE,
        activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT),
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
  }

  @Test
  fun snapshotSignalsMapToExpectedSessionActivities() {
    assertThat(
      snapshot(
        statusKind = CodexThreadStatusKind.IDLE,
        hasUnreadAssistantMessage = true,
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.UNREAD)
    assertThat(
      snapshot(
        statusKind = CodexThreadStatusKind.IDLE,
        hasPendingPlan = true,
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
    assertThat(
      snapshot(
        statusKind = CodexThreadStatusKind.IDLE,
        isReviewing = true,
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.REVIEWING)
    assertThat(
      snapshot(
        statusKind = CodexThreadStatusKind.IDLE,
        hasInProgressTurn = true,
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.PROCESSING)
  }

  @Test
  fun passiveUnreadDoesNotOverrideReviewingOrProcessing() {
    assertThat(
      snapshot(
        statusKind = CodexThreadStatusKind.IDLE,
        hasUnreadAssistantMessage = true,
        isReviewing = true,
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.REVIEWING)
    assertThat(
      snapshot(
        statusKind = CodexThreadStatusKind.ACTIVE,
        hasUnreadAssistantMessage = true,
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.PROCESSING)
    assertThat(
      snapshot(
        statusKind = CodexThreadStatusKind.ACTIVE,
        hasUnreadAssistantMessage = true,
        hasTurnActivity = true,
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.UNREAD)
  }

  @Test
  fun responseRequiredOverridesReviewingAndProcessing() {
    assertThat(
      snapshot(
        statusKind = CodexThreadStatusKind.ACTIVE,
        activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT),
        hasUnreadAssistantMessage = true,
        isReviewing = true,
        hasInProgressTurn = true,
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
    assertThat(
      snapshot(
        statusKind = CodexThreadStatusKind.ACTIVE,
        hasPendingPlan = true,
        isReviewing = true,
        hasInProgressTurn = true,
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
  }

  @Test
  fun startedThreadUsesSameStatusMappingAsOtherCodexModels() {
    assertThat(startedThread(statusKind = CodexThreadStatusKind.IDLE).toCodexSessionActivity())
      .isEqualTo(CodexSessionActivity.READY)
    assertThat(startedThread(statusKind = CodexThreadStatusKind.ACTIVE).toCodexSessionActivity())
      .isEqualTo(CodexSessionActivity.PROCESSING)
    assertThat(
      startedThread(
        statusKind = CodexThreadStatusKind.ACTIVE,
        activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT),
      ).toCodexSessionActivity()
    ).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
  }
}

private fun thread(
  statusKind: CodexThreadStatusKind,
  activeFlags: List<CodexThreadActiveFlag> = emptyList(),
): CodexThread {
  return CodexThread(
    id = "thread-1",
    title = "Thread 1",
    updatedAt = 100L,
    archived = false,
    statusKind = statusKind,
    activeFlags = activeFlags,
  )
}

private fun snapshot(
  statusKind: CodexThreadStatusKind,
  activeFlags: List<CodexThreadActiveFlag> = emptyList(),
  hasUnreadAssistantMessage: Boolean = false,
  hasPendingPlan: Boolean = false,
  isReviewing: Boolean = false,
  hasInProgressTurn: Boolean = false,
  hasTurnActivity: Boolean = false,
): CodexThreadActivitySnapshot {
  return CodexThreadActivitySnapshot(
    threadId = "thread-1",
    updatedAt = 100L,
    statusKind = statusKind,
    activeFlags = activeFlags,
    hasUnreadAssistantMessage = hasUnreadAssistantMessage,
    hasPendingPlan = hasPendingPlan,
    isReviewing = isReviewing,
    hasInProgressTurn = hasInProgressTurn,
    hasTurnActivity = hasTurnActivity,
  )
}

private fun startedThread(
  statusKind: CodexThreadStatusKind,
  activeFlags: List<CodexThreadActiveFlag> = emptyList(),
): CodexAppServerStartedThread {
  return CodexAppServerStartedThread(
    id = "thread-1",
    title = "Thread 1",
    updatedAt = 100L,
    cwd = "/work/project",
    statusKind = statusKind,
    activeFlags = activeFlags,
  )
}
