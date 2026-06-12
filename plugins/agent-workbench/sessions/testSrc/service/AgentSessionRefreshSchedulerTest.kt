// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadPresentationUpdate
import com.intellij.agent.workbench.sessions.waitForCondition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionRefreshSchedulerTest {
  @Test
  fun staleActivityUpdateKeepsCurrentThreadActivity() {
    val thread = AgentSessionThread(
      id = "thread-a",
      title = "Thread A",
      updatedAt = 200L,
      archived = false,
      activityReport = AgentThreadActivityReport(
        rowActivity = AgentThreadActivity.PROCESSING,
        chromeActivity = AgentThreadActivity.REVIEWING,
      ),
      provider = AgentSessionProvider.CODEX,
    )

    val resolved = resolveAgentThreadActivityReportUpdate(
      thread = thread,
      activityUpdate = AgentSessionThreadActivityUpdate(
        activityReport = AgentThreadActivityReport(rowActivity = AgentThreadActivity.READY, chromeActivity = null),
        updatedAt = 100L,
      ),
    )

    assertThat(resolved.activityReport).isEqualTo(thread.activityReport)
    assertThat(resolved.updatedAt).isEqualTo(200L)
  }

  @Test
  fun rowOnlyActivityUpdatePreservesCurrentChromeActivity() {
    val thread = AgentSessionThread(
      id = "thread-a",
      title = "Thread A",
      updatedAt = 200L,
      archived = false,
      activityReport = AgentThreadActivityReport(
        rowActivity = AgentThreadActivity.READY,
        chromeActivity = AgentThreadActivity.REVIEWING,
      ),
      provider = AgentSessionProvider.CODEX,
    )

    val resolved = resolveAgentThreadActivityReportUpdate(
      thread = thread,
      activityUpdate = AgentSessionThreadActivityUpdate(
        activityReport = AgentThreadActivityReport(rowActivity = AgentThreadActivity.PROCESSING, chromeActivity = null),
        updatesChromeActivity = false,
        updatedAt = 300L,
      ),
    )

    assertThat(resolved.activityReport).isEqualTo(
      AgentThreadActivityReport(
        rowActivity = AgentThreadActivity.PROCESSING,
        chromeActivity = AgentThreadActivity.REVIEWING,
      )
    )
    assertThat(resolved.updatedAt).isEqualTo(300L)
  }

  @Test
  fun scopedRefreshSignalsAreDebouncedIntoSingleProviderRefresh() = runBlocking(Dispatchers.Default) {
    val projectPath = "/work/project"
    val scopedEvents = flow {
      emit(scopedEvent(type = AgentSessionSourceUpdate.THREADS_CHANGED, path = projectPath, threadId = "thread-a"))
      delay(100.milliseconds)
      emit(scopedEvent(type = AgentSessionSourceUpdate.THREADS_CHANGED, path = projectPath, threadId = "thread-a"))
      delay(100.milliseconds)
      emit(scopedEvent(type = AgentSessionSourceUpdate.THREADS_CHANGED, path = projectPath, threadId = "thread-a"))
    }

    withScheduler(scopedEvents) {
      waitForCondition { providerRefreshes.size == 1 }
      delay(500.milliseconds)

      assertThat(providerRefreshes).hasSize(1)
      assertThat(vfsRefreshes).isEmpty()
      val event = providerRefreshes.single()
      assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
      assertThat(event.scopedPaths).containsExactly(projectPath)
      assertThat(event.threadIds).containsExactly("thread-a")
    }
  }

  @Test
  fun scopedRefreshSignalsMergeScopeAndProjectFileEvidenceBeforeRefresh() = runBlocking(Dispatchers.Default) {
    val firstPath = "/work/first"
    val secondPath = "/work/second"
    val firstChangedFile = "$firstPath/src/A.kt"
    val secondChangedFile = "$secondPath/src/B.kt"
    val scopedEvents = flow {
      emit(
        scopedEvent(
          path = firstPath,
          threadId = "thread-a",
          activityUpdatesByThreadId = mapOf("thread-a" to activityUpdate(AgentThreadActivity.PROCESSING)),
          mayHaveChangedProjectFiles = true,
          changedProjectFilePaths = setOf(firstChangedFile),
        )
      )
      delay(100.milliseconds)
      emit(
        scopedEvent(
          path = secondPath,
          threadId = "thread-b",
          activityUpdatesByThreadId = mapOf("thread-b" to activityUpdate(AgentThreadActivity.NEEDS_INPUT)),
          mayHaveChangedProjectFiles = true,
          changedProjectFilePaths = setOf(secondChangedFile),
        )
      )
    }

    withScheduler(scopedEvents) {
      waitForCondition { providerHintRefreshes.size == 1 && vfsRefreshes.size == 1 }
      delay(500.milliseconds)

      assertThat(providerRefreshes).isEmpty()
      assertThat(providerHintRefreshes).hasSize(1)
      assertThat(vfsRefreshes).hasSize(1)
      assertMergedScopedRefresh(providerHintRefreshes.single(), firstChangedFile, secondChangedFile)
      assertMergedScopedRefresh(vfsRefreshes.single(), firstChangedFile, secondChangedFile)
    }
  }

  @Test
  fun scopedRefreshSignalsMergeRowOnlyActivityWithoutClearingChromeActivity() = runBlocking(Dispatchers.Default) {
    val projectPath = "/work/project"
    val firstChangedFile = "$projectPath/src/A.kt"
    val secondChangedFile = "$projectPath/src/B.kt"
    val scopedEvents = flow {
      emit(
        scopedEvent(
          path = projectPath,
          threadId = "thread-a",
          activityUpdatesByThreadId = mapOf("thread-a" to activityUpdate(
            activity = AgentThreadActivity.PROCESSING,
            chromeActivity = AgentThreadActivity.REVIEWING,
          )),
          mayHaveChangedProjectFiles = true,
          changedProjectFilePaths = setOf(firstChangedFile),
        )
      )
      delay(100.milliseconds)
      emit(
        scopedEvent(
          path = projectPath,
          threadId = "thread-a",
          activityUpdatesByThreadId = mapOf("thread-a" to activityUpdate(
            activity = AgentThreadActivity.NEEDS_INPUT,
            chromeActivity = null,
            updatesChromeActivity = false,
          )),
          mayHaveChangedProjectFiles = true,
          changedProjectFilePaths = setOf(secondChangedFile),
        )
      )
    }

    withScheduler(scopedEvents) {
      waitForCondition { providerHintRefreshes.size == 1 && vfsRefreshes.size == 1 }
      delay(500.milliseconds)

      val update = providerHintRefreshes.single().activityUpdatesByThreadId.getValue("thread-a")
      assertThat(update.activityReport).isEqualTo(
        AgentThreadActivityReport(
          rowActivity = AgentThreadActivity.NEEDS_INPUT,
          chromeActivity = AgentThreadActivity.REVIEWING,
        )
      )
      assertThat(update.updatesChromeActivity).isTrue()
      assertThat(providerHintRefreshes.single().changedProjectFilePaths).containsExactly(firstChangedFile, secondChangedFile)
      assertThat(vfsRefreshes.single().changedProjectFilePaths).containsExactly(firstChangedFile, secondChangedFile)
    }
  }

  @Test
  fun activityOnlyScopedRefreshSignalsDoNotRefreshProvider() = runBlocking(Dispatchers.Default) {
    val activityUpdate = AgentSessionThreadActivityUpdate(
      activityReport = AgentThreadActivityReport(rowActivity = AgentThreadActivity.PROCESSING, chromeActivity = null),
    )
    val scopedEvents = flow {
      emit(
        activityOnlyEvent(
          activityUpdate = activityUpdate,
        )
      )
    }

    withScheduler(scopedEvents) {
      waitForCondition { appliedActivityUpdates.size == 1 }
      delay(500.milliseconds)

      assertThat(providerRefreshes).isEmpty()
      assertThat(providerHintRefreshes).isEmpty()
      assertThat(vfsRefreshes).isEmpty()
      assertThat(appliedActivityUpdates.single().activityUpdatesByThreadId["thread-a"]).isEqualTo(activityUpdate)
    }
  }

  @Test
  fun titleOnlyScopedRefreshSignalsDoNotRefreshProvider() = runBlocking(Dispatchers.Default) {
    val presentationUpdate = AgentSessionThreadPresentationUpdate(title = "Explicit rollout title", updatedAt = 300L)
    val scopedEvents = flow {
      emit(
        titleOnlyEvent(
          presentationUpdate = presentationUpdate,
        )
      )
    }

    withScheduler(scopedEvents) {
      waitForCondition { appliedActivityUpdates.size == 1 }
      delay(500.milliseconds)

      assertThat(providerRefreshes).isEmpty()
      assertThat(providerHintRefreshes).isEmpty()
      assertThat(vfsRefreshes).isEmpty()
      assertThat(appliedActivityUpdates.single().presentationUpdatesByThreadId["thread-a"]).isEqualTo(presentationUpdate)
    }
  }

  @Test
  fun threadScopedActivityHintRefreshesProviderHintsWithoutProjectFileRefresh() = runBlocking(Dispatchers.Default) {
    val projectPath = "/work/project"
    val scopedEvents = flow {
      emit(
        scopedEvent(
          path = projectPath,
          threadId = "thread-a",
          activityUpdatesByThreadId = mapOf("thread-a" to activityUpdate(AgentThreadActivity.UNREAD)),
        )
      )
    }

    withScheduler(scopedEvents) {
      waitForCondition { providerHintRefreshes.size == 1 }
      delay(500.milliseconds)

      assertThat(providerRefreshes).isEmpty()
      assertThat(providerHintRefreshes).hasSize(1)
      assertThat(vfsRefreshes).isEmpty()
      val event = providerHintRefreshes.single()
      assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
      assertThat(event.scopedPaths).containsExactly(projectPath)
      assertThat(event.threadIds).containsExactly("thread-a")
      assertThat(event.activityUpdatesByThreadId["thread-a"]).isEqualTo(activityUpdate(AgentThreadActivity.UNREAD))
      assertThat(event.mayHaveChangedProjectFiles).isFalse()
      assertThat(event.changedProjectFilePaths).isNull()
    }
  }

  @Test
  fun scopedRefreshSignalsKeepNewestActivityUpdateBeforeRefresh() = runBlocking(Dispatchers.Default) {
    val projectPath = "/work/project"
    val scopedEvents = flow {
      emit(
        scopedEvent(
          path = projectPath,
          threadId = "thread-a",
          activityUpdatesByThreadId = mapOf(
            "thread-a" to activityUpdate(activity = AgentThreadActivity.PROCESSING, updatedAt = 500L)
          ),
        )
      )
      delay(100.milliseconds)
      emit(
        scopedEvent(
          path = projectPath,
          threadId = "thread-a",
          activityUpdatesByThreadId = mapOf(
            "thread-a" to activityUpdate(activity = AgentThreadActivity.NEEDS_INPUT, updatedAt = 100L)
          ),
        )
      )
    }

    withScheduler(scopedEvents) {
      waitForCondition { providerHintRefreshes.size == 1 }
      delay(500.milliseconds)

      assertThat(providerRefreshes).isEmpty()
      assertThat(providerHintRefreshes.single().activityUpdatesByThreadId["thread-a"]).isEqualTo(
        activityUpdate(activity = AgentThreadActivity.PROCESSING, updatedAt = 500L)
      )
    }
  }

  private suspend fun withScheduler(
    scopedEvents: Flow<AgentSessionSourceUpdateEvent>,
    action: suspend SchedulerProbe.() -> Unit,
  ) {
    @Suppress("RAW_SCOPE_CREATION")
    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val providerRefreshes = CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>()
    val providerHintRefreshes = CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>()
    val vfsRefreshes = CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>()
    val appliedActivityUpdates = CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>()
    val scheduler = AgentSessionRefreshScheduler(
      serviceScope = serviceScope,
      sessionSourcesProvider = { emptyList() },
      scopedRefreshProvidersProvider = { listOf(AgentSessionProvider.CODEX) },
      scopedRefreshSignalsProvider = { provider -> if (provider == AgentSessionProvider.CODEX) scopedEvents else emptyFlow() },
      isRefreshGateActive = { true },
      executeFullRefresh = {},
      executeProviderRefresh = { _, _, updateEvent -> providerRefreshes.add(updateEvent) },
      executeProviderHintRefresh = { _, _, updateEvent -> providerHintRefreshes.add(updateEvent) },
      applySourceUpdatePresentationHints = { _, updateEvent -> appliedActivityUpdates.add(updateEvent) },
      scheduleVfsRefreshForSourceUpdate = { _, updateEvent -> vfsRefreshes.add(updateEvent) },
      onFullRefreshFailure = { error -> throw AssertionError("Unexpected full refresh failure", error) },
    )
    try {
      scheduler.observeSessionSourceUpdates()
      SchedulerProbe(
        providerRefreshes = providerRefreshes,
        providerHintRefreshes = providerHintRefreshes,
        vfsRefreshes = vfsRefreshes,
        appliedActivityUpdates = appliedActivityUpdates,
      ).action()
    }
    finally {
      serviceScope.cancel()
    }
  }
}

