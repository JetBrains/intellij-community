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
import com.intellij.testFramework.junit5.TestApplication
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
  fun planCommandRetryIgnoresThreadActivityBeforeSend(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = planCommandDispatch()

    assertThat(
      CodexAgentChatProviderBehavior.beforeInitialMessageSend(
        file = TestBehaviorFile(threadActivity = AgentThreadActivity.PROCESSING),
        tab = TestBehaviorTerminalTab(recentOutputTail = ""),
        dispatch = dispatch,
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
  }

  @Test
  fun planCommandRetryIgnoresStartupTerminalOutputBeforeSend(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = planCommandDispatch()
    assertThat(
      CodexAgentChatProviderBehavior.beforeInitialMessageSend(
        file = TestBehaviorFile(threadActivity = AgentThreadActivity.READY),
        tab = TestBehaviorTerminalTab(recentOutputTail = "Running SessionStart hook:\nhook output"),
        dispatch = dispatch,
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
  }

  @Test
  fun planCommandRetryTreatsDisabledPlanCommandAsTransientBusy(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = planCommandDispatch()

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("'/plan' is disabled while a task is in progress."),
        retryAttempt = 0,
      )
    ).isInstanceOf(AgentChatInitialMessageRetryDecision.RetryTransientBusyWithoutReadiness::class.java)
  }

  @Test
  fun planCommandRetryStopsWhenPlanModeIsUnavailable(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = planCommandDispatch()

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("Plan mode unavailable right now."),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("Collaboration modes are disabled."),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)
  }

  @Test
  fun planCommandRetryStopsWhenPlanCommandIsUnsupported(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = planCommandDispatch()

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("Unknown command: /plan"),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("/plan is not a recognized command"),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)
  }

  @Test
  fun planCommandRetryRetriesBlankOutputBeforeStopping(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = planCommandDispatch()

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation("\u001B[0m \n\t"),
        retryAttempt = 0,
      )
    ).isInstanceOf(AgentChatInitialMessageRetryDecision.RetryWithoutReadiness::class.java)

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation(""),
        retryAttempt = 2,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.Stop)
  }

  @Test
  fun planCommandRetryDoesNotTreatSessionStartHookOutputAsTransientBusy(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = planCommandDispatch()
    val outputText = buildString {
      appendLine("Running SessionStart hook:")
      repeat(12) { lineIndex ->
        appendLine("hook output $lineIndex")
      }
    }

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation(outputText),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
  }

  @Test
  fun planCommandRetryDoesNotTreatQueueHintOutputAsTransientBusy(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = planCommandDispatch()
    val outputText = listOf(
      "Working (0s - esc to interrupt)",
      "",
      "",
      "> Ask Codex to do anything",
      "",
      "  tab to queue message - Plan mode",
    ).joinToString(separator = "\n")

    assertThat(
      CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(
        file = TestBehaviorFile(),
        dispatch = dispatch,
        observation = observation(outputText),
        retryAttempt = 0,
      )
    ).isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
  }
}

private fun observation(outputText: String, recentOutputTail: String = ""): AgentChatInitialMessageSendObservation {
  return AgentChatInitialMessageSendObservation(outputText = outputText, recentOutputTail = recentOutputTail)
}

private fun planCommandDispatch(): TestDispatch {
  return TestDispatch(
    action = AgentInitialMessageDispatchAction.SEND_TEXT,
    completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
  )
}

private data class TestBehaviorFile(
  override val provider: AgentSessionProvider? = AgentSessionProvider.CODEX,
  override val isPendingThread: Boolean = false,
  override val subAgentId: String? = null,
  override val pendingFirstInputAtMs: Long? = null,
  override val threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
) : AgentChatBehaviorFile

private class TestBehaviorTerminalTab(
  private val recentOutputTail: String,
) : AgentChatBehaviorTerminalTab {
  override suspend fun readRecentOutputTail(): String = recentOutputTail
}

private data class TestDispatch(
  override val action: AgentInitialMessageDispatchAction,
  override val completionPolicy: AgentInitialMessageDispatchCompletionPolicy,
) : AgentChatInitialMessageDispatchContext
