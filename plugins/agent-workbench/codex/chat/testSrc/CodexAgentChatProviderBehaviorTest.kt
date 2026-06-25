// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

import com.intellij.agent.workbench.chat.AgentChatBehaviorFile
import com.intellij.agent.workbench.chat.AgentChatBehaviorTerminalTab
import com.intellij.agent.workbench.chat.AgentChatInitialMessageDispatchContext
import com.intellij.agent.workbench.chat.AgentChatInitialMessageRetryDecision
import com.intellij.agent.workbench.chat.AgentChatInitialMessageSendObservation
import com.intellij.agent.workbench.chat.AgentChatInitialMessageTerminalSendMode
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexAgentChatProviderBehaviorTest {
  private val behavior = CodexAgentChatProviderBehavior()

  @Test
  fun concreteThreadRebindCommandsAreExactNewOrFork() {
    assertThat(behavior.isConcreteNewThreadRebindCommand("/new")).isTrue()
    assertThat(behavior.isConcreteNewThreadRebindCommand("/fork")).isTrue()

    assertThat(behavior.isConcreteNewThreadRebindCommand("/fork now")).isFalse()
    assertThat(behavior.isConcreteNewThreadRebindCommand("/forkx")).isFalse()
    assertThat(behavior.isConcreteNewThreadRebindCommand("echo /fork")).isFalse()
  }

  @Test
  fun planCommandIsSentWithoutBracketedPasteMode() {
    assertThat(behavior.shouldUseBracketedPasteMode("/plan")).isFalse()
    assertThat(behavior.shouldUseBracketedPasteMode("  /plan  ")).isFalse()
    assertThat(behavior.shouldUseBracketedPasteMode("/plan Refactor this")).isTrue()
    assertThat(behavior.shouldUseBracketedPasteMode("Refactor this")).isTrue()
  }

  @Test
  fun generatedPlanCommandRequestsTerminalOutputObservation() {
    assertThat(
      behavior.requiresPostSendObservation(
        planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY),
      )
    ).isTrue()

    assertThat(behavior.requiresPostSendObservation(planCommandDispatch())).isFalse()
    assertThat(behavior.requiresPostSendObservation(promptDispatch())).isFalse()
  }

  @Test
  fun generatedPlanCommandUsesInteractiveTerminalCommandSendMode() {
    assertThat(
      behavior.initialMessageTerminalSendMode(
        planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY),
      )
    ).isEqualTo(AgentChatInitialMessageTerminalSendMode.INTERACTIVE_COMMAND)

    assertThat(behavior.initialMessageTerminalSendMode(planCommandDispatch()))
      .isEqualTo(AgentChatInitialMessageTerminalSendMode.TEXT)
    assertThat(behavior.initialMessageTerminalSendMode(planCommandWithArgsDispatch()))
      .isEqualTo(AgentChatInitialMessageTerminalSendMode.TEXT)
    assertThat(behavior.initialMessageTerminalSendMode(promptDispatch()))
      .isEqualTo(AgentChatInitialMessageTerminalSendMode.TEXT)
  }

  @Test
  fun generatedPlanCommandBusyOutputStopsDispatch() {
    val dispatch = planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY)

    assertThat(
      behavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("'/plan' is disabled while a task is in progress."),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)
  }

  @Test
  fun generatedPlanCommandFormattedBusyOutputStopsDispatch() {
    val dispatch = planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY)

    assertThat(
      behavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("\u001B[31m'/plan'\u001B[0m   is disabled while a\n task is in progress."),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)
  }

  @Test
  fun confirmedPlanCommandResponseOutputProceeds() {
    val dispatch = planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY)
    val confirmedResponses = listOf(
      "Plan mode",
      "status: Plan mode (shift+tab to cycle)",
    )

    for (output in confirmedResponses) {
      assertThat(
        behavior.afterInitialMessageSendObservation(
          file = TestBehaviorFile(),
          dispatch = dispatch,
          observation = observation(output),
          retryAttempt = 0,
        )
      ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
    }
  }

  @Test
  fun planCommandFailureOutputStopsDispatch() {
    val dispatch = planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY)
    val failureResponses = listOf(
      "Plan mode unavailable right now.",
      "Collaboration modes are disabled.",
      "Unknown command: /plan",
    )

    for (output in failureResponses) {
      assertThat(
        behavior.afterInitialMessageSendObservation(
          file = TestBehaviorFile(),
          dispatch = dispatch,
          observation = observation(output),
          retryAttempt = 0,
        )
      ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)
    }
  }

  @Test
  fun unconfirmedPlanCommandOutputWaitsForMoreOutput() {
    val dispatch = planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY)
    // The prompt step must stay withheld while Codex has not yet reacted to `/plan` (still echoing the
    // command, rendering startup, or running a SessionStart hook), so it cannot merge onto the input line.
    val unconfirmedOutputs = listOf(
      "/plan",
      "\u001B[0m \n\t",
      "Running SessionStart hook:\nhook output",
    )

    for (output in unconfirmedOutputs) {
      assertThat(
        behavior.afterInitialMessageSendObservation(
          file = TestBehaviorFile(),
          dispatch = dispatch,
          observation = observation(output),
          retryAttempt = 0,
        )
      ).isEqualTo(AgentChatInitialMessageRetryDecision.AwaitMorePostSendOutput(backoffMs = 250))
    }
  }

  @Test
  fun nonRetryablePlanCommandOutputProceedsIfObserved() {
    assertThat(
      behavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = planCommandDispatch(),
        observation = observation("'/plan' is disabled while a task is in progress."),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
  }

  @Test
  fun concreteBusyPlanModeBeforePlanStepStopsDispatch(): Unit = runBlocking {
    assertThat(
      behavior.beforeInitialMessageSend(
        file = TestBehaviorFile(
          threadActivity = AgentThreadActivity.PROCESSING,
          initialMessageMode = AgentInitialMessageMode.PLAN,
        ),
        tab = TestBehaviorTerminalTab(),
        dispatch = planCommandDispatch(stepIndex = 0),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)
  }

  @Test
  fun concreteBusyPlanModeBeforePromptStepStopsDispatch(): Unit = runBlocking {
    assertThat(
      behavior.beforeInitialMessageSend(
        file = TestBehaviorFile(
          threadActivity = AgentThreadActivity.REVIEWING,
          initialMessageMode = AgentInitialMessageMode.PLAN,
        ),
        tab = TestBehaviorTerminalTab(),
        dispatch = promptDispatch(),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)
  }

  @Test
  fun pendingBusyPlanModeProceedsBeforeSend(): Unit = runBlocking {
    assertThat(
      behavior.beforeInitialMessageSend(
        file = TestBehaviorFile(
          isPendingThread = true,
          threadActivity = AgentThreadActivity.PROCESSING,
          initialMessageMode = AgentInitialMessageMode.PLAN,
        ),
        tab = TestBehaviorTerminalTab(),
        dispatch = planCommandDispatch(stepIndex = 0),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
  }

  @Test
  fun nonPlanOrReadyPlanModeProceedsBeforeSend(): Unit = runBlocking {
    assertThat(
      behavior.beforeInitialMessageSend(
        file = TestBehaviorFile(
          threadActivity = AgentThreadActivity.PROCESSING,
          initialMessageMode = AgentInitialMessageMode.STANDARD,
        ),
        tab = TestBehaviorTerminalTab(),
        dispatch = promptDispatch(),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)

    assertThat(
      behavior.beforeInitialMessageSend(
        file = TestBehaviorFile(
          threadActivity = AgentThreadActivity.READY,
          initialMessageMode = AgentInitialMessageMode.PLAN,
        ),
        tab = TestBehaviorTerminalTab(),
        dispatch = promptDispatch(),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
  }
}

private fun observation(outputText: String, recentOutputTail: String = ""): AgentChatInitialMessageSendObservation {
  return AgentChatInitialMessageSendObservation(outputText = outputText, recentOutputTail = recentOutputTail)
}

private fun planCommandDispatch(
  stepIndex: Int = 0,
  completionPolicy: AgentInitialMessageDispatchCompletionPolicy = AgentInitialMessageDispatchCompletionPolicy.IMMEDIATE,
): TestDispatch {
  return TestDispatch(
    action = AgentInitialMessageDispatchAction.SEND_TEXT,
    message = "/plan",
    stepIndex = stepIndex,
    completionPolicy = completionPolicy,
  )
}

private fun promptDispatch(): TestDispatch {
  return TestDispatch(
    action = AgentInitialMessageDispatchAction.SEND_TEXT,
    message = "Refactor this",
    stepIndex = 1,
  )
}

private fun planCommandWithArgsDispatch(): TestDispatch {
  return TestDispatch(
    action = AgentInitialMessageDispatchAction.SEND_TEXT,
    message = "/plan Refactor this",
    stepIndex = 1,
    completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
  )
}

private data class TestBehaviorFile(
  override val provider: AgentSessionProvider? = AgentSessionProvider.from("codex"),
  override val isPendingThread: Boolean = false,
  override val subAgentId: String? = null,
  override val pendingFirstInputAtMs: Long? = null,
  override val threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
  override val initialMessageMode: AgentInitialMessageMode? = null,
) : AgentChatBehaviorFile

private class TestBehaviorTerminalTab : AgentChatBehaviorTerminalTab {
  override suspend fun readRecentOutputTail(): String = ""
}

private data class TestDispatch(
  override val action: AgentInitialMessageDispatchAction,
  override val message: String,
  override val stepIndex: Int,
  override val completionPolicy: AgentInitialMessageDispatchCompletionPolicy = AgentInitialMessageDispatchCompletionPolicy.IMMEDIATE,
) : AgentChatInitialMessageDispatchContext
