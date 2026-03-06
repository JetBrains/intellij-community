// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexAppServerNotification
import com.intellij.agent.workbench.codex.common.CodexAppServerNotificationKind
import com.intellij.agent.workbench.codex.common.CodexAppServerStartedThread
import com.intellij.agent.workbench.codex.common.CodexThreadActiveFlag
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import com.intellij.agent.workbench.common.AgentThreadActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CodexAppServerRefreshHintsProviderTest {
  @Test
  fun mapsThreadReadSnapshotSignalsToRefreshActivities(): Unit = runBlocking(Dispatchers.Default) {
    val snapshotsByThreadId = linkedMapOf(
      "thread-ready" to snapshot(
        threadId = "thread-ready",
        statusKind = CodexThreadStatusKind.IDLE,
      ),
      "thread-unread-flag" to snapshot(
        threadId = "thread-unread-flag",
        statusKind = CodexThreadStatusKind.ACTIVE,
        activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT),
      ),
      "thread-unread-message" to snapshot(
        threadId = "thread-unread-message",
        statusKind = CodexThreadStatusKind.ACTIVE,
        hasUnreadAssistantMessage = true,
      ),
      "thread-review-flag" to snapshot(
        threadId = "thread-review-flag",
        statusKind = CodexThreadStatusKind.ACTIVE,
        activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_APPROVAL),
      ),
      "thread-review-mode" to snapshot(
        threadId = "thread-review-mode",
        statusKind = CodexThreadStatusKind.IDLE,
        isReviewing = true,
      ),
      "thread-processing-status" to snapshot(
        threadId = "thread-processing-status",
        statusKind = CodexThreadStatusKind.ACTIVE,
      ),
      "thread-processing-turn" to snapshot(
        threadId = "thread-processing-turn",
        statusKind = CodexThreadStatusKind.IDLE,
        hasInProgressTurn = true,
      ),
    )

    val provider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = { threadId -> snapshotsByThreadId[threadId] },
      notifications = emptyFlow(),
    )

    val hintsByPath = provider.prefetchRefreshHints(
      paths = listOf("/work/project"),
      knownThreadIdsByPath = mapOf(
        "/work/project" to snapshotsByThreadId.keys,
      ),
    )

    val activityByThreadId = hintsByPath.getValue("/work/project").activityByThreadId
    assertThat(activityByThreadId).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "thread-ready" to AgentThreadActivity.READY,
        "thread-unread-flag" to AgentThreadActivity.UNREAD,
        "thread-unread-message" to AgentThreadActivity.UNREAD,
        "thread-review-flag" to AgentThreadActivity.REVIEWING,
        "thread-review-mode" to AgentThreadActivity.REVIEWING,
        "thread-processing-status" to AgentThreadActivity.PROCESSING,
        "thread-processing-turn" to AgentThreadActivity.PROCESSING,
      )
    )
  }

  @Test
  fun fetchesSharedThreadIdOnlyOnceAcrossMultiplePaths(): Unit = runBlocking(Dispatchers.Default) {
    val requestedThreadIds = mutableListOf<String>()
    val provider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = { threadId ->
        requestedThreadIds += threadId
        snapshot(
          threadId = threadId,
          statusKind = CodexThreadStatusKind.IDLE,
        )
      },
      notifications = emptyFlow(),
    )

    val hintsByPath = provider.prefetchRefreshHints(
      paths = listOf("/work/project-a", "/work/project-b"),
      knownThreadIdsByPath = mapOf(
        "/work/project-a" to setOf("thread-shared"),
        "/work/project-b" to setOf("thread-shared"),
      ),
    )

    assertThat(requestedThreadIds).containsExactly("thread-shared")
    assertThat(hintsByPath.keys).containsExactlyInAnyOrder("/work/project-a", "/work/project-b")
    assertThat(hintsByPath.getValue("/work/project-a").activityByThreadId)
      .containsExactlyEntriesOf(mapOf("thread-shared" to AgentThreadActivity.READY))
    assertThat(hintsByPath.getValue("/work/project-b").activityByThreadId)
      .containsExactlyEntriesOf(mapOf("thread-shared" to AgentThreadActivity.READY))
  }

  @Test
  fun ignoresPendingThreadIdsWhenPrefetchingActivityHints(): Unit = runBlocking(Dispatchers.Default) {
    val requestedThreadIds = mutableListOf<String>()
    val provider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = { threadId ->
        requestedThreadIds += threadId
        snapshot(
          threadId = threadId,
          statusKind = CodexThreadStatusKind.IDLE,
        )
      },
      notifications = emptyFlow(),
    )

    val hintsByPath = provider.prefetchRefreshHints(
      paths = listOf("/work/project"),
      knownThreadIdsByPath = mapOf(
        "/work/project" to linkedSetOf("new-123", "thread-real"),
      ),
    )

    assertThat(requestedThreadIds).containsExactly("thread-real")
    assertThat(hintsByPath.getValue("/work/project").activityByThreadId)
      .containsExactlyEntriesOf(mapOf("thread-real" to AgentThreadActivity.READY))
  }

  @Test
  fun threadStartedNotificationsEmitSnapshotBackedRebindCandidatesForUnknownThreadIds(): Unit = runBlocking(Dispatchers.Default) {
    val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
    val provider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = { threadId ->
        if (threadId == "thread-started") {
          snapshot(
            threadId = threadId,
            statusKind = CodexThreadStatusKind.IDLE,
            hasUnreadAssistantMessage = true,
            updatedAt = 900L,
          )
        }
        else {
          null
        }
      },
      notifications = notifications,
    )

    notifications.emit(
      CodexAppServerNotification(
        method = "thread/started",
        kind = CodexAppServerNotificationKind.THREAD_STARTED,
        threadId = "thread-started",
        startedThread = startedThread(
          threadId = "thread-started",
          path = "/work/project",
          updatedAt = 100L,
          statusKind = CodexThreadStatusKind.IDLE,
        ),
      )
    )

    withTimeout(2.seconds) {
      provider.updates.first()
    }

    val hintsByPath = provider.prefetchRefreshHints(
      paths = listOf("/work/project"),
      knownThreadIdsByPath = emptyMap(),
    )

    val hints = hintsByPath.getValue("/work/project")
    assertThat(hints.activityByThreadId).isEmpty()
    val candidate = hints.rebindCandidates.single()
    assertThat(candidate.threadId).isEqualTo("thread-started")
    assertThat(candidate.title).isEqualTo("thread-started")
    assertThat(candidate.updatedAt).isEqualTo(900L)
    assertThat(candidate.activity).isEqualTo(AgentThreadActivity.UNREAD)
  }

  @Test
  fun startedRebindCandidatesDisappearOnceThreadIdBecomesKnown(): Unit = runBlocking(Dispatchers.Default) {
    val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
    val provider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = { threadId ->
        if (threadId == "thread-started") {
          snapshot(
            threadId = threadId,
            statusKind = CodexThreadStatusKind.IDLE,
            updatedAt = 500L,
          )
        }
        else {
          null
        }
      },
      notifications = notifications,
    )

    notifications.emit(
      CodexAppServerNotification(
        method = "thread/started",
        kind = CodexAppServerNotificationKind.THREAD_STARTED,
        threadId = "thread-started",
        startedThread = startedThread(
          threadId = "thread-started",
          path = "/work/project",
          updatedAt = 100L,
        ),
      )
    )

    withTimeout(2.seconds) {
      provider.updates.first()
    }

    val hintsBeforeKnown = provider.prefetchRefreshHints(
      paths = listOf("/work/project"),
      knownThreadIdsByPath = emptyMap(),
    )
    assertThat(hintsBeforeKnown.getValue("/work/project").rebindCandidates).hasSize(1)

    val hintsAfterKnown = provider.prefetchRefreshHints(
      paths = listOf("/work/project"),
      knownThreadIdsByPath = mapOf("/work/project" to setOf("thread-started")),
    )

    val hints = hintsAfterKnown.getValue("/work/project")
    assertThat(hints.rebindCandidates).isEmpty()
    assertThat(hints.activityByThreadId)
      .containsExactlyEntriesOf(mapOf("thread-started" to AgentThreadActivity.READY))
  }

  @Test
  fun startedRebindCandidatesFallBackToNotificationStatusWhenSnapshotUnavailable(): Unit = runBlocking(Dispatchers.Default) {
    val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
    val provider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = { null },
      notifications = notifications,
    )

    notifications.emit(
      CodexAppServerNotification(
        method = "thread/started",
        kind = CodexAppServerNotificationKind.THREAD_STARTED,
        threadId = "thread-reviewing",
        startedThread = startedThread(
          threadId = "thread-reviewing",
          path = "/work/project",
          updatedAt = 700L,
          activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_APPROVAL),
        ),
      )
    )

    withTimeout(2.seconds) {
      provider.updates.first()
    }

    val hints = provider.prefetchRefreshHints(
      paths = listOf("/work/project"),
      knownThreadIdsByPath = emptyMap(),
    ).getValue("/work/project")

    val candidate = hints.rebindCandidates.single()
    assertThat(candidate.threadId).isEqualTo("thread-reviewing")
    assertThat(candidate.updatedAt).isEqualTo(700L)
    assertThat(candidate.activity).isEqualTo(AgentThreadActivity.REVIEWING)
  }

  @Test
  fun emitsUpdatesFromThreadStatusNotifications(): Unit = runBlocking(Dispatchers.Default) {
    val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
    val provider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = { null },
      notifications = notifications,
    )

    val update = async {
      withTimeout(2.seconds) {
        provider.updates.first()
      }
    }

    notifications.emit(
      CodexAppServerNotification(
        method = "thread/status/changed",
        kind = CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
        threadId = "thread-1",
      )
    )

    update.await()
  }

  @Test
  fun outputNotificationsAreDebouncedIntoSingleUpdate(): Unit = runBlocking(Dispatchers.Default) {
    val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
    val provider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = { null },
      notifications = notifications,
    )

    val updates = Channel<Unit>(capacity = Channel.UNLIMITED)
    val collectJob = launch {
      provider.updates.collect {
        updates.send(Unit)
      }
    }
    try {
      notifications.emit(
        CodexAppServerNotification(
          method = "item/commandExecution/outputDelta",
          kind = CodexAppServerNotificationKind.COMMAND_EXECUTION_OUTPUT_DELTA,
          threadId = "thread-1",
        )
      )
      notifications.emit(
        CodexAppServerNotification(
          method = "item/commandExecution/outputDelta",
          kind = CodexAppServerNotificationKind.COMMAND_EXECUTION_OUTPUT_DELTA,
          threadId = "thread-1",
        )
      )

      withTimeout(2.seconds) {
        updates.receive()
      }
      val unexpectedSecond = withTimeoutOrNull(500.milliseconds) {
        updates.receive()
      }
      assertThat(unexpectedSecond).isNull()
    }
    finally {
      collectJob.cancel()
      updates.close()
    }
  }
}

private fun snapshot(
  threadId: String,
  statusKind: CodexThreadStatusKind,
  activeFlags: List<CodexThreadActiveFlag> = emptyList(),
  hasUnreadAssistantMessage: Boolean = false,
  isReviewing: Boolean = false,
  hasInProgressTurn: Boolean = false,
  updatedAt: Long = 100L,
): CodexThreadActivitySnapshot {
  return CodexThreadActivitySnapshot(
    threadId = threadId,
    updatedAt = updatedAt,
    statusKind = statusKind,
    activeFlags = activeFlags,
    hasUnreadAssistantMessage = hasUnreadAssistantMessage,
    isReviewing = isReviewing,
    hasInProgressTurn = hasInProgressTurn,
  )
}

private fun startedThread(
  threadId: String,
  path: String,
  updatedAt: Long,
  statusKind: CodexThreadStatusKind = CodexThreadStatusKind.IDLE,
  activeFlags: List<CodexThreadActiveFlag> = emptyList(),
): CodexAppServerStartedThread {
  return CodexAppServerStartedThread(
    id = threadId,
    title = threadId,
    updatedAt = updatedAt,
    cwd = path,
    statusKind = statusKind,
    activeFlags = activeFlags,
  )
}
