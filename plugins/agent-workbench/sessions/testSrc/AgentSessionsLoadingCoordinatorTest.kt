// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.providers.AgentSessionSource
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class AgentSessionsLoadingCoordinatorTest {
  @Test
  fun providerUpdateQueuedWhileRefreshIsInProgress() = runBlocking {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val refreshStarted = CompletableDeferred<Unit>()
    val releaseRefresh = CompletableDeferred<Unit>()
    val closedRefreshInvocations = AtomicInteger(0)
    var closedThreadUpdatedAt = 100L

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      updates = updates,
      listFromOpenProject = { path, _ ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          refreshStarted.complete(Unit)
          releaseRefresh.await()
          listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX))
        }
      },
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          closedRefreshInvocations.incrementAndGet()
          listOf(thread(id = "codex-1", updatedAt = closedThreadUpdatedAt, provider = AgentSessionProvider.CODEX))
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
      isRefreshGateActive = { true },
    ) { coordinator, stateStore ->
      coordinator.observeSessionSourceUpdates()
      coordinator.refresh()

      refreshStarted.await()
      closedThreadUpdatedAt = 300L
      updates.tryEmit(Unit)
      delay(700.milliseconds)
      releaseRefresh.complete(Unit)

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.provider == AgentSessionProvider.CODEX }
          ?.updatedAt == 300L
      }

      assertThat(closedRefreshInvocations.get()).isEqualTo(1)
    }
  }

  @Test
  fun providerUpdateDeferredUntilRefreshGateIsActive() = runBlocking {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val gateActive = AtomicBoolean(false)
    val closedRefreshInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      updates = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          closedRefreshInvocations.incrementAndGet()
          listOf(thread(id = "codex-1", updatedAt = 300L, provider = AgentSessionProvider.CODEX))
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { gateActive.get() },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(Unit)
      delay(900.milliseconds)

      assertThat(closedRefreshInvocations.get()).isEqualTo(0)
      assertThat(
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.provider == AgentSessionProvider.CODEX }
          ?.updatedAt
      ).isEqualTo(100L)

      gateActive.set(true)

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.provider == AgentSessionProvider.CODEX }
          ?.updatedAt == 300L
      }

      assertThat(closedRefreshInvocations.get()).isEqualTo(1)
    }
  }

  private suspend fun withLoadingCoordinator(
    sessionSourcesProvider: () -> List<AgentSessionSource>,
    projectEntriesProvider: suspend () -> List<ProjectEntry> = { emptyList() },
    isRefreshGateActive: suspend () -> Boolean,
    action: suspend (AgentSessionsLoadingCoordinator, AgentSessionsStateStore) -> Unit,
  ) {
    @Suppress("RAW_SCOPE_CREATION")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val treeUiState = InMemorySessionsTreeUiState()
    val stateStore = AgentSessionsStateStore(treeUiState)
    try {
      val coordinator = AgentSessionsLoadingCoordinator(
        serviceScope = scope,
        sessionSourcesProvider = sessionSourcesProvider,
        projectEntriesProvider = projectEntriesProvider,
        treeUiState = treeUiState,
        stateStore = stateStore,
        isRefreshGateActive = isRefreshGateActive,
      )
      action(coordinator, stateStore)
    }
    finally {
      scope.cancel()
    }
  }
}
