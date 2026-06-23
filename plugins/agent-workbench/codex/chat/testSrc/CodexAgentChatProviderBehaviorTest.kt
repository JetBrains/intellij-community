// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

import com.intellij.agent.workbench.chat.AgentChatBehaviorFile
import com.intellij.agent.workbench.chat.AgentChatBehaviorTerminalTab
import com.intellij.agent.workbench.chat.AgentChatInitialMessageDispatchContext
import com.intellij.agent.workbench.chat.AgentChatInitialMessageRetryDecision
import com.intellij.agent.workbench.chat.AgentChatInitialMessageSendObservation
import com.intellij.agent.workbench.chat.AgentChatProviderBehaviors
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
  @Test
  fun behaviorIsRegisteredForCodexProvider() {
    assertThat(AgentChatProviderBehaviors.find(AgentSessionProvider.CODEX))
      .isSameAs(CodexAgentChatProviderBehavior)
  }

  @Test
  fun concreteThreadRebindCommandsAreExactNewOrFork() {
    assertThat(CodexAgentChatProviderBehavior.isConcreteNewThreadRebindCommand("/new")).isTrue()
    assertThat(CodexAgentChatProviderBehavior.isConcreteNewThreadRebindCommand("/fork")).isTrue()

    assertThat(CodexAgentChatProviderBehavior.isConcreteNewThreadRebindCommand("/fork now")).isFalse()
    assertThat(CodexAgentChatProviderBehavior.isConcreteNewThreadRebindCommand("/forkx")).isFalse()
    assertThat(CodexAgentChatProviderBehavior.isConcreteNewThreadRebindCommand("echo /fork")).isFalse()
  }

  @Test
  fun planCommandIsSentWithoutBracketedPasteMode() {
    assertThat(CodexAgentChatProviderBehavior.shouldUseBracketedPasteMode("/plan")).isFalse()
    assertThat(CodexAgentChatProviderBehavior.shouldUseBracketedPasteMode("  /plan  ")).isFalse()
    assertThat(CodexAgentChatProviderBehavior.shouldUseBracketedPasteMode("/plan Refactor this")).isTrue()
    assertThat(CodexAgentChatProviderBehavior.shouldUseBracketedPasteMode("Refactor this")).isTrue()
  }

  @Test
  fun generatedPlanCommandRequestsTerminalOutputObservation() {
    assertThat(
      CodexAgentChatProviderBehavior.requiresPostSendObservation(
        planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY),
      )
    ).isTrue()

    assertThat(CodexAgentChatProviderBehavior.requiresPostSendObservation(planCommandDispatch())).isFalse()
    assertThat(CodexAgentChatProviderBehavior.requiresPostSendObservation(promptDispatch())).isFalse()
  }

  @Test
  fun generatedPlanCommandBusyOutputRetriesAfterFreshReadiness() {
    val dispatch = planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY)

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("'/plan' is disabled while a task is in progress."),
        retryAttempt = 0,
      )
    ).isEqualTo(AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterReadiness(backoffMs = 250))
  }

  @Test
  fun generatedPlanCommandFormattedBusyOutputRetriesAfterFreshReadiness() {
    val dispatch = planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY)

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("\u001B[31m'/plan'\u001B[0m   is disabled while a\n task is in progress."),
        retryAttempt = 0,
      )
    ).isEqualTo(AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterReadiness(backoffMs = 250))
  }

  @Test
  fun nonBusyPlanCommandOutputProceeds() {
    val dispatch = planCommandDispatch(completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY)
    val ignoredOutputs = listOf(
      "Plan mode unavailable right now.",
      "Collaboration modes are disabled.",
      "Unknown command: /plan",
      "'/permissions' is disabled while a task is in progress.",
      "\u001B[0m \n\t",
      "Running SessionStart hook:\nhook output",
    )

    for (output in ignoredOutputs) {
      assertThat(
        CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
          file = TestBehaviorFile(),
          dispatch = dispatch,
          observation = observation(output),
          retryAttempt = 0,
        )
      ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
    }
  }

  @Test
  fun nonRetryablePlanCommandOutputProceedsIfObserved() {
    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = planCommandDispatch(),
        observation = observation("'/plan' is disabled while a task is in progress."),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
  }

  @Test
  fun busyPlanModeBeforePlanStepRetriesAfterFreshReadiness(): Unit = runBlocking {
    assertThat(
      CodexAgentChatProviderBehavior.beforeInitialMessageSend(
        file = TestBehaviorFile(
          threadActivity = AgentThreadActivity.PROCESSING,
          initialMessageMode = AgentInitialMessageMode.PLAN,
        ),
        tab = TestBehaviorTerminalTab(),
        dispatch = planCommandDispatch(stepIndex = 0),
        retryAttempt = 0,
      )
    ).isEqualTo(AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterReadiness(backoffMs = 250))
  }

  @Test
  fun busyPlanModeBeforePromptStepRewindsAndRetriesAfterFreshReadiness(): Unit = runBlocking {
    assertThat(
      CodexAgentChatProviderBehavior.beforeInitialMessageSend(
        file = TestBehaviorFile(
          threadActivity = AgentThreadActivity.REVIEWING,
          initialMessageMode = AgentInitialMessageMode.PLAN,
        ),
        tab = TestBehaviorTerminalTab(),
        dispatch = promptDispatch(),
        retryAttempt = 0,
      )
    ).isEqualTo(AgentChatInitialMessageRetryDecision.RetryTransientBusyAfterRewindAndReadiness(backoffMs = 250))
  }

  @Test
  fun nonPlanOrReadyPlanModeProceedsBeforeSend(): Unit = runBlocking {
    assertThat(
      CodexAgentChatProviderBehavior.beforeInitialMessageSend(
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
      CodexAgentChatProviderBehavior.beforeInitialMessageSend(
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

private data class TestBehaviorFile(
  override val provider: AgentSessionProvider? = AgentSessionProvider.CODEX,
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
