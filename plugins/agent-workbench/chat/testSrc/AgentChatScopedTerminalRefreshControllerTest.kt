// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AGENT_SESSION_OPTIMISTIC_ACTIVITY_HINTS_PROPERTY
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.testFramework.junit5.SystemProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingQueue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AgentChatScopedTerminalRefreshControllerTest {
  @Test
  fun scopedRefreshThreadIdUsesParentSessionIdForSubAgentTabs() {
    val file = AgentChatVirtualFile(
      projectPath = "/work/project",
      threadIdentity = "codex:parent-thread",
      shellCommand = listOf("codex", "resume", "sub-agent-thread"),
      threadId = "sub-agent-thread",
      threadTitle = "Sub Agent",
      subAgentId = "sub-agent-thread",
    )

    assertThat(resolveAgentChatScopedRefreshThreadId(file)).isEqualTo("parent-thread")
  }

  @Test
  fun emitsInitialScopedRefreshWhenAttached() = runBlocking(Dispatchers.Default) {
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CLAUDE,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = MutableStateFlow(TerminalViewSessionState.NotStarted),
      parentScope = this,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CLAUDE, "/work/project", null, null))
    }
  }

  @Test
  fun debouncesTerminalOutputIntoScopedRefreshWithoutActivityHintByDefault() = runBlocking(Dispatchers.Default) {
    val outputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = outputChanges,
      sessionState = MutableStateFlow(TerminalViewSessionState.Running),
      parentScope = this,
      debounceMs = 25L,
      emitInitialRefresh = false,
      threadId = "codex-thread",
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      delay(50.milliseconds)
      outputChanges.emit(Unit)
      outputChanges.emit(Unit)

      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "codex-thread", null))
      assertThat(signals).isEmpty()
    }
  }

  @Test
  @SystemProperty(propertyKey = AGENT_SESSION_OPTIMISTIC_ACTIVITY_HINTS_PROPERTY, propertyValue = "true")
  fun debouncesTerminalOutputIntoScopedRefreshWithActivityHintWhenEnabled() = runBlocking(Dispatchers.Default) {
    val outputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = outputChanges,
      sessionState = MutableStateFlow(TerminalViewSessionState.Running),
      parentScope = this,
      debounceMs = 25L,
      emitInitialRefresh = false,
      threadId = "codex-thread",
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      delay(50.milliseconds)
      outputChanges.emit(Unit)
      outputChanges.emit(Unit)

      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CODEX,
                                                 "/work/project",
                                                 "codex-thread",
                                                 AgentThreadActivity.PROCESSING))
      assertThat(signals).isEmpty()
    }
  }

  @Test
  fun terminalTerminationEmitsScopedRefresh() = runBlocking(Dispatchers.Default) {
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CLAUDE,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = sessionState,
      parentScope = this,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      sessionState.value = TerminalViewSessionState.Terminated

      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CLAUDE, "/work/project", null, null))
    }
  }
}

private data class RefreshSignal(
  val provider: AgentSessionProvider,
  val projectPath: String,
  val threadId: String?,
  val activityHint: AgentThreadActivity?,
)

private suspend inline fun AgentChatScopedTerminalRefreshController.use(block: suspend (AgentChatScopedTerminalRefreshController) -> Unit) {
  try {
    block(this)
  }
  finally {
    dispose()
  }
}
