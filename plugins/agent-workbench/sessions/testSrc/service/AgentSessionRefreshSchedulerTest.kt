// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
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
  fun scopedRefreshSignalsAreDebouncedIntoSingleProviderRefresh() = runBlocking(Dispatchers.Default) {
    val projectPath = "/work/project"
    val scopedEvents = flow {
      emit(scopedEvent(path = projectPath, threadId = "thread-a"))
      delay(100.milliseconds)
      emit(scopedEvent(path = projectPath, threadId = "thread-a"))
      delay(100.milliseconds)
      emit(scopedEvent(path = projectPath, threadId = "thread-a"))
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
          activityHintsByThreadId = mapOf("thread-a" to AgentThreadActivity.PROCESSING),
          mayHaveChangedProjectFiles = true,
          changedProjectFilePaths = setOf(firstChangedFile),
        )
      )
      delay(100.milliseconds)
      emit(
        scopedEvent(
          path = secondPath,
          threadId = "thread-b",
          activityHintsByThreadId = mapOf("thread-b" to AgentThreadActivity.NEEDS_INPUT),
          mayHaveChangedProjectFiles = true,
          changedProjectFilePaths = setOf(secondChangedFile),
        )
      )
    }

    withScheduler(scopedEvents) {
      waitForCondition { providerRefreshes.size == 1 && vfsRefreshes.size == 1 }
      delay(500.milliseconds)

      assertThat(providerRefreshes).hasSize(1)
      assertThat(vfsRefreshes).hasSize(1)
      assertMergedScopedRefresh(providerRefreshes.single(), firstChangedFile, secondChangedFile)
      assertMergedScopedRefresh(vfsRefreshes.single(), firstChangedFile, secondChangedFile)
    }
  }

  private suspend fun withScheduler(
    scopedEvents: Flow<AgentSessionSourceUpdateEvent>,
    action: suspend SchedulerProbe.() -> Unit,
  ) {
    @Suppress("RAW_SCOPE_CREATION")
    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val providerRefreshes = CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>()
    val vfsRefreshes = CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>()
    val scheduler = AgentSessionRefreshScheduler(
      serviceScope = serviceScope,
      sessionSourcesProvider = { emptyList() },
      scopedRefreshProvidersProvider = { listOf(AgentSessionProvider.CODEX) },
      scopedRefreshSignalsProvider = { provider -> if (provider == AgentSessionProvider.CODEX) scopedEvents else emptyFlow() },
      isRefreshGateActive = { true },
      executeFullRefresh = {},
      executeProviderRefresh = { _, _, updateEvent -> providerRefreshes.add(updateEvent) },
      executeProviderHintRefresh = { _, _, updateEvent -> throw AssertionError("Unexpected hint refresh: $updateEvent") },
      scheduleVfsRefreshForSourceUpdate = { _, updateEvent -> vfsRefreshes.add(updateEvent) },
      onFullRefreshFailure = { error -> throw AssertionError("Unexpected full refresh failure", error) },
    )
    try {
      scheduler.observeSessionSourceUpdates()
      SchedulerProbe(
        providerRefreshes = providerRefreshes,
        vfsRefreshes = vfsRefreshes,
      ).action()
    }
    finally {
      serviceScope.cancel()
    }
  }
}

private fun scopedEvent(
  path: String,
  threadId: String,
  activityHintsByThreadId: Map<String, AgentThreadActivity> = emptyMap(),
  mayHaveChangedProjectFiles: Boolean = false,
  changedProjectFilePaths: Set<String>? = null,
): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.HINTS_CHANGED,
    scopedPaths = setOf(path),
    threadIds = setOf(threadId),
    activityHintsByThreadId = activityHintsByThreadId,
    mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
    changedProjectFilePaths = changedProjectFilePaths,
  )
}

private fun assertMergedScopedRefresh(
  event: AgentSessionSourceUpdateEvent,
  firstChangedFile: String,
  secondChangedFile: String,
) {
  assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
  assertThat(event.scopedPaths).containsExactly("/work/first", "/work/second")
  assertThat(event.threadIds).containsExactly("thread-a", "thread-b")
  assertThat(event.activityHintsByThreadId).containsOnly(
    entry("thread-a", AgentThreadActivity.PROCESSING),
    entry("thread-b", AgentThreadActivity.NEEDS_INPUT),
  )
  assertThat(event.mayHaveChangedProjectFiles).isTrue()
  assertThat(event.changedProjectFilePaths).containsExactly(firstChangedFile, secondChangedFile)
}

private data class SchedulerProbe(
  @JvmField val providerRefreshes: CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>,
  @JvmField val vfsRefreshes: CopyOnWriteArrayList<AgentSessionSourceUpdateEvent>,
)
