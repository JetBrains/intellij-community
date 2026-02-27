// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
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
  fun providerUpdateQueuedWhileRefreshIsInProgress() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val refreshStarted = CompletableDeferred<Unit>()
    val releaseRefresh = CompletableDeferred<Unit>()
    val closedRefreshInvocations = AtomicInteger(0)
    var closedThreadUpdatedAt = 100L

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
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
  fun providerUpdateDeferredUntilRefreshGateIsActive() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val gateActive = AtomicBoolean(false)
    val closedRefreshInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
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

  @Test
  fun providerUpdateIgnoredWhenSourceDoesNotSupportUpdates() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
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
      isRefreshGateActive = { true },
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
    }
  }

  @Test
  fun providerUpdateSynchronizesOpenChatTabPresentationWithoutExplicitRefresh() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    var updatedTitle = "Initial title"
    val receivedTitleMaps = mutableListOf<Map<Pair<String, String>, String>>()
    val receivedActivityMaps = mutableListOf<Map<Pair<String, String>, AgentThreadActivity>>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updates = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(thread(id = "codex-1", updatedAt = 300L, title = updatedTitle, provider = AgentSessionProvider.CODEX))
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      openChatTabPresentationUpdater = { titleMap, activityMap ->
        receivedTitleMaps.add(titleMap)
        receivedActivityMaps.add(activityMap)
        titleMap.size
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, title = "Initial title", provider = AgentSessionProvider.CODEX)
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updatedTitle = "Renamed from source update"
      updates.tryEmit(Unit)

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.provider == AgentSessionProvider.CODEX }
          ?.title == "Renamed from source update"
      }

      val expectedKey = PROJECT_PATH to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-1")
      waitForCondition {
        receivedTitleMaps.any { it[expectedKey] == "Renamed from source update" }
      }
      waitForCondition {
        receivedActivityMaps.any { it[expectedKey] == AgentThreadActivity.READY }
      }
    }
  }

  @Test
  fun providerUpdateBuildsPendingTabRebindTargetsForCodex() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updates = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(thread(id = "codex-2", updatedAt = 300L, title = "New Codex thread", provider = AgentSessionProvider.CODEX))
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-1",
              pendingCreatedAtMs = 200L,
            )
          )
        )
      },
      openChatPendingTabSpecificBinder = { path, pendingThreadIdentity, target ->
        rebindInvocations.add(
          PendingCodexRebindInvocation(
            path = path,
            pendingThreadIdentity = pendingThreadIdentity,
            target = target,
          )
        )
        true
      },
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

      waitForCondition {
        rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-1")
      val target = invocation.target
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-2"))
      assertThat(target.threadId).isEqualTo("codex-2")
      assertThat(target.shellCommand).containsExactly("codex", "resume", "codex-2")
      assertThat(target.threadTitle).isEqualTo("New Codex thread")
      assertThat(target.threadActivity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun providerUpdateRebindsOnlyToNewThreadIdsForCodex() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updates = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(id = "codex-1", updatedAt = 500L, title = "Existing thread", provider = AgentSessionProvider.CODEX),
            thread(id = "codex-2", updatedAt = 700L, title = "New Codex thread", provider = AgentSessionProvider.CODEX),
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-2",
              pendingCreatedAtMs = 680L,
            )
          )
        )
      },
      openChatPendingTabSpecificBinder = { path, pendingThreadIdentity, target ->
        rebindInvocations.add(
          PendingCodexRebindInvocation(
            path = path,
            pendingThreadIdentity = pendingThreadIdentity,
            target = target,
          )
        )
        true
      },
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

      waitForCondition {
        rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-2")
      val target = invocation.target
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-2"))
      assertThat(target.threadId).isEqualTo("codex-2")
    }
  }

  @Test
  fun refreshFallsBackToPerPathLoadWhenPrefetchOmitsPath() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val openLoadCounts = LinkedHashMap<String, AtomicInteger>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      prefetch = {
        mapOf(
          PROJECT_PATH to listOf(
            thread(id = "codex-prefetched-a", updatedAt = 600L, title = "Prefetched A", provider = AgentSessionProvider.CODEX)
          )
        )
      },
      listFromOpenProject = { path, _ ->
        openLoadCounts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        listOf(
          thread(
            id = "codex-fallback-${path.substringAfterLast('/')}",
            updatedAt = 500L,
            provider = AgentSessionProvider.CODEX,
          )
        )
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      projectEntriesProvider = {
        listOf(
          openProjectEntry(PROJECT_PATH, "Project A"),
          openProjectEntry(projectB, "Project B"),
        )
      },
      isRefreshGateActive = { true },
    ) { coordinator, stateStore ->
      coordinator.refresh()

      waitForCondition {
        val projects = stateStore.snapshot().projects
        projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true &&
        projects.firstOrNull { it.path == projectB }?.hasLoaded == true
      }

      val projectA = stateStore.snapshot().projects.first { it.path == PROJECT_PATH }
      val projectBState = stateStore.snapshot().projects.first { it.path == projectB }

      assertThat(projectA.threads.map { it.id }).containsExactly("codex-prefetched-a")
      assertThat(projectBState.threads.map { it.id }).containsExactly("codex-fallback-project-b")
      assertThat(openLoadCounts[PROJECT_PATH]?.get() ?: 0).isEqualTo(0)
      assertThat(openLoadCounts[projectB]?.get() ?: 0).isEqualTo(1)
    }
  }

  @Test
  fun pendingCodexTabsWithoutKnownBaselineDoNotTriggerRebind() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val binderInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          closedRefreshInvocations.incrementAndGet()
          listOf(thread(id = "codex-polled", updatedAt = 700L, title = "Polled thread", provider = AgentSessionProvider.CODEX))
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      openPendingChatPathsProvider = { setOf(PROJECT_PATH) },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-unknown",
              pendingCreatedAtMs = null,
            )
          )
        )
      },
      openChatPendingTabSpecificBinder = { _, _, _ ->
        binderInvocations.incrementAndGet()
        false
      },
      pendingCodexRebindPollIntervalMs = 50L,
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()

      waitForCondition {
        closedRefreshInvocations.get() > 0
      }
      delay(150.milliseconds)

      assertThat(binderInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun pendingCodexPollingRebindsOnlyNewThreadIdsWhenBaselineKnown() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          closedRefreshInvocations.incrementAndGet()
          listOf(
            thread(id = "codex-existing", updatedAt = 700L, title = "Existing thread", provider = AgentSessionProvider.CODEX),
            thread(id = "codex-new", updatedAt = 800L, title = "New thread", provider = AgentSessionProvider.CODEX),
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      openPendingChatPathsProvider = { setOf(PROJECT_PATH) },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-3",
              pendingCreatedAtMs = 750L,
            )
          )
        )
      },
      openChatPendingTabSpecificBinder = { path, pendingThreadIdentity, target ->
        rebindInvocations.add(
          PendingCodexRebindInvocation(
            path = path,
            pendingThreadIdentity = pendingThreadIdentity,
            target = target,
          )
        )
        true
      },
      pendingCodexRebindPollIntervalMs = 50L,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(thread(id = "codex-existing", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()

      waitForCondition {
        closedRefreshInvocations.get() > 0 && rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-3")
      val target = invocation.target
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-new"))
      assertThat(target.threadId).isEqualTo("codex-new")
      assertThat(target.threadTitle).isEqualTo("New thread")
    }
  }

  @Test
  fun pendingCodexPollingDoesNotRebindToPreviouslyKnownThreadIds() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val binderInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          closedRefreshInvocations.incrementAndGet()
          listOf(thread(id = "codex-existing", updatedAt = 700L, title = "Existing thread", provider = AgentSessionProvider.CODEX))
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      openPendingChatPathsProvider = { setOf(PROJECT_PATH) },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-existing",
              pendingCreatedAtMs = 680L,
            )
          )
        )
      },
      openChatPendingTabSpecificBinder = { _, _, _ ->
        binderInvocations.incrementAndGet()
        false
      },
      pendingCodexRebindPollIntervalMs = 50L,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(thread(id = "codex-existing", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()

      waitForCondition {
        closedRefreshInvocations.get() > 0
      }
      delay(150.milliseconds)

      assertThat(binderInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun pendingCodexPollingRefreshScopesToPendingPathsOnly() = runBlocking(Dispatchers.Default) {
    val pendingPath = "/work/project-pending"
    val refreshedPaths = mutableListOf<String>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        synchronized(refreshedPaths) {
          refreshedPaths.add(path)
        }
        when (path) {
          pendingPath -> listOf(thread(id = "codex-polled", updatedAt = 700L, title = "Polled thread", provider = AgentSessionProvider.CODEX))
          PROJECT_PATH -> listOf(thread(id = "codex-loaded", updatedAt = 999L, title = "Loaded thread", provider = AgentSessionProvider.CODEX))
          else -> emptyList()
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH, pendingPath) },
      openPendingChatPathsProvider = { setOf(pendingPath) },
      pendingCodexRebindPollIntervalMs = 50L,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Loaded",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(thread(id = "codex-loaded", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = pendingPath,
            name = "Pending",
            isOpen = true,
            hasLoaded = false,
            threads = emptyList(),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()

      waitForCondition {
        synchronized(refreshedPaths) {
          refreshedPaths.contains(pendingPath)
        }
      }

      val refreshedPathsSnapshot = synchronized(refreshedPaths) {
        refreshedPaths.toList()
      }
      assertThat(refreshedPathsSnapshot).containsOnly(pendingPath)

      val loadedProject = stateStore.snapshot().projects.first { it.path == PROJECT_PATH }
      assertThat(
        loadedProject.threads.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt
      ).isEqualTo(100L)
    }
  }

  @Test
  fun pendingCodexPollingProjectsPendingThreadsForUnloadedPath() = runBlocking(Dispatchers.Default) {
    val pendingPath = "/work/project-pending"

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { _ ->
        emptyList()
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openPendingChatPathsProvider = { setOf(pendingPath) },
      openPendingCodexTabsProvider = {
        mapOf(
          pendingPath to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-pending",
              projectPath = pendingPath,
              pendingCreatedAtMs = 700L,
            )
          )
        )
      },
      pendingCodexRebindPollIntervalMs = 50L,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = pendingPath,
            name = "Pending",
            isOpen = true,
            hasLoaded = false,
            threads = emptyList(),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == pendingPath }
          ?.threads
          ?.any { it.provider == AgentSessionProvider.CODEX && it.id == "new-pending" } == true
      }

      val pendingProject = stateStore.snapshot().projects.first { it.path == pendingPath }
      val pendingThread = pendingProject.threads.single { it.provider == AgentSessionProvider.CODEX && it.id == "new-pending" }
      assertThat(pendingProject.hasLoaded).isFalse()
      assertThat(pendingThread.title).isEqualTo(AgentSessionsBundle.message("toolwindow.action.new.thread"))
      assertThat(pendingThread.updatedAt).isEqualTo(700L)
    }
  }

}

