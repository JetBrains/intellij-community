// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class ClaudeSessionSourceTest {
  @Test
  fun allThreadsAreReadyOnFirstLoad() {
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
  fun threadBecomesUnreadWhenUpdatedAtIncreases() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = currentThreads
    }
    val source = ClaudeSessionSource(backend = backend)

    runBlocking(Dispatchers.Default) {
      // First load: seeds the read tracker.
      val initial = source.listThreadsFromClosedProject(path = "/any")
      assertThat(initial.single().activity).isEqualTo(AgentThreadActivity.READY)

      // Simulate new content: updatedAt increases.
      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L),
      )
      val refreshed = source.listThreadsFromClosedProject(path = "/any")
      assertThat(refreshed.single().activity).isEqualTo(AgentThreadActivity.UNREAD)
    }
  }

  @Test
  fun threadBecomesReadyAfterMarkAsRead() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = currentThreads
    }
    val source = ClaudeSessionSource(backend = backend)

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")

      // Bump updatedAt → UNREAD.
      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L),
      )
      val unread = source.listThreadsFromClosedProject(path = "/any")
      assertThat(unread.single().activity).isEqualTo(AgentThreadActivity.UNREAD)

      // Mark as read.
      source.markThreadAsRead("s1", 2000L)
      val readAgain = source.listThreadsFromClosedProject(path = "/any")
      assertThat(readAgain.single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun processingTakesPriorityOverUnread() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L, activity = ClaudeSessionActivity.PROCESSING),
    )
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = currentThreads
    }
    val source = ClaudeSessionSource(backend = backend)

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")

      // Even with increased updatedAt, PROCESSING wins.
      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L, activity = ClaudeSessionActivity.PROCESSING),
      )
      val result = source.listThreadsFromClosedProject(path = "/any")
      assertThat(result.single().activity).isEqualTo(AgentThreadActivity.PROCESSING)
    }
  }

  @Test
  fun staleMarkAsReadDoesNotDowngradeTracker() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = currentThreads
    }
    val source = ClaudeSessionSource(backend = backend)

    runBlocking(Dispatchers.Default) {
      source.listThreadsFromClosedProject(path = "/any")

      // New content: updatedAt increases to 2000.
      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L),
      )

      // User opens thread and sees the new content — marks as read at 2000.
      source.markThreadAsRead("s1", 2000L)
      assertThat(source.listThreadsFromClosedProject(path = "/any").single().activity)
        .isEqualTo(AgentThreadActivity.READY)

      // A second open with a stale snapshot (updatedAt=1000) must not downgrade the tracker.
      source.markThreadAsRead("s1", 1000L)
      val result = source.listThreadsFromClosedProject(path = "/any")
      assertThat(result.single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun updatesFlowMergesBackendAndReadState() {
    val backendUpdates = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1)
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = emptyList()
      override val updates get() = backendUpdates
    }
    val source = ClaudeSessionSource(backend = backend)

    // With replay=1 on the backend, emission before subscription is delivered.
    backendUpdates.tryEmit(Unit)

    runBlocking(Dispatchers.Default) {
      val result = withTimeoutOrNull(2.seconds) { source.updates.first() }
      assertThat(result).isNotNull()
    }
  }
  @Test
  fun activeThreadIsAutoAdvancedDuringRefresh() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = currentThreads
    }
    val source = ClaudeSessionSource(backend = backend)

    runBlocking(Dispatchers.Default) {
      // First load seeds the read tracker.
      source.listThreadsFromClosedProject(path = "/any")
      source.markThreadAsRead("s1", 1000L)

      // User is viewing thread s1.
      source.setActiveThreadId("s1")

      // User types → updatedAt increases. Active thread: tracker auto-advances → READY.
      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L))
      val whileActive = source.listThreadsFromClosedProject(path = "/any")
      assertThat(whileActive.single().activity).isEqualTo(AgentThreadActivity.READY)

      // User switches away → clear active. Agent replies → updatedAt increases → UNREAD.
      source.setActiveThreadId(null)
      currentThreads = listOf(ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 3000L))
      val afterSwitch = source.listThreadsFromClosedProject(path = "/any")
      assertThat(afterSwitch.single().activity).isEqualTo(AgentThreadActivity.UNREAD)
    }
  }
  @Test
  fun setActiveBeforeMarkReadPreventsStaleUnread() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = currentThreads
    }
    val source = ClaudeSessionSource(backend = backend)

    runBlocking(Dispatchers.Default) {
      // First load seeds the read tracker at updatedAt=1000.
      source.listThreadsFromClosedProject(path = "/any")

      // Simulate openChatThread: setActive then markAsRead with the snapshot's updatedAt (1000).
      source.setActiveThreadId("s1")
      source.markThreadAsRead("s1", 1000L)

      // Backend now reports a newer updatedAt (agent replied while chat was opening).
      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L),
      )

      // Because the thread is active, the refresh should auto-advance the tracker → READY, not UNREAD.
      val result = source.listThreadsFromClosedProject(path = "/any")
      assertThat(result.single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun markAsReadCatchesUpEvenWhenActiveThreadIsCleared() {
    var currentThreads = listOf(
      ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 1000L),
    )
    val backend = object : ClaudeSessionBackend {
      override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = currentThreads
    }
    val source = ClaudeSessionSource(backend = backend)

    runBlocking(Dispatchers.Default) {
      // First load seeds the read tracker at updatedAt=1000.
      source.listThreadsFromClosedProject(path = "/any")

      // Simulate openChatThread: set active + mark as read.
      source.setActiveThreadId("s1")
      source.markThreadAsRead("s1", 1000L)

      // Backend advanced while the chat tab was opening asynchronously.
      currentThreads = listOf(
        ClaudeBackendThread(id = "s1", title = "Session 1", updatedAt = 2000L),
      )

      // Simulate the debounced refresh overriding activeThreadId (chat tab not visible yet).
      source.setActiveThreadId(null)

      // markThreadAsRead should still guarantee READY via the catch-up sentinel.
      val result = source.listThreadsFromClosedProject(path = "/any")
      assertThat(result.single().activity).isEqualTo(AgentThreadActivity.READY)
    }
  }
}

private fun staticBackend(threads: List<ClaudeBackendThread>): ClaudeSessionBackend {
  return object : ClaudeSessionBackend {
    override suspend fun listThreads(path: String, openProject: Project?): List<ClaudeBackendThread> = threads
  }
}
