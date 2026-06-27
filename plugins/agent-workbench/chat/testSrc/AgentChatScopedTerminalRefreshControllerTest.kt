// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionActiveThreadUpdateSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.openapi.project.Project
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
  fun activeThreadUpdateResolverSkipsUnsupportedSource() {
    val calls = AtomicInteger()

    val updateEvents = resolveAgentChatActiveThreadUpdateEvents(
      sessionSource = TestAgentSessionSource(),
      projectPath = "/work/project",
    )

    assertThat(updateEvents).isNull()
    assertThat(calls.get()).isZero()
  }

  @Test
  fun activeThreadUpdateResolverUsesSupportedSource() {
    val fileChanges = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 16)
    val watchRequests = LinkedBlockingQueue<Pair<String, String>>()
    val updateEvents = checkNotNull(resolveAgentChatActiveThreadUpdateEvents(
      sessionSource = TestAgentSessionActiveThreadUpdateSource(
        activeThreadUpdateEventsProvider = { path, threadId ->
          watchRequests.add(path to threadId)
          fileChanges
        },
      ),
      projectPath = "/work/project",
    ))

    assertThat(updateEvents("thread-a")).isSameAs(fileChanges)
    assertThat(watchRequests.take()).isEqualTo("/work/project" to "thread-a")
  }

  @Test
  fun emitsInitialScopedRefreshWhenAttached() = runBlocking(Dispatchers.Default) {
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.from("claude"),
      projectPath = "/work/project",
      sessionState = MutableStateFlow(TerminalViewSessionState.NotStarted),
      parentScope = this,
      notifyRefresh = { provider, path, threadId, activityReport -> signals.add(RefreshSignal(provider, path, threadId, activityReport)) },
    ).use {
      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.from("claude"), "/work/project", null, null))
    }
  }

  @Test
  fun terminalTerminationEmitsScopedRefresh() = runBlocking(Dispatchers.Default) {
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.NotStarted)
    val signals = LinkedBlockingQueue<RefreshSignal>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.from("claude"),
      projectPath = "/work/project",
      sessionState = sessionState,
      parentScope = this,
      emitInitialRefresh = false,
      notifyRefresh = { provider, path, threadId, activityReport -> signals.add(RefreshSignal(provider, path, threadId, activityReport)) },
    ).use {
      sessionState.value = TerminalViewSessionState.Terminated

      val signal = withTimeout(5.seconds) { signals.take() }

      assertThat(signal).isEqualTo(RefreshSignal(AgentSessionProvider.from("claude"), "/work/project", null, null))
    }
  }

  @Test
  fun activeThreadFileChangeEmitsScopedRefreshWhileRunning() = runBlocking(Dispatchers.Default) {
    val update = activeUpdate(threadId = "thread-a")
    val fileChanges = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 16)
    val watchRequests = LinkedBlockingQueue<String>()
    val updates = LinkedBlockingQueue<AgentSessionSourceUpdateEvent>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.from("codex"),
      projectPath = "/work/project",
      sessionState = MutableStateFlow(TerminalViewSessionState.Running),
      parentScope = this,
      emitInitialRefresh = false,
      threadId = "thread-a",
      activeThreadIdProvider = { "thread-a" },
      activeThreadUpdateEvents = { threadId ->
        watchRequests.add(threadId)
        fileChanges
      },
      notifyUpdate = { _, updateEvent -> updates.add(updateEvent) },
    ).use {
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")

      fileChanges.emit(update)

      assertThat(withTimeout(5.seconds) { updates.take() }).isEqualTo(update)
    }
  }

  @Test
  fun activeThreadFileWatchRetriesSameThreadAfterCompletedWatch() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val update = activeUpdate(threadId = "thread-a")
    val retryFileChanges = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 16)
    val watchAttempts = AtomicInteger()
    val watchRequests = LinkedBlockingQueue<String>()
    val updates = LinkedBlockingQueue<AgentSessionSourceUpdateEvent>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.from("codex"),
      projectPath = "/work/project",
      inputChanges = inputChanges,
      sessionState = MutableStateFlow(TerminalViewSessionState.Running),
      parentScope = this,
      emitInitialRefresh = false,
      threadId = "thread-a",
      activeThreadIdProvider = { "thread-a" },
      activeThreadUpdateEvents = { threadId ->
        watchRequests.add(threadId)
        if (watchAttempts.incrementAndGet() == 1) emptyFlow() else retryFileChanges
      },
      notifyUpdate = { _, updateEvent -> updates.add(updateEvent) },
    ).use {
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")

      delay(50.milliseconds)
      inputChanges.emit(Unit)

      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")
      inputChanges.emit(Unit)
      assertThat(watchRequests.poll(300, TimeUnit.MILLISECONDS)).isNull()

      retryFileChanges.emit(update)

      assertThat(withTimeout(5.seconds) { updates.take() }).isEqualTo(update)
    }
  }

  @Test
  fun activeThreadFileChangeStopsWhenSessionLeavesRunning() = runBlocking(Dispatchers.Default) {
    val update = activeUpdate(threadId = "thread-a")
    val fileChanges = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 16)
    val watchRequests = LinkedBlockingQueue<String>()
    val sessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.NotStarted)
    val signals = LinkedBlockingQueue<RefreshSignal>()
    val updates = LinkedBlockingQueue<AgentSessionSourceUpdateEvent>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.from("codex"),
      projectPath = "/work/project",
      sessionState = sessionState,
      parentScope = this,
      emitInitialRefresh = false,
      threadId = "thread-a",
      activeThreadIdProvider = { "thread-a" },
      activeThreadUpdateEvents = { threadId ->
        watchRequests.add(threadId)
        fileChanges
      },
      notifyRefresh = { provider, path, threadId, activityReport -> signals.add(RefreshSignal(provider, path, threadId, activityReport)) },
      notifyUpdate = { _, updateEvent -> updates.add(updateEvent) },
    ).use {
      assertThat(watchRequests.poll(200, TimeUnit.MILLISECONDS)).isNull()

      sessionState.value = TerminalViewSessionState.Running
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")
      fileChanges.emit(update)
      assertThat(withTimeout(5.seconds) { updates.take() }).isEqualTo(update)

      sessionState.value = TerminalViewSessionState.Terminated
      assertThat(withTimeout(5.seconds) { signals.take() })
        .isEqualTo(RefreshSignal(AgentSessionProvider.from("codex"), "/work/project", "thread-a", null))
      signals.clear()

      delay(100.milliseconds)
      fileChanges.emit(update)

      assertThat(updates.poll(300, TimeUnit.MILLISECONDS)).isNull()
    }
  }

  @Test
  fun activeThreadFileWatchRestartsAfterTerminalActivityChangesActiveThread() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val activeThreadId = AtomicReference("thread-a")
    val fileChangesByThreadId = ConcurrentHashMap<String, MutableSharedFlow<AgentSessionSourceUpdateEvent>>()
    val watchRequests = LinkedBlockingQueue<String>()
    val updates = LinkedBlockingQueue<AgentSessionSourceUpdateEvent>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.from("codex"),
      projectPath = "/work/project",
      inputChanges = inputChanges,
      sessionState = MutableStateFlow(TerminalViewSessionState.Running),
      parentScope = this,
      emitInitialRefresh = false,
      threadId = "thread-a",
      activeThreadIdProvider = { activeThreadId.get() },
      activeThreadUpdateEvents = { threadId ->
        val fileChanges = fileChangesByThreadId.computeIfAbsent(threadId) { MutableSharedFlow(extraBufferCapacity = 16) }
        watchRequests.add(threadId)
        fileChanges
      },
      notifyUpdate = { _, updateEvent -> updates.add(updateEvent) },
    ).use {
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")
      val firstUpdate = activeUpdate(threadId = "thread-a")
      fileChangesByThreadId["thread-a"]!!.emit(firstUpdate)
      assertThat(withTimeout(5.seconds) { updates.take() }).isEqualTo(firstUpdate)
      updates.clear()

      activeThreadId.set("thread-b")
      inputChanges.emit(Unit)
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-b")

      fileChangesByThreadId["thread-a"]!!.emit(firstUpdate)
      assertThat(updates.poll(300, TimeUnit.MILLISECONDS)).isNull()

      val secondUpdate = activeUpdate(threadId = "thread-b")
      fileChangesByThreadId["thread-b"]!!.emit(secondUpdate)
      assertThat(withTimeout(5.seconds) { updates.take() }).isEqualTo(secondUpdate)
    }
  }

  @Test
  fun activeThreadFileWatchStartsAfterActiveThreadIdAppears() = runBlocking(Dispatchers.Default) {
    val inputChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val activeThreadId = AtomicReference<String?>(null)
    val update = activeUpdate(threadId = "thread-a")
    val fileChanges = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 16)
    val watchRequests = LinkedBlockingQueue<String>()
    val updates = LinkedBlockingQueue<AgentSessionSourceUpdateEvent>()

    AgentChatScopedTerminalRefreshController(
      provider = AgentSessionProvider.from("codex"),
      projectPath = "/work/project",
      inputChanges = inputChanges,
      sessionState = MutableStateFlow(TerminalViewSessionState.Running),
      parentScope = this,
      emitInitialRefresh = false,
      activeThreadIdProvider = { activeThreadId.get() },
      activeThreadUpdateEvents = { threadId ->
        watchRequests.add(threadId)
        fileChanges
      },
      notifyUpdate = { _, updateEvent -> updates.add(updateEvent) },
    ).use {
      assertThat(watchRequests.poll(300, TimeUnit.MILLISECONDS)).isNull()

      activeThreadId.set("thread-a")
      inputChanges.emit(Unit)
      assertThat(withTimeout(5.seconds) { watchRequests.take() }).isEqualTo("thread-a")

      fileChanges.emit(update)
      assertThat(withTimeout(5.seconds) { updates.take() }).isEqualTo(update)
    }
  }

}

private data class RefreshSignal(
  val provider: AgentSessionProvider,
  val projectPath: String,
  val threadId: String?,
  val activityReport: AgentThreadActivityReport?,
)

private fun activeUpdate(threadId: String): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent.activityChanged(
    scopedPaths = setOf("/work/project"),
    activityUpdatesByThreadId = mapOf(threadId to AgentSessionThreadActivityUpdate(AgentThreadActivityReport(AgentThreadActivity.PROCESSING))),
  )
}

private open class TestAgentSessionSource : AgentSessionSource {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.from("codex")

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> = emptyList()
}

private class TestAgentSessionActiveThreadUpdateSource(
  private val activeThreadUpdateEventsProvider: (String, String) -> Flow<AgentSessionSourceUpdateEvent>,
) : TestAgentSessionSource(), AgentSessionActiveThreadUpdateSource {
  override fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
    return activeThreadUpdateEventsProvider(path, threadId)
  }
}

private suspend inline fun AgentChatScopedTerminalRefreshController.use(block: suspend (AgentChatScopedTerminalRefreshController) -> Unit) {
  try {
    block(this)
  }
  finally {
    dispose()
  }
}