private data class PendingCodexRebindInvocation(
  val path: String,
  val pendingThreadIdentity: String,
  val target: AgentChatPendingTabRebindTarget,
)

private fun pendingCodexTab(
  pendingThreadIdentity: String,
  projectPath: String = PROJECT_PATH,
  pendingCreatedAtMs: Long? = null,
  pendingFirstInputAtMs: Long? = null,
  pendingLaunchMode: String? = null,
): AgentChatPendingCodexTabSnapshot {
  return AgentChatPendingCodexTabSnapshot(
    projectPath = projectPath,
    pendingThreadIdentity = pendingThreadIdentity,
    pendingCreatedAtMs = pendingCreatedAtMs,
    pendingFirstInputAtMs = pendingFirstInputAtMs,
    pendingLaunchMode = pendingLaunchMode,
  )
}

private suspend fun withLoadingCoordinator(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<ProjectEntry> = { emptyList() },
  isRefreshGateActive: suspend () -> Boolean,
  openChatPathsProvider: suspend () -> Set<String> = { emptySet() },
  openPendingChatPathsProvider: suspend () -> Set<String> = { emptySet() },
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingCodexTabSnapshot>> = { emptyMap() },
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> = { emptyMap() },
  openChatTabPresentationUpdater: suspend (
    Map<Pair<String, String>, String>,
    Map<Pair<String, String>, AgentThreadActivity>,
  ) -> Int = { _, _ -> 0 },
  openChatPendingTabSpecificBinder: (String, String, AgentChatPendingTabRebindTarget) -> Boolean = { _, _, _ -> false },
  pendingCodexRebindPollIntervalMs: Long = 1_500L,
  action: suspend (AgentSessionsLoadingCoordinator, AgentSessionsStateStore) -> Unit,
) {
  @Suppress("RAW_SCOPE_CREATION")
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val treeUiState = InMemorySessionsTreeUiState()
  val stateStore = AgentSessionsStateStore()
  try {
    val coordinator = AgentSessionsLoadingCoordinator(
      serviceScope = scope,
      sessionSourcesProvider = sessionSourcesProvider,
      projectEntriesProvider = projectEntriesProvider,
      treeUiState = treeUiState,
      stateStore = stateStore,
      isRefreshGateActive = isRefreshGateActive,
      openAgentChatProjectPathsProvider = openChatPathsProvider,
      openPendingAgentChatProjectPathsProvider = openPendingChatPathsProvider,
      openPendingCodexTabsProvider = openPendingCodexTabsProvider,
      openConcreteChatThreadIdentitiesByPathProvider = openConcreteChatThreadIdentitiesByPathProvider,
      openAgentChatTabPresentationUpdater = openChatTabPresentationUpdater,
      openAgentChatPendingTabSpecificBinder = openChatPendingTabSpecificBinder,
      pendingCodexRebindPollIntervalMs = pendingCodexRebindPollIntervalMs,
    )
    action(coordinator, stateStore)
  }
  finally {
    scope.cancel()
  }
}
