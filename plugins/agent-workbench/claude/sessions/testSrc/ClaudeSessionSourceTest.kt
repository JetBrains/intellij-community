// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.agent.workbench.sessions.core.providers.toAgentSessionRefreshThreadSeeds
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.math.BigDecimal
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class ClaudeSessionSourceTest {
  @Test
  fun allThreadsAreReadyOnInitialLoad() {
    val threads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
      ClaudeBackendThread(id = "s2", title = "Session 2", updatedAt = 2000L),
    )
    val source = ClaudeSessionSource(backend = staticBackend(threads))

    val result = runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")
    }

    assertThat(result).hasSize(2)
    assertThat(result).allMatch { it.activity == AgentThreadActivity.READY }
  }

  @Test
  fun sumsCostsAcrossClaudeUsageSnapshots() {
    val source = ClaudeSessionSource(
      backend = staticBackend(
        listOf(
          ClaudeBackendThread(
            id = "s1",
            title = "Session 1",
            updatedAt = 1000L,
            usageSnapshots = listOf(
              AgentSessionUsageSnapshot(modelId = "claude-opus-4-7"),
              AgentSessionUsageSnapshot(modelId = "claude-haiku-4-5-20251001"),
            ),
          ),
        )
      ),
      calculateCost = { usage ->
        when (usage.modelId) {
          "claude-opus-4-7" -> AgentSessionCost(amountUsd = BigDecimal("1.25"), kind = AgentSessionCostKind.ESTIMATED)
          "claude-haiku-4-5-20251001" -> AgentSessionCost(amountUsd = BigDecimal("0.50"), kind = AgentSessionCostKind.ESTIMATED)
          else -> AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
        }
      },
    )

    val thread = runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any").single()
    }

    assertThat(thread.cost).isNull()

    val loadedCost = runBlocking(Dispatchers.Default) {
      source.loadThreadCosts(path = "/any", threads = listOf(thread)).getValue(thread.id)
    }

    assertThat(loadedCost?.kind).isEqualTo(AgentSessionCostKind.ESTIMATED)
    assertThat(loadedCost?.amountUsd).isEqualByComparingTo(BigDecimal("1.75"))
    assertThat(loadedCost?.matchedModelId).isNull()
  }

  @Test
  fun neverOpenedThreadStaysReadyEvenWhenUpdatedAtIncreases() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicRefreshBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)

      // updatedAt increases but thread was never opened → stays READY.
      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L))
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun threadBecomesUnreadWhenUpdatedAfterBeingOpened() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicRefreshBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")

      // User opens the thread.
      source.markThreadAsRead("s1", 1000L)
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)

      // Agent replies while user is elsewhere → UNREAD.
      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L))
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.UNREAD)
    }
  }

  @Test
  fun threadBecomesReadyAfterMarkAsRead() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")
      source.markThreadAsRead("s1", 1000L)

      // Agent replies → UNREAD.
      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L))
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.UNREAD)

      // User returns → mark as read → READY.
      source.markThreadAsRead("s1", 2000L)
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun processingAlwaysWinsOverUnread() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")
      source.markThreadAsRead("s1", 1000L)

      // Backend says PROCESSING with increased updatedAt → PROCESSING wins.
      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L, activity = ClaudeSessionActivity.PROCESSING),
      )
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun needsInputAlwaysWinsOverUnread() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")
      source.markThreadAsRead("s1", 1000L)

      // Backend says NEEDS_INPUT with increased updatedAt → NEEDS_INPUT wins.
      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L, activity = ClaudeSessionActivity.NEEDS_INPUT),
      )
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.NEEDS_INPUT)
    }
  }

  @Test
  fun staleMarkAsReadDoesNotDowngradeTracker() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")

      // User reads at updatedAt=2000.
      source.markThreadAsRead("s1", 2000L)

      // A stale markAsRead at 1000 must not downgrade the tracker.
      source.markThreadAsRead("s1", 1000L)

      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L))
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun activeThreadCompletionBecomesUnreadAfterProcessing() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicRefreshBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")
      source.markThreadAsRead("s1", 1000L)

      // User is viewing thread s1 (tab focused).
      source.setActiveThreadId("s1")

      // Agent starts working while the user is viewing the thread.
      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L, activity = ClaudeSessionActivity.PROCESSING)
      )
      assertThat(source.refreshThreads(refreshRequest()).partialThreadsByPath.getValue("/any").single().activity)
        .isEqualTo(AgentThreadActivity.PROCESSING)

      // Completion should still be surfaced as Done/UNREAD even if the thread remains selected.
      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 3000L))
      assertThat(source.refreshThreads(refreshRequest()).partialThreadsByPath.getValue("/any").single().activity)
        .isEqualTo(AgentThreadActivity.UNREAD)

      // User switches away → clear active. A later update still becomes UNREAD.
      source.setActiveThreadId(null)
      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 4000L))
      assertThat(source.refreshThreads(refreshRequest()).partialThreadsByPath.getValue("/any").single().activity)
        .isEqualTo(AgentThreadActivity.UNREAD)
    }
  }

  @Test
  fun scopedReadyRefreshBecomesUnreadWhenUpdatedAfterInitialLoad() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicRefreshBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)

      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L))
      assertThat(source.refreshThreads(refreshRequest()).partialThreadsByPath.getValue("/any").single().activity)
        .isEqualTo(AgentThreadActivity.UNREAD)

      assertThat(source.refreshThreads(refreshRequest()).partialThreadsByPath.getValue("/any").single().activity)
        .isEqualTo(AgentThreadActivity.UNREAD)

      source.markThreadAsRead("s1", 2000L)
      assertThat(source.refreshThreads(refreshRequest()).partialThreadsByPath.getValue("/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun scopedAwaitingAssistantReadyRefreshStaysReadyWhenUpdatedAfterInitialLoad() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicRefreshBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)

      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L, awaitingAssistantTurn = true)
      )
      assertThat(source.refreshThreads(refreshRequest()).partialThreadsByPath.getValue("/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun updateEventsMergeBackendAndReadState() {
    val backendUpdates = MutableSharedFlow<Unit>(replay = 1)
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = emptyList()
      override val updates get() = backendUpdates
    }
    val source = ClaudeSessionSource(backend = backend)

    backendUpdates.tryEmit(Unit)

    runBlocking(Dispatchers.Default) {
      val result = withTimeoutOrNull(2.seconds) { source.updateEvents.first() }
      assertThat(result?.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
    }
  }

  @Test
  fun updateEventsPreserveBackendScope() {
    val backendUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1)
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = emptyList()
      override val sessionUpdates get() = backendUpdates
    }
    val source = ClaudeSessionSource(backend = backend)

    backendUpdates.tryEmit(
      AgentSessionSourceUpdateEvent(
        type = AgentSessionSourceUpdate.THREADS_CHANGED,
        scopedPaths = setOf("/work/project"),
        threadIds = setOf("session-1"),
      )
    )

    runBlocking(Dispatchers.Default) {
      val result = withTimeoutOrNull(2.seconds) { source.updateEvents.first() }
      assertThat(result?.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
      assertThat(result?.scopedPaths).containsExactly("/work/project")
      assertThat(result?.threadIds).containsExactly("session-1")
    }
  }

  @Test
  fun markThreadAsReadEmitsThreadScopedUpdateOnlyWhenReadTimestampAdvances() {
    val source = ClaudeSessionSource(backend = staticBackend(emptyList()))

    runBlocking(Dispatchers.Default) {
      val events = Channel<AgentSessionSourceUpdateEvent>(Channel.UNLIMITED)
      val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        source.updateEvents.collect { event -> events.send(event) }
      }

      try {
        delay(50.milliseconds)
        source.markThreadAsRead("s1", 1000L)

        val event = withTimeoutOrNull(2.seconds) { events.receive() }
        assertThat(event?.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
        assertThat(event?.threadIds).containsExactly("s1")

        source.markThreadAsRead("s1", 1000L)
        source.markThreadAsRead("s1", 999L)

        assertThat(withTimeoutOrNull(200.milliseconds) { events.receive() }).isNull()
      }
      finally {
        collector.cancelAndJoin()
      }
    }
  }

  @Test
  fun archivedThreadsAreHiddenFromActiveList() {
    val source = ClaudeSessionSource(
      backend = staticBackend(
        listOf(
          ClaudeBackendThread(id = "visible", title = "Visible", updatedAt = 2_000L),
          ClaudeBackendThread(id = "archived", title = "Archived", archived = true, updatedAt = 1_000L),
        )
      )
    )

    val result = runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")
    }

    assertThat(result.map { it.id }).containsExactly("visible")
  }

  @Test
  fun archivedThreadsAreReturnedFromArchivedList() {
    val source = ClaudeSessionSource(
      backend = staticBackend(
        listOf(
          ClaudeBackendThread(id = "visible", title = "Visible", updatedAt = 2_000L),
          ClaudeBackendThread(
            id = "archived",
            title = "Archived",
            archived = true,
            updatedAt = 1_000L,
            activity = ClaudeSessionActivity.PROCESSING,
          ),
        )
      )
    )

    val result = runBlocking(Dispatchers.Default) {
      source.listArchivedThreadsFromClosedProject(path = "/any")
    }

    assertThat(result.map { it.id }).containsExactly("archived")
    assertThat(result.single().archived).isTrue()
    assertThat(result.single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
  }

  @Test
  fun archivedThreadsDoNotProduceRefreshHintCandidates() {
    val source = ClaudeSessionSource(
      backend = staticBackend(
        listOf(
          ClaudeBackendThread(id = "existing", title = "Existing", updatedAt = 3_000L),
          ClaudeBackendThread(id = "archived", title = "Archived", archived = true, updatedAt = 2_000L),
          ClaudeBackendThread(id = "new-visible", title = "New visible", updatedAt = 1_000L),
        )
      )
    )

    val hints = runBlocking(Dispatchers.Default) {
      source.prefetchRefreshHints(
        paths = listOf("/any"),
        refreshThreadSeedsByPath = mapOf("/any" to setOf("existing").toAgentSessionRefreshThreadSeeds()),
      )
    }

    assertThat(hints.getValue("/any").rebindCandidates.map { it.threadId }).containsExactly("new-visible")
  }

  @Test
  fun visibleKnownThreadsProduceRefreshActivityHints() {
    val source = ClaudeSessionSource(
      backend = staticBackend(
        listOf(
          ClaudeBackendThread(id = "known-processing",
                              title = "Known processing",
                              updatedAt = 3_000L,
                              activity = ClaudeSessionActivity.PROCESSING),
          ClaudeBackendThread(id = "known-needs-input",
                              title = "Known needs input",
                              updatedAt = 2_500L,
                              activity = ClaudeSessionActivity.NEEDS_INPUT),
          ClaudeBackendThread(id = "known-ready", title = "Known ready", updatedAt = 2_000L),
          ClaudeBackendThread(id = "new-visible", title = "New visible", updatedAt = 1_000L),
        )
      )
    )

    val hints = runBlocking(Dispatchers.Default) {
      source.prefetchRefreshHints(
        paths = listOf("/any"),
        refreshThreadSeedsByPath = mapOf(
          "/any" to setOf("known-processing", "known-needs-input", "known-ready").toAgentSessionRefreshThreadSeeds()
        ),
      )
    }

    val pathHints = hints.getValue("/any")
    assertThat(pathHints.activityUpdatesByThreadId.mapValues { (_, update) -> update.activityReport.rowActivity }).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "known-processing" to AgentThreadActivity.PROCESSING,
        "known-needs-input" to AgentThreadActivity.NEEDS_INPUT,
        "known-ready" to AgentThreadActivity.READY,
      )
    )
    assertThat(pathHints.activityUpdatesByThreadId["known-processing"]).isEqualTo(
      AgentSessionThreadActivityUpdate(
        activityReport = AgentThreadActivityReport(AgentThreadActivity.PROCESSING),
        updatesChromeActivity = false,
      )
    )
    assertThat(pathHints.rebindCandidates.map { it.threadId }).containsExactly("new-visible")
  }


}

