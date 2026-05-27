// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexAppServerNotification
import com.intellij.agent.workbench.codex.common.CodexAppServerNotificationKind
import com.intellij.agent.workbench.codex.common.CodexAppServerStartedThread
import com.intellij.agent.workbench.codex.common.CodexAppServerException
import com.intellij.agent.workbench.codex.common.CodexThreadActiveFlag
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT
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
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
                hasTurnActivity = true,
            ),
            "thread-pending-plan" to snapshot(
                threadId = "thread-pending-plan",
                statusKind = CodexThreadStatusKind.IDLE,
                hasPendingPlan = true,
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
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to snapshotsByThreadId.keys.toRefreshThreadSeeds(),
            ),
        )

        val hints = hintsByPath.getValue("/work/project")
        val activityByThreadId = hints.activities()
        assertThat(activityByThreadId).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "thread-ready" to AgentThreadActivity.READY,
                "thread-unread-flag" to AgentThreadActivity.NEEDS_INPUT,
                "thread-unread-message" to AgentThreadActivity.UNREAD,
                "thread-pending-plan" to AgentThreadActivity.NEEDS_INPUT,
                "thread-review-flag" to AgentThreadActivity.NEEDS_INPUT,
                "thread-review-mode" to AgentThreadActivity.REVIEWING,
                "thread-processing-status" to AgentThreadActivity.PROCESSING,
                "thread-processing-turn" to AgentThreadActivity.PROCESSING,
            )
        )
        assertThat(hints.activityHintsByThreadId.getValue("thread-unread-flag").responseRequired).isTrue()
        assertThat(hints.activityHintsByThreadId.getValue("thread-pending-plan").responseRequired).isTrue()
        assertThat(hints.activityHintsByThreadId.getValue("thread-review-flag").responseRequired).isTrue()
        assertThat(hints.activityHintsByThreadId.getValue("thread-unread-message").responseRequired).isFalse()
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
            refreshThreadSeedsByPath = mapOf(
                "/work/project-a" to setOf(refreshThreadSeed("thread-shared", updatedAt = 100L)),
                "/work/project-b" to setOf(refreshThreadSeed("thread-shared", updatedAt = 100L)),
            ),
        )

        assertThat(requestedThreadIds).containsExactly("thread-shared")
        assertThat(hintsByPath.keys).containsExactlyInAnyOrder("/work/project-a", "/work/project-b")
        assertThat(hintsByPath.getValue("/work/project-a").activities())
            .containsExactlyEntriesOf(mapOf("thread-shared" to AgentThreadActivity.READY))
        assertThat(hintsByPath.getValue("/work/project-b").activities())
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
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to linkedSetOf(
                    refreshThreadSeed("new-123"),
                    refreshThreadSeed("thread-real", updatedAt = 100L),
                ),
            ),
        )

        assertThat(requestedThreadIds).containsExactly("thread-real")
        assertThat(hintsByPath.getValue("/work/project").activities())
            .containsExactlyEntriesOf(mapOf("thread-real" to AgentThreadActivity.READY))
    }

    @Test
    fun prefetchSkipsTransientThreadNotLoadedError(): Unit = runBlocking(Dispatchers.Default) {
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { threadId ->
                if (threadId == "thread-loading") {
                    throw CodexAppServerException("thread not loaded: $threadId")
                }
                snapshot(
                    threadId = threadId,
                    statusKind = CodexThreadStatusKind.IDLE,
                )
            },
            notifications = emptyFlow(),
        )

        val hintsByPath = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to linkedSetOf(
                    refreshThreadSeed("thread-loading"),
                    refreshThreadSeed("thread-ready"),
                ),
            ),
        )

        assertThat(hintsByPath.getValue("/work/project").activities())
            .containsExactlyEntriesOf(mapOf("thread-ready" to AgentThreadActivity.READY))
    }

    @Test
    fun threadNameUpdatedNotificationsTriggerRefreshWithoutCreatingRebindCandidates(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { threadId ->
                snapshot(
                    threadId = threadId,
                    statusKind = CodexThreadStatusKind.IDLE,
                )
            },
            notifications = notifications,
        )

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/name/updated",
                kind = CodexAppServerNotificationKind.THREAD_NAME_UPDATED,
                threadId = "thread-rename",
            )
        )

        val updateEvent = withTimeout(2.seconds) {
            provider.updateEvents.first()
        }
        assertThat(updateEvent.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
        assertThat(updateEvent.scopedPaths).isNull()
        assertThat(updateEvent.threadIds).containsExactly("thread-rename")

        val hintsByPath = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf("/work/project" to setOf(refreshThreadSeed("thread-rename", updatedAt = 100L))),
        )

        val hints = hintsByPath.getValue("/work/project")
        assertThat(hints.rebindCandidates).isEmpty()
        assertThat(hints.activities())
            .containsExactlyEntriesOf(mapOf("thread-rename" to AgentThreadActivity.READY))
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
                } else {
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

        val updateEvent = withTimeout(2.seconds) {
            provider.updateEvents.first()
        }
        assertThat(updateEvent.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
        assertThat(updateEvent.scopedPaths).containsExactly("/work/project")
        assertThat(updateEvent.threadIds).isNull()

        val hintsByPath = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = emptyMap(),
        )

        val hints = hintsByPath.getValue("/work/project")
        assertThat(hints.activityHintsByThreadId).isEmpty()
        val candidate = hints.rebindCandidates.single()
        assertThat(candidate.threadId).isEqualTo("thread-started")
        assertThat(candidate.title).isEqualTo("thread-started")
        assertThat(candidate.updatedAt).isEqualTo(900L)
        assertThat(candidate.activity).isEqualTo(AgentThreadActivity.UNREAD)
    }

    @Test
    fun threadStartedNotificationsEmitDelayedRetryForThreadListRefresh(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { null },
            notifications = notifications,
        )
        val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.UNLIMITED)
        val collectJob = launch {
            provider.updateEvents.collect {
                updates.send(it)
            }
        }
        try {
            withTimeout(2.seconds) {
                notifications.subscriptionCount.first { it > 0 }
            }
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

            val firstUpdate = withTimeout(2.seconds) { updates.receive() }
            val retryUpdate = withTimeout(2.seconds) { updates.receive() }
            assertThat(firstUpdate.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
            assertThat(firstUpdate.scopedPaths).containsExactly("/work/project")
            assertThat(firstUpdate.threadIds).isNull()
            assertThat(retryUpdate).isEqualTo(firstUpdate)
        }
        finally {
            collectJob.cancel()
            updates.close()
        }
    }

    @Test
    fun threadNameUpdatedNotificationsUseRecentStartedThreadPathWhenAvailable(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { null },
            notifications = notifications,
        )

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/started",
                kind = CodexAppServerNotificationKind.THREAD_STARTED,
                threadId = "thread-rename",
                startedThread = startedThread(
                    threadId = "thread-rename",
                    path = "/work/project",
                    updatedAt = 100L,
                ),
            )
        )
        withTimeout(2.seconds) {
            provider.updateEvents.first()
        }

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/name/updated",
                kind = CodexAppServerNotificationKind.THREAD_NAME_UPDATED,
                threadId = "thread-rename",
            )
        )

        val updateEvent = withTimeout(2.seconds) {
            provider.updateEvents.first()
        }
        assertThat(updateEvent.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
        assertThat(updateEvent.scopedPaths).containsExactly("/work/project")
        assertThat(updateEvent.threadIds).containsExactly("thread-rename")

        val hintsByPath = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = emptyMap(),
        )
        assertThat(hintsByPath.getValue("/work/project").rebindCandidates.single().threadId).isEqualTo("thread-rename")
    }

    @Test
    fun threadStartedNotificationCwdIsNormalizedForRebindCandidates(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { null },
            notifications = notifications,
        )

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/started",
                kind = CodexAppServerNotificationKind.THREAD_STARTED,
                threadId = "thread-started",
                startedThread = startedThread(
                    threadId = "thread-started",
                    path = "/work/project/",
                    updatedAt = 100L,
                ),
            )
        )
        withTimeout(2.seconds) {
            provider.updateEvents.first()
        }

        val hints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = emptyMap(),
        ).getValue("/work/project")

        assertThat(hints.rebindCandidates.single().threadId).isEqualTo("thread-started")
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
                } else {
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

        val updateEvent = withTimeout(2.seconds) {
            provider.updateEvents.first()
        }
        assertThat(updateEvent.scopedPaths).containsExactly("/work/project")

        val hintsBeforeKnown = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = emptyMap(),
        )
        assertThat(hintsBeforeKnown.getValue("/work/project").rebindCandidates).hasSize(1)

        val hintsAfterKnown = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf("/work/project" to setOf(refreshThreadSeed("thread-started", updatedAt = 500L))),
        )

        val hints = hintsAfterKnown.getValue("/work/project")
        assertThat(hints.rebindCandidates).isEmpty()
        assertThat(hints.activities())
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

        val updateEvent = withTimeout(2.seconds) {
            provider.updateEvents.first()
        }
        assertThat(updateEvent.scopedPaths).containsExactly("/work/project")

        val hints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = emptyMap(),
        ).getValue("/work/project")

        val candidate = hints.rebindCandidates.single()
        assertThat(candidate.threadId).isEqualTo("thread-reviewing")
        assertThat(candidate.updatedAt).isEqualTo(700L)
        assertThat(candidate.activity).isEqualTo(AgentThreadActivity.NEEDS_INPUT)
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
                provider.updateEvents.first()
            }
        }

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/status/changed",
                kind = CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
                threadId = "thread-1",
            )
        )
        val updateEvent = update.await()
        assertThat(updateEvent.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
        assertThat(updateEvent.threadIds).containsExactly("thread-1")
    }

    @Test
    fun threadStatusNotificationsProvideNotificationBackedHintsWhenSnapshotUnavailable(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { null },
            notifications = notifications,
        )

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/status/changed",
                kind = CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
                threadId = "thread-live",
                statusKind = CodexThreadStatusKind.ACTIVE,
                activeFlags = listOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT),
            )
        )
        withTimeout(2.seconds) {
            provider.updateEvents.first()
        }

        val hints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-live")),
            ),
        ).getValue("/work/project")

        val hint = hints.activityHintsByThreadId.getValue("thread-live")
        assertThat(hint.activity).isEqualTo(AgentThreadActivity.NEEDS_INPUT)
        assertThat(hint.responseRequired).isTrue()
        assertThat(hint.updatedAt).isGreaterThan(0L)
    }

    @Test
    fun threadStatusNotificationHintDoesNotOverrideNewerKnownThreadWhenSnapshotUnavailable(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { null },
            notifications = notifications,
        )

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/status/changed",
                kind = CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
                threadId = "thread-live",
                statusKind = CodexThreadStatusKind.ACTIVE,
            )
        )
        withTimeout(2.seconds) {
            provider.updateEvents.first()
        }

        val hints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-live", updatedAt = Long.MAX_VALUE)),
            ),
        )

        assertThat(hints).isEmpty()
    }

    @Test
    fun snapshotEnrichmentOverridesNotificationOnlyStatusHint(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val requestedThreadIds = mutableListOf<String>()
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { threadId ->
                requestedThreadIds += threadId
                snapshot(
                    threadId = threadId,
                    statusKind = CodexThreadStatusKind.IDLE,
                    isReviewing = true,
                    updatedAt = 500L,
                )
            },
            notifications = notifications,
        )

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/status/changed",
                kind = CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
                threadId = "thread-live",
                statusKind = CodexThreadStatusKind.ACTIVE,
            )
        )
        withTimeout(2.seconds) {
            provider.updateEvents.first()
        }

        val hints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-live", updatedAt = 500L)),
            ),
        ).getValue("/work/project")

        assertThat(requestedThreadIds).containsExactly("thread-live")
        assertThat(hints.activities()).containsExactlyEntriesOf(mapOf("thread-live" to AgentThreadActivity.REVIEWING))
    }

    @Test
    fun turnLifecycleNotificationsClearNotificationBackedHintsWhenSnapshotUnavailable(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { null },
            notifications = notifications,
        )

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/status/changed",
                kind = CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
                threadId = "thread-live",
                statusKind = CodexThreadStatusKind.ACTIVE,
            )
        )
        withTimeout(2.seconds) {
            provider.updateEvents.first()
        }
        val hintsBeforeTurnCompleted = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-live")),
            ),
        )
        assertThat(hintsBeforeTurnCompleted.getValue("/work/project").activities())
            .containsExactlyEntriesOf(mapOf("thread-live" to AgentThreadActivity.PROCESSING))

        notifications.emit(
            CodexAppServerNotification(
                method = "turn/completed",
                kind = CodexAppServerNotificationKind.TURN_COMPLETED,
                threadId = "thread-live",
            )
        )
        withTimeout(2.seconds) {
            provider.updateEvents.first()
        }

        val hintsAfterTurnCompleted = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-live")),
            ),
        )
        assertThat(hintsAfterTurnCompleted).isEmpty()
    }

    @Test
    fun reusesCachedSnapshotWhenUpdatedAtIsUnchanged(): Unit = runBlocking(Dispatchers.Default) {
        val requestedThreadIds = mutableListOf<String>()
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { threadId ->
                requestedThreadIds += threadId
                snapshot(
                    threadId = threadId,
                    statusKind = CodexThreadStatusKind.IDLE,
                    updatedAt = 100L,
                )
            },
            notifications = emptyFlow(),
        )

        val refreshThreadSeedsByPath = mapOf(
            "/work/project" to setOf(refreshThreadSeed("thread-cached", updatedAt = 100L)),
        )

        provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = refreshThreadSeedsByPath,
        )
        val secondHints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = refreshThreadSeedsByPath,
        )

        assertThat(requestedThreadIds).containsExactly("thread-cached")
        assertThat(secondHints.getValue("/work/project").activities())
            .containsExactlyEntriesOf(mapOf("thread-cached" to AgentThreadActivity.READY))
    }

    @Test
    fun forceRefreshBypassesCachedSnapshotWhenUpdatedAtIsUnchanged(): Unit = runBlocking(Dispatchers.Default) {
        val requestedThreadIds = mutableListOf<String>()
        var statusKind = CodexThreadStatusKind.IDLE
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { threadId ->
                requestedThreadIds += threadId
                snapshot(
                    threadId = threadId,
                    statusKind = statusKind,
                    updatedAt = 100L,
                )
            },
            notifications = emptyFlow(),
        )

        provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-cached", updatedAt = 100L)),
            ),
        )
        statusKind = CodexThreadStatusKind.ACTIVE
        val refreshedHints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-cached", updatedAt = 100L, forceRefresh = true)),
            ),
        )

        assertThat(requestedThreadIds).containsExactly("thread-cached", "thread-cached")
        val hint = refreshedHints.getValue("/work/project").activityHintsByThreadId.getValue("thread-cached")
        assertThat(hint.activity).isEqualTo(AgentThreadActivity.PROCESSING)
        assertThat(hint.verifiedFresh).isTrue()
    }

    @Test
    fun forceRefreshDoesNotFallBackToNotificationHintWhenSnapshotUnavailable(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { null },
            notifications = notifications,
        )

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/status/changed",
                kind = CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
                threadId = "thread-live",
                statusKind = CodexThreadStatusKind.ACTIVE,
            )
        )
        withTimeout(2.seconds) {
            provider.updateEvents.first()
        }

        val notificationHints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-live")),
            ),
        )
        assertThat(notificationHints.getValue("/work/project").activities())
            .containsExactlyEntriesOf(mapOf("thread-live" to AgentThreadActivity.PROCESSING))

        val forcedHints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-live", forceRefresh = true)),
            ),
        )
        assertThat(forcedHints).isEmpty()
    }

    @Test
    fun refetchesSnapshotWhenUpdatedAtChanges(): Unit = runBlocking(Dispatchers.Default) {
        val requestedThreadIds = mutableListOf<String>()
        val snapshotsByUpdatedAt = mapOf(
            100L to snapshot(threadId = "thread-cached", statusKind = CodexThreadStatusKind.IDLE, updatedAt = 100L),
            200L to snapshot(threadId = "thread-cached", statusKind = CodexThreadStatusKind.ACTIVE, updatedAt = 200L),
        )
        var currentUpdatedAt = 100L
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { threadId ->
                requestedThreadIds += threadId
                snapshotsByUpdatedAt.getValue(currentUpdatedAt)
            },
            notifications = emptyFlow(),
        )

        provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-cached", updatedAt = 100L)),
            ),
        )
        currentUpdatedAt = 200L
        val refreshedHints = provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = mapOf(
                "/work/project" to setOf(refreshThreadSeed("thread-cached", updatedAt = 200L)),
            ),
        )

        assertThat(requestedThreadIds).containsExactly("thread-cached", "thread-cached")
        assertThat(refreshedHints.getValue("/work/project").activities())
            .containsExactlyEntriesOf(mapOf("thread-cached" to AgentThreadActivity.PROCESSING))
    }

    @Test
    fun statusNotificationsInvalidateCachedSnapshot(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val requestedThreadIds = mutableListOf<String>()
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { threadId ->
                requestedThreadIds += threadId
                snapshot(
                    threadId = threadId,
                    statusKind = CodexThreadStatusKind.IDLE,
                    updatedAt = 100L,
                )
            },
            notifications = notifications,
        )

        val refreshThreadSeedsByPath = mapOf(
            "/work/project" to setOf(refreshThreadSeed("thread-cached", updatedAt = 100L)),
        )
        provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = refreshThreadSeedsByPath,
        )

        notifications.emit(
            CodexAppServerNotification(
                method = "thread/status/changed",
                kind = CodexAppServerNotificationKind.THREAD_STATUS_CHANGED,
                threadId = "thread-cached",
            )
        )
        withTimeout(2.seconds) {
            provider.updateEvents.first()
        }

        provider.prefetchRefreshHints(
            paths = listOf("/work/project"),
            refreshThreadSeedsByPath = refreshThreadSeedsByPath,
        )

        assertThat(requestedThreadIds).containsExactly("thread-cached", "thread-cached")
    }

    @Test
    fun outputNotificationsAreDebouncedIntoSingleUpdate(): Unit = runBlocking(Dispatchers.Default) {
        val notifications = MutableSharedFlow<CodexAppServerNotification>(replay = 1, extraBufferCapacity = 16)
        val provider = CodexAppServerRefreshHintsProvider(
            readThreadActivitySnapshot = { null },
            notifications = notifications,
        )

        val updates = Channel<AgentSessionSourceUpdateEvent>(capacity = Channel.UNLIMITED)
        val collectJob = launch {
            provider.updateEvents.collect {
                updates.send(it)
            }
        }
        try {
            withTimeout(2.seconds) {
                notifications.subscriptionCount.first { it > 0 }
            }
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
                    threadId = "thread-2",
                )
            )

            val updateEvent = withTimeout(2.seconds) {
                updates.receive()
            }
            assertThat(updateEvent.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
            assertThat(updateEvent.threadIds).containsExactly("thread-1", "thread-2")
            val unexpectedSecond = withTimeoutOrNull(500.milliseconds) {
                updates.receive()
            }
            assertThat(unexpectedSecond).isNull()
        } finally {
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
    hasPendingPlan: Boolean = false,
    isReviewing: Boolean = false,
    hasInProgressTurn: Boolean = false,
    hasTurnActivity: Boolean = false,
    updatedAt: Long = 100L,
): CodexThreadActivitySnapshot {
    return CodexThreadActivitySnapshot(
        threadId = threadId,
        updatedAt = updatedAt,
        statusKind = statusKind,
        activeFlags = activeFlags,
        hasUnreadAssistantMessage = hasUnreadAssistantMessage,
        hasPendingPlan = hasPendingPlan,
        isReviewing = isReviewing,
        hasInProgressTurn = hasInProgressTurn,
        hasTurnActivity = hasTurnActivity,
    )
}

private fun CodexRefreshHints.activities(): Map<String, AgentThreadActivity> {
    return activityHintsByThreadId.mapValues { (_, hint) -> hint.activity }
}

private fun Collection<String>.toRefreshThreadSeeds(): Set<AgentSessionRefreshThreadSeed> {
    return mapTo(LinkedHashSet()) { threadId -> refreshThreadSeed(threadId) }
}

private fun refreshThreadSeed(
    threadId: String,
    updatedAt: Long = UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT,
    forceRefresh: Boolean = false,
): AgentSessionRefreshThreadSeed {
    return AgentSessionRefreshThreadSeed(
        threadId = threadId,
        updatedAt = updatedAt,
        forceRefresh = forceRefresh,
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
