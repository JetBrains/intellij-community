// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.toAgentSessionRefreshThreadSeeds
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

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
  fun neverOpenedThreadStaysReadyEvenWhenUpdatedAtIncreases() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicBackend { currentThreads })

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
    val source = ClaudeSessionSource(backend = dynamicBackend { currentThreads })

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
  fun activeThreadAutoAdvancesTrackerOnRefresh() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val source = ClaudeSessionSource(backend = dynamicBackend { currentThreads })

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")
      source.markThreadAsRead("s1", 1000L)

      // User is viewing thread s1 (tab focused).
      source.setActiveThreadId("s1")

      // Agent replies → updatedAt increases. Active thread: tracker auto-advances → READY.
      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L))
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)

      // User switches away → clear active. Agent replies → updatedAt increases → UNREAD.
      source.setActiveThreadId(null)
      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 3000L))
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.UNREAD)
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