private fun staticBackend(threads: List<ClaudeBackendThread>): ClaudeSessionBackend {
  return object : ClaudeSessionBackend {
    override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = threads
  }
}

private fun dynamicBackend(provider: () -> List<ClaudeBackendThread>): ClaudeSessionBackend {
  return object : ClaudeSessionBackend {
    override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = provider()
  }
}

private fun dynamicRefreshBackend(provider: () -> List<ClaudeBackendThread>): ClaudeSessionBackend {
  return object : ClaudeSessionBackend {
    override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = provider()

    override suspend fun refreshThreads(
      path: String,
      threadIds: Set<String>,
      openProject: Project?,
    ): ClaudeBackendThreadRefreshResult {
      return ClaudeBackendThreadRefreshResult(
        threads = provider().filter { it.id in threadIds },
        isComplete = false,
      )
    }
  }
}

private fun refreshRequest(): AgentSessionSourceRefreshRequest {
  return AgentSessionSourceRefreshRequest(
    paths = listOf("/any"),
    threadIds = setOf("s1"),
    updateEvent = AgentSessionSourceUpdateEvent(
      type = AgentSessionSourceUpdate.THREADS_CHANGED,
      scopedPaths = setOf("/any"),
      threadIds = setOf("s1"),
    ),
  )
}
