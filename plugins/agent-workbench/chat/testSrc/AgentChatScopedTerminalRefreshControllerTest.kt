// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Long poll interval used in tests that do not exercise the poll. Keeps the controller's
// rolloutPollJob effectively dormant so it does not race with the assertion paths.
private const val DISABLED_POLL_INTERVAL_MS: Long = 60_000L

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
      rolloutPollIntervalMs = DISABLED_POLL_INTERVAL_MS,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CLAUDE, "/work/project", null, null))
    }
  }

  @Test
  fun debouncesTerminalOutputIntoScopedRefreshWithoutActivityHint() = runBlocking(Dispatchers.Default) {
    val outputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = outputChanges,
      sessionState = MutableStateFlow(TerminalViewSessionState.NotStarted),
      parentScope = this,
      debounceMs = 25L,
      rolloutPollIntervalMs = DISABLED_POLL_INTERVAL_MS,
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
  fun terminalTerminationEmitsScopedRefresh() = runBlocking(Dispatchers.Default) {
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.NotStarted)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CLAUDE,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = DISABLED_POLL_INTERVAL_MS,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      sessionState.value = TerminalViewSessionState.Terminated

      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CLAUDE, "/work/project", null, null))
    }
  }

  @Test
  fun rolloutPollEmitsScopedRefreshWhileSessionIsRunning() = runBlocking(Dispatchers.Default) {
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = 50L,
      emitInitialRefresh = false,
      threadId = "codex-thread",
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      // Expect a handful of poll-driven refreshes within a generous window.
      val collected = ArrayList<RefreshSignal>()
      withTimeout(5.seconds) {
        repeat(3) {
          collected.add(signals.take())
        }
      }

      assertThat(collected).hasSizeGreaterThanOrEqualTo(3)
      assertThat(collected).allMatch { it == RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "codex-thread", null) }
    }
  }

  @Test
  fun rolloutPollStopsWhenSessionLeavesRunning() = runBlocking(Dispatchers.Default) {
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = 50L,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      // Drain the first poll-driven signal so we know the loop is running.
      withTimeout(5.seconds) { signals.take() }
      // Transition away from Running; the termination signal also fires for Terminated.
      sessionState.value = TerminalViewSessionState.Terminated
      // Drain any pending signals (any extra polls in-flight plus the one-shot termination signal).
      delay(200.milliseconds)
      signals.clear()
      // Now wait significantly longer than the poll interval and assert no further refreshes arrive.
      val extra = signals.poll(500, TimeUnit.MILLISECONDS)
      assertThat(extra).isNull()
    }
  }

  @Test
  fun rolloutPollDoesNotFireInNotStartedState() = runBlocking(Dispatchers.Default) {
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.NotStarted)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = 50L,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      // Wait several poll intervals. The poll is gated on Running, so nothing should arrive.
      val received = signals.poll(400, TimeUnit.MILLISECONDS)
      assertThat(received).isNull()

      // After flipping to Running the poll should arm and emit.
      sessionState.value = TerminalViewSessionState.Running
      withTimeout(5.seconds) { signals.take() }
    }
  }

  @Test
  fun rolloutPollReArmsOnRunningAfterTerminated() = runBlocking(Dispatchers.Default) {
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = 50L,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      withTimeout(5.seconds) { signals.take() }
      sessionState.value = TerminalViewSessionState.Terminated
      // Drain the one-shot terminated refresh.
      withTimeout(5.seconds) { signals.take() }
      // Re-enter Running and expect the poll to re-arm.
      sessionState.value = TerminalViewSessionState.Running
      withTimeout(5.seconds) { signals.take() }
    }
  }

  @Test
  fun rolloutPollCancelsOnDispose() = runBlocking(Dispatchers.Default) {
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    val controller = AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = 50L,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    )
    withTimeout(5.seconds) { signals.take() }
    controller.dispose()
    // Allow any in-flight emissions to drain.
    delay(150.milliseconds)
    signals.clear()
    val extra = signals.poll(400, TimeUnit.MILLISECONDS)
    assertThat(extra).isNull()
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