private fun scopedEvent(
  type: AgentSessionSourceUpdate = AgentSessionSourceUpdate.HINTS_CHANGED,
  path: String,
  threadId: String,
  activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
  mayHaveChangedProjectFiles: Boolean = false,
  changedProjectFilePaths: Set<String>? = null,
): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent(
    type = type,
    scopedPaths = setOf(path),
    threadIds = setOf(threadId),
    activityUpdatesByThreadId = activityUpdatesByThreadId,
    mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
    changedProjectFilePaths = changedProjectFilePaths,
  )
}

private fun activityOnlyEvent(
  activityUpdate: AgentSessionThreadActivityUpdate,
): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.HINTS_CHANGED,
    scopedPaths = setOf("/work/project"),
    activityUpdatesByThreadId = mapOf("thread-a" to activityUpdate),
  )
}

private fun titleOnlyEvent(
  presentationUpdate: AgentSessionThreadPresentationUpdate,
): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.HINTS_CHANGED,
    scopedPaths = setOf("/work/project"),
    presentationUpdatesByThreadId = mapOf("thread-a" to presentationUpdate),
  )
}

private fun assertMergedScopedRefresh(
  event: AgentSessionSourceUpdateEvent,
  firstChangedFile: String,
  secondChangedFile: String,
) {
  assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
  assertThat(event.scopedPaths).containsExactly("/work/first", "/work/second")
  assertThat(event.threadIds).containsExactly("thread-a", "thread-b")
  assertThat(event.activityUpdatesByThreadId.mapValues { (_, update) -> update.activityReport }).containsOnly(
    entry("thread-a", AgentThreadActivityReport(AgentThreadActivity.PROCESSING)),
    entry("thread-b", AgentThreadActivityReport(AgentThreadActivity.NEEDS_INPUT)),
  )
  assertThat(event.mayHaveChangedProjectFiles).isTrue()
  assertThat(event.changedProjectFilePaths).containsExactly(firstChangedFile, secondChangedFile)
}

private fun activityUpdate(
  activity: AgentThreadActivity,
  chromeActivity: AgentThreadActivity? = activity,
  updatesChromeActivity: Boolean = true,
  updatedAt: Long? = null,
): AgentSessionThreadActivityUpdate {
  return AgentSessionThreadActivityUpdate(
    activityReport = AgentThreadActivityReport(rowActivity = activity, chromeActivity = chromeActivity),
    updatesChromeActivity = updatesChromeActivity,
    updatedAt = updatedAt,
  )
}

private data class SchedulerProbe(
  @JvmField val providerRefreshes: CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>,
  @JvmField val providerHintRefreshes: CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>,
  @JvmField val vfsRefreshes: CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>,
  @JvmField val appliedActivityUpdates: CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>,
)
