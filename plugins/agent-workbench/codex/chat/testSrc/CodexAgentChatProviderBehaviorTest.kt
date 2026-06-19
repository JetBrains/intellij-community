// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

import com.intellij.agent.workbench.chat.AgentChatBehaviorFile
import com.intellij.agent.workbench.chat.AgentChatBehaviorTerminalTab
import com.intellij.agent.workbench.chat.AgentChatInitialMessageDispatchContext
import com.intellij.agent.workbench.chat.AgentChatInitialMessageRetryDecision
import com.intellij.agent.workbench.chat.AgentChatProviderBehaviors
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
  @RegistryKey(key = CODEX_TUI_PATCH_FOLDING_REGISTRY_KEY, value = "true")
  fun patchFoldingInstallCheckHonorsRegistryKey() {
    assertThat(CodexAgentChatProviderBehavior.shouldInstallPatchFolding()).isTrue()
  }

  @Test
  fun planModeEnsureIsSatisfiedWhenCodexPlanModeIsVisible(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = ensurePlanModeDispatch()
    val tab = TestBehaviorTerminalTab(recentOutputTail = "gpt-5.3-codex medium Plan mode (shift+tab to cycle)")

    assertThat(CodexAgentChatProviderBehavior.isInitialMessageDispatchAlreadySatisfied(tab, dispatch)).isTrue()
  }

  @Test
  fun planModeEnsureRetriesUntilVisibleAndThenStops(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = ensurePlanModeDispatch()
    val file = TestBehaviorFile()

    assertThat(CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(file, dispatch, "Default mode", retryAttempt = 0))
      .isInstanceOf(AgentChatInitialMessageRetryDecision.RetryWithoutReadiness::class.java)
    assertThat(CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(file, dispatch, "Plan mode", retryAttempt = 0))
      .isSameAs(AgentChatInitialMessageRetryDecision.PROCEED)
    assertThat(CodexAgentChatProviderBehavior.afterInitialMessageSendObservation(file, dispatch, "Default mode", retryAttempt = 5))
      .isSameAs(AgentChatInitialMessageRetryDecision.Stop)
  }

  @Test
  fun planModeRetryWaitsWhileThreadOrTerminalTailIsUnsafe(): Unit = kotlinx.coroutines.runBlocking {
    val dispatch = ensurePlanModeDispatch()

    assertThat(
      CodexAgentChatProviderBehavior.beforeInitialMessageSend(
        file = TestBehaviorFile(threadActivity = AgentThreadActivity.PROCESSING),
        tab = TestBehaviorTerminalTab(recentOutputTail = ""),
        dispatch = dispatch,
        retryAttempt = 0,
      )
    ).isInstanceOf(AgentChatInitialMessageRetryDecision.RetryWithoutReadiness::class.java)

    assertThat(
      CodexAgentChatProviderBehavior.beforeInitialMessageSend(
        file = TestBehaviorFile(threadActivity = AgentThreadActivity.READY),
        tab = TestBehaviorTerminalTab(recentOutputTail = "Booting MCP server: idea"),
        dispatch = dispatch,
        retryAttempt = 0,
      )
    ).isInstanceOf(AgentChatInitialMessageRetryDecision.RetryWithoutReadiness::class.java)
  }
}

private fun ensurePlanModeDispatch(): TestDispatch {
  return TestDispatch(
    action = AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
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
  override val coroutineScope: CoroutineScope
    get() = error("Not used in behavior tests")
  override val sessionState: StateFlow<TerminalViewSessionState> = MutableStateFlow(TerminalViewSessionState.Running)
  override val terminalView: TerminalView? = null

  override suspend fun readRecentOutputTail(): String = recentOutputTail
}

private data class TestDispatch(
  override val action: AgentInitialMessageDispatchAction,
  override val completionPolicy: AgentInitialMessageDispatchCompletionPolicy,
) : AgentChatInitialMessageDispatchContext
