// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val LONG_DEBOUNCE_MS: Long = 60_000L
private const val TEST_POLL_INTERVAL_MS: Long = 40L
private const val TEST_POLL_ACTIVE_WINDOW_MS: Long = 120L

@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      sessionState.value = TerminalViewSessionState.Terminated

      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CLAUDE, "/work/project", null, null))
    }
  }

  @Test
  fun rolloutPollIsDisabledByDefault() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      inputChanges = inputChanges,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = TEST_POLL_INTERVAL_MS,
      rolloutPollActiveWindowMs = TEST_POLL_ACTIVE_WINDOW_MS,
      emitInitialRefresh = false,
      threadId = "codex-thread",
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      delay(50.milliseconds)
      inputChanges.emit(Unit)
      val signal = signals.poll(300, TimeUnit.MILLISECONDS)
      assertThat(signal).isNull()
    }
  }

  @Test
  fun activeThreadFileChangeEmitsScopedRefreshWhileRunning() = runBlocking(Dispatchers.Default) {
    val fileChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val watchRequests = LinkedBlockingQueue<String>()
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = MutableStateFlow(TerminalViewSessionState.Running),
      parentScope = this,
      emitInitialRefresh = false,
      threadId = "thread-a",
      activeThreadIdProvider = { "thread-a" },
      activeThreadFileChangeEvents = { threadId ->
        watchRequests.add(threadId)
        fileChanges
      },
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")

      fileChanges.emit(Unit)

      val signal = withTimeout(5.seconds) { signals.take() }
      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "thread-a", null))
    }
  }

  @Test
  fun activeThreadFileWatchRetriesSameThreadAfterCompletedWatch() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val retryFileChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val watchAttempts = AtomicInteger()
    val watchRequests = LinkedBlockingQueue<String>()
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      inputChanges = inputChanges,
      sessionState = MutableStateFlow(TerminalViewSessionState.Running),
      parentScope = this,
      emitInitialRefresh = false,
      threadId = "thread-a",
      activeThreadIdProvider = { "thread-a" },
      activeThreadFileChangeEvents = { threadId ->
        watchRequests.add(threadId)
        if (watchAttempts.incrementAndGet() == 1) emptyFlow() else retryFileChanges
      },
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")

      delay(50.milliseconds)
      inputChanges.emit(Unit)

      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")
      inputChanges.emit(Unit)
      assertThat(watchRequests.poll(300, TimeUnit.MILLISECONDS)).isNull()

      retryFileChanges.emit(Unit)

      val signal = withTimeout(5.seconds) { signals.take() }
      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "thread-a", null))
    }
  }

  @Test
  fun activeThreadFileChangeStopsWhenSessionLeavesRunning() = runBlocking(Dispatchers.Default) {
    val fileChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val watchRequests = LinkedBlockingQueue<String>()
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.NotStarted)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      sessionState = sessionState,
      parentScope = this,
      emitInitialRefresh = false,
      threadId = "thread-a",
      activeThreadIdProvider = { "thread-a" },
      activeThreadFileChangeEvents = { threadId ->
        watchRequests.add(threadId)
        fileChanges
      },
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      assertThat(watchRequests.poll(200, TimeUnit.MILLISECONDS)).isNull()

      sessionState.value = TerminalViewSessionState.Running
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")
      fileChanges.emit(Unit)
      assertThat(withTimeout(5.seconds) { signals.take() })
        .isEqualTo(RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "thread-a", null))

      sessionState.value = TerminalViewSessionState.Terminated
      assertThat(withTimeout(5.seconds) { signals.take() })
        .isEqualTo(RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "thread-a", null))
      signals.clear()

      delay(100.milliseconds)
      fileChanges.emit(Unit)

      assertThat(signals.poll(300, TimeUnit.MILLISECONDS)).isNull()
    }
  }

  @Test
  fun activeThreadFileWatchRestartsAfterTerminalActivityChangesActiveThread() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val activeThreadId = AtomicReference("thread-a")
    val fileChangesByThreadId = ConcurrentHashMap<String, MutableSharedFlow<Unit>>()
    val watchRequests = LinkedBlockingQueue<String>()
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      inputChanges = inputChanges,
      sessionState = MutableStateFlow(TerminalViewSessionState.Running),
      parentScope = this,
      emitInitialRefresh = false,
      threadId = "thread-a",
      activeThreadIdProvider = { activeThreadId.get() },
      activeThreadFileChangeEvents = { threadId ->
        val fileChanges = fileChangesByThreadId.computeIfAbsent(threadId) { MutableSharedFlow(extraBufferCapacity = 16) }
        watchRequests.add(threadId)
        fileChanges
      },
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")
      fileChangesByThreadId["thread-a"]!!.emit(Unit)
      assertThat(withTimeout(5.seconds) { signals.take() })
        .isEqualTo(RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "thread-a", null))
      signals.clear()

      activeThreadId.set("thread-b")
      inputChanges.emit(Unit)
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-b")

      fileChangesByThreadId["thread-a"]!!.emit(Unit)
      assertThat(signals.poll(300, TimeUnit.MILLISECONDS)).isNull()

      fileChangesByThreadId["thread-b"]!!.emit(Unit)
      assertThat(withTimeout(5.seconds) { signals.take() })
        .isEqualTo(RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "thread-a", null))
    }
  }

  @Test
  fun rolloutPollStartsAfterTerminalOutputWhileRunning() = runBlocking(Dispatchers.Default) {
    val outputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = outputChanges,
      sessionState = sessionState,
      parentScope = this,
      debounceMs = LONG_DEBOUNCE_MS,
      rolloutPollIntervalMs = TEST_POLL_INTERVAL_MS,
      rolloutPollActiveWindowMs = TEST_POLL_ACTIVE_WINDOW_MS,
      rolloutPollEnabled = true,
      emitInitialRefresh = false,
      threadId = "codex-thread",
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      delay(50.milliseconds)
      outputChanges.emit(Unit)

      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "codex-thread", null))
    }
  }

  @Test
  fun rolloutPollStartsAfterTerminalInputWhileRunning() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      inputChanges = inputChanges,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = TEST_POLL_INTERVAL_MS,
      rolloutPollActiveWindowMs = TEST_POLL_ACTIVE_WINDOW_MS,
      rolloutPollEnabled = true,
      emitInitialRefresh = false,
      threadId = "codex-thread",
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      delay(50.milliseconds)
      inputChanges.emit(Unit)

      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.CODEX, "/work/project", "codex-thread", null))
    }
  }

  @Test
  fun rolloutPollStopsWhenSessionLeavesRunning() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      inputChanges = inputChanges,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = TEST_POLL_INTERVAL_MS,
      rolloutPollActiveWindowMs = TEST_POLL_ACTIVE_WINDOW_MS,
      rolloutPollEnabled = true,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      delay(50.milliseconds)
      inputChanges.emit(Unit)
      withTimeout(5.seconds) { signals.take() }
      sessionState.value = TerminalViewSessionState.Terminated
      delay(200.milliseconds)
      signals.clear()
      val extra = signals.poll(500, TimeUnit.MILLISECONDS)
      assertThat(extra).isNull()
    }
  }

  @Test
  fun rolloutPollDoesNotFireInNotStartedState() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.NotStarted)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      inputChanges = inputChanges,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = TEST_POLL_INTERVAL_MS,
      rolloutPollActiveWindowMs = TEST_POLL_ACTIVE_WINDOW_MS,
      rolloutPollEnabled = true,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      delay(50.milliseconds)
      inputChanges.emit(Unit)
      val received = signals.poll(400, TimeUnit.MILLISECONDS)
      assertThat(received).isNull()

      sessionState.value = TerminalViewSessionState.Running
      inputChanges.emit(Unit)
      withTimeout(5.seconds) { signals.take() }
    }
  }

  @Test
  fun rolloutPollReArmsOnRunningAfterTerminated() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      inputChanges = inputChanges,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = TEST_POLL_INTERVAL_MS,
      rolloutPollActiveWindowMs = TEST_POLL_ACTIVE_WINDOW_MS,
      rolloutPollEnabled = true,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      delay(50.milliseconds)
      inputChanges.emit(Unit)
      withTimeout(5.seconds) { signals.take() }
      sessionState.value = TerminalViewSessionState.Terminated
      withTimeout(5.seconds) { signals.take() }
      sessionState.value = TerminalViewSessionState.Running
      inputChanges.emit(Unit)
      withTimeout(5.seconds) { signals.take() }
    }
  }

  @Test
  fun rolloutPollStopsAfterActivityWindowExpires() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      inputChanges = inputChanges,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = TEST_POLL_INTERVAL_MS,
      rolloutPollActiveWindowMs = TEST_POLL_ACTIVE_WINDOW_MS,
      rolloutPollEnabled = true,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    ).use {
      delay(50.milliseconds)
      inputChanges.emit(Unit)
      withTimeout(5.seconds) { signals.take() }

      delay((TEST_POLL_ACTIVE_WINDOW_MS + TEST_POLL_INTERVAL_MS * 2).milliseconds)
      signals.clear()
      val extra = signals.poll(300, TimeUnit.MILLISECONDS)
      assertThat(extra).isNull()
    }
  }

  @Test
  fun rolloutPollCancelsOnDispose() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.Running)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    val controller = AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.CODEX,
      projectPath = "/work/project",
      outputChanges = null,
      inputChanges = inputChanges,
      sessionState = sessionState,
      parentScope = this,
      rolloutPollIntervalMs = TEST_POLL_INTERVAL_MS,
      rolloutPollActiveWindowMs = TEST_POLL_ACTIVE_WINDOW_MS,
      rolloutPollEnabled = true,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityHint -> signals.add(RefreshSignal(provider, path, threadId, activityHint)) },
    )
    delay(50.milliseconds)
    inputChanges.emit(Unit)
    withTimeout(5.seconds) { signals.take() }
    controller.dispose()
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
