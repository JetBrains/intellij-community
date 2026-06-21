// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.codex.common.CodexThreadActivityProjection
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySignal
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodexThreadActivityProjectionTest {
  @Test
  fun completedPlanTurnDoesNotRemainNeedsInput() {
    val projection = CodexThreadActivityProjection()
    projection.markUserMessage(1)
    projection.markTurnStarted(order = 2, turnId = "turn-1")
    projection.markPlan(order = 3, turnId = "turn-1")
    projection.markTurnCompleted(order = 4, turnId = "turn-1")

    val snapshot = projection.snapshot()

    assertThat(snapshot.hasPendingPlan).isFalse()
    assertThat(snapshot.hasInProgressTurn).isFalse()
    assertThat(snapshot.toCodexSessionActivity()).isEqualTo(CodexSessionActivity.READY)
  }

  @Test
  fun staleTurnCompletionDoesNotClearNewerPlan() {
    val projection = CodexThreadActivityProjection()
    projection.markUserMessage(1)
    projection.markPlan(order = 2, turnId = "turn-new")
    projection.markTurnCompleted(order = 3, turnId = "turn-old")

    val snapshot = projection.snapshot()

    assertThat(snapshot.hasPendingPlan).isTrue()
    assertThat(snapshot.toCodexSessionActivity()).isEqualTo(CodexSessionActivity.NEEDS_INPUT)
  }

  @Test
  fun legacyCompletionWithoutTurnIdClearsEarlierPlan() {
    val projection = CodexThreadActivityProjection()
    projection.markUserMessage(1)
    projection.markPlan(order = 2)
    projection.markTurnCompleted(order = 3)

    val snapshot = projection.snapshot()

    assertThat(snapshot.hasPendingPlan).isFalse()
    assertThat(snapshot.toCodexSessionActivity()).isEqualTo(CodexSessionActivity.READY)
  }

  @Test
  fun userMessageClearsPendingPlan() {
    val projection = CodexThreadActivityProjection()
    projection.markUserMessage(1)
    projection.markPlan(order = 2)
    projection.markUserMessage(3)

    val snapshot = projection.snapshot()

    assertThat(snapshot.hasPendingPlan).isFalse()
    assertThat(snapshot.toCodexSessionActivity()).isEqualTo(CodexSessionActivity.READY)
  }

  @Test
  fun openToolCallIsProcessing() {
    val projection = CodexThreadActivityProjection()
    projection.markToolCallStarted(order = 1, callId = "call-1", turnId = "turn-1")

    val snapshot = projection.snapshot()

    assertThat(snapshot.hasInProgressTurn).isTrue()
    assertThat(snapshot.toCodexSessionActivity()).isEqualTo(CodexSessionActivity.PROCESSING)
  }

  @Test
  fun assistantOutputAfterUserMessageIsUnread() {
    val projection = CodexThreadActivityProjection()
    projection.markUserMessage(1)
    projection.markAssistantMessage(2)

    val snapshot = projection.snapshot()

    assertThat(snapshot.hasUnreadAssistantMessage).isTrue()
    assertThat(snapshot.toCodexSessionActivity()).isEqualTo(CodexSessionActivity.UNREAD)
  }

  @Test
  fun activitySignalsUseProjectionSemantics() {
    val projection = CodexThreadActivityProjection()
    projection.apply(CodexThreadActivitySignal.UserMessage(1))
    projection.apply(CodexThreadActivitySignal.Plan(order = 2, turnId = "turn-1"))
    projection.apply(CodexThreadActivitySignal.TurnCompleted(order = 3, turnId = "turn-1"))

    val snapshot = projection.snapshot()

    assertThat(snapshot.hasPendingPlan).isFalse()
    assertThat(snapshot.toCodexSessionActivity()).isEqualTo(CodexSessionActivity.READY)
  }

  private fun CodexThreadActivityProjection.snapshot() = toSnapshot(
    threadId = "thread-1",
    updatedAt = 100L,
    statusKind = CodexThreadStatusKind.IDLE,
    hasTurnActivity = true,
  )
}
