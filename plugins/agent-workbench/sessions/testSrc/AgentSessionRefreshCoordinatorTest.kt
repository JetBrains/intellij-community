// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatConcreteTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindOutcome
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.service.AgentSessionContentRepository
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshCoordinator
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmPathSnapshot
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.InMemorySessionWarmState
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionRefreshCoordinatorTest {
  @Test
  fun providerUpdateQueuedWhileRefreshIsInProgress() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val refreshStarted = CompletableDeferred<Unit>()
    val releaseRefresh = CompletableDeferred<Unit>()
    val closedRefreshInvocations = AtomicInteger(0)
    var closedThreadUpdatedAt = 100L

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
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
      updates.tryEmit(threadsChangedEvent())
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
  fun sourceUpdatesScheduleSingleDebouncedVfsRefresh() = runBlocking(Dispatchers.Default) {
    val vfsRefreshRequests = CopyOnWriteArrayList<Set<String>>()
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = flow {
        emit(threadsChangedEvent(mayHaveChangedProjectFiles = true))
        emit(hintsChangedEvent(activityUpdatesByThreadId = mapOf("codex-1" to activityUpdate(AgentThreadActivity.NEEDS_INPUT))))
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      scheduleVfsRefresh = { paths -> vfsRefreshRequests.add(paths) },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )
      coordinator.observeSessionSourceUpdates()

      waitForCondition { vfsRefreshRequests.size == 1 }
      delay(500.milliseconds)

      assertThat(vfsRefreshRequests).containsExactly(setOf(PROJECT_PATH))
    }
  }

  @Test
  fun hintSourceUpdateWithProjectFileChangeEvidenceSchedulesVfsRefresh() = runBlocking(Dispatchers.Default) {
    val vfsRefreshRequests = CopyOnWriteArrayList<Set<String>>()
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = flow {
        emit(
          hintsChangedEvent(
            scopedPaths = setOf(PROJECT_PATH),
            activityUpdatesByThreadId = mapOf("codex-1" to activityUpdate(AgentThreadActivity.READY)),
            mayHaveChangedProjectFiles = true,
          )
        )
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      scheduleVfsRefresh = { paths -> vfsRefreshRequests.add(paths) },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()

      waitForCondition { vfsRefreshRequests.size == 1 }

      assertThat(vfsRefreshRequests).containsExactly(setOf(PROJECT_PATH))
    }
  }

  @Test
  fun sourceUpdateWithProjectFileChangeEvidenceSchedulesVfsRefresh(@TempDir tempDir: Path) = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project")
    Files.createDirectories(projectPath)
    val vfsRefreshRequests = CopyOnWriteArrayList<Set<String>>()
    val closedRefreshInvocations = AtomicInteger(0)
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = flow { emit(threadsChangedEvent(mayHaveChangedProjectFiles = true)) },
      listFromClosedProject = { path ->
        if (path == projectPath.toString()) {
          closedRefreshInvocations.incrementAndGet()
        }
        listOf(thread(id = "codex-1", updatedAt = 200L, provider = AgentSessionProvider.CODEX))
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      scheduleVfsRefresh = { paths -> vfsRefreshRequests.add(paths) },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = projectPath.toString(),
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )
      coordinator.observeSessionSourceUpdates()

      waitForCondition { closedRefreshInvocations.get() == 1 }

      assertThat(vfsRefreshRequests).containsExactly(setOf(projectPath.toString()))
    }
  }

  @Test
  fun sourceUpdateWithExactProjectFileChangeEvidenceSchedulesFileVfsRefresh(@TempDir tempDir: Path) = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project")
    val changedFile = projectPath.resolve("src").resolve("Main.kt")
    Files.createDirectories(changedFile.parent)
    Files.writeString(changedFile, "fun main() {}")
    val vfsRefreshRequests = CopyOnWriteArrayList<Set<String>>()
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = flow {
        emit(
          threadsChangedEvent(
            scopedPaths = setOf(projectPath.toString()),
            mayHaveChangedProjectFiles = true,
            changedProjectFilePaths = setOf(changedFile.toString()),
          )
        )
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      scheduleVfsRefresh = { paths -> vfsRefreshRequests.add(paths) },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = projectPath.toString(),
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )
      coordinator.observeSessionSourceUpdates()

      waitForCondition { vfsRefreshRequests.size == 1 }

      assertThat(vfsRefreshRequests).containsExactly(setOf(changedFile.toString()))
    }
  }

  @Test
  fun sourceUpdateWithDeletedExactProjectFileSchedulesParentVfsRefresh(@TempDir tempDir: Path) = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project")
    val changedDirectory = projectPath.resolve("src")
    val deletedFile = changedDirectory.resolve("Deleted.kt")
    Files.createDirectories(changedDirectory)
    val vfsRefreshRequests = CopyOnWriteArrayList<Set<String>>()
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = flow {
        emit(
          threadsChangedEvent(
            scopedPaths = setOf(projectPath.toString()),
            mayHaveChangedProjectFiles = true,
            changedProjectFilePaths = setOf(deletedFile.toString()),
          )
        )
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      scheduleVfsRefresh = { paths -> vfsRefreshRequests.add(paths) },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = projectPath.toString(),
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )
      coordinator.observeSessionSourceUpdates()

      waitForCondition { vfsRefreshRequests.size == 1 }

      assertThat(vfsRefreshRequests).containsExactly(setOf(changedDirectory.toString()))
    }
  }

  @Test
  fun mergedSourceUpdateWithExactAndBroadProjectFileChangeEvidenceFallsBackToProjectVfsRefresh(
    @TempDir tempDir: Path,
  ) = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project")
    val changedFile = projectPath.resolve("src").resolve("Main.kt")
    Files.createDirectories(changedFile.parent)
    Files.writeString(changedFile, "fun main() {}")
    val vfsRefreshRequests = CopyOnWriteArrayList<Set<String>>()
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = flow {
        emit(
          threadsChangedEvent(
            scopedPaths = setOf(projectPath.toString()),
            mayHaveChangedProjectFiles = true,
            changedProjectFilePaths = setOf(changedFile.toString()),
          )
        )
        emit(
          threadsChangedEvent(
            scopedPaths = setOf(projectPath.toString()),
            mayHaveChangedProjectFiles = true,
          )
        )
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      scheduleVfsRefresh = { paths -> vfsRefreshRequests.add(paths) },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = projectPath.toString(),
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )
      coordinator.observeSessionSourceUpdates()

      waitForCondition { vfsRefreshRequests.size == 1 }

      assertThat(vfsRefreshRequests).containsExactly(setOf(projectPath.toString()))
    }
  }

  @Test
  fun exactProjectFileChangeEvidenceRespectsOwnerRootVfsRefreshConfig(@TempDir tempDir: Path) = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project")
    val changedFile = projectPath.resolve("src").resolve("Main.kt")
    Files.createDirectories(changedFile.parent)
    Files.writeString(changedFile, "fun main() {}")
    val vfsRefreshRequests = CopyOnWriteArrayList<Set<String>>()
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = flow {
        emit(
          threadsChangedEvent(
            scopedPaths = setOf(projectPath.toString()),
            mayHaveChangedProjectFiles = true,
            changedProjectFilePaths = setOf(changedFile.toString()),
          )
        )
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      scheduleVfsRefresh = { paths -> vfsRefreshRequests.add(paths) },
      isVfsRefreshOnStatusUpdatesEnabled = { path -> path != projectPath.toString() },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = projectPath.toString(),
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )
      coordinator.observeSessionSourceUpdates()

      delay(700.milliseconds)

      assertThat(vfsRefreshRequests).isEmpty()
    }
  }

  @Test
  fun sourceUpdateWithoutProjectFileChangeEvidenceSkipsVfsRefresh(@TempDir tempDir: Path) = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project")
    Files.createDirectories(projectPath)
    val vfsRefreshInvocations = AtomicInteger(0)
    val closedRefreshInvocations = AtomicInteger(0)
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = flow { emit(threadsChangedEvent()) },
      listFromClosedProject = { path ->
        if (path == projectPath.toString()) {
          closedRefreshInvocations.incrementAndGet()
        }
        listOf(thread(id = "codex-1", updatedAt = 200L, provider = AgentSessionProvider.CODEX))
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      scheduleVfsRefresh = { _ -> vfsRefreshInvocations.incrementAndGet() },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = projectPath.toString(),
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )
      coordinator.observeSessionSourceUpdates()

      waitForCondition { closedRefreshInvocations.get() == 1 }

      assertThat(vfsRefreshInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun sourceUpdateSkipsVfsRefreshWhenRuntimeConfigDisablesIt(@TempDir tempDir: Path) = runBlocking(Dispatchers.Default) {
    val projectPath = tempDir.resolve("project")
    Files.createDirectories(projectPath)
    val vfsRefreshInvocations = AtomicInteger(0)
    val closedRefreshInvocations = AtomicInteger(0)
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = flow { emit(threadsChangedEvent(mayHaveChangedProjectFiles = true)) },
      listFromClosedProject = { path ->
        if (path == projectPath.toString()) {
          closedRefreshInvocations.incrementAndGet()
        }
        listOf(thread(id = "codex-1", updatedAt = 200L, provider = AgentSessionProvider.CODEX))
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      scheduleVfsRefresh = { _ -> vfsRefreshInvocations.incrementAndGet() },
      isVfsRefreshOnStatusUpdatesEnabled = { false },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = projectPath.toString(),
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )
      coordinator.observeSessionSourceUpdates()

      waitForCondition { closedRefreshInvocations.get() == 1 }

      assertThat(vfsRefreshInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun providerUpdateDeferredUntilRefreshGateIsActive() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val gateActive = AtomicBoolean(false)
    val closedRefreshInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())
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
  fun sourceUpdateObserverRestartsAfterFailure() = runBlocking(Dispatchers.Default) {
    val recoveredUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val sourceProviderCalls = AtomicInteger(0)
    val closedRefreshInvocations = AtomicInteger(0)

    fun source(updateEvents: kotlinx.coroutines.flow.Flow<AgentSessionSourceUpdateEvent>): ScriptedSessionSource {
      return ScriptedSessionSource(
        provider = AgentSessionProvider.CODEX,
        supportsUpdates = true,
        updateEvents = updateEvents,
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
    }

    withLoadingCoordinator(
      sessionSourcesProvider = {
        val call = sourceProviderCalls.incrementAndGet()
        val updates = if (call == 1) {
          flow {
            throw IllegalStateException("source updates failed")
          }
        }
        else {
          recoveredUpdates
        }
        listOf(source(updates))
      },
      isRefreshGateActive = { true },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()

      waitForCondition(timeoutMs = 4_000) {
        sourceProviderCalls.get() >= 2
      }

      recoveredUpdates.tryEmit(threadsChangedEvent())

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
  fun scopedRefreshMergedWithThreadTargetedUpdateDoesNotWidenToOtherPaths() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val gateActive = AtomicBoolean(false)
    val closedRefreshInvocations = LinkedHashMap<String, AtomicInteger>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        closedRefreshInvocations.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        when (path) {
          PROJECT_PATH -> listOf(thread(id = "codex-a", updatedAt = 300L, provider = AgentSessionProvider.CODEX))
          projectB -> listOf(thread(id = "codex-b", updatedAt = 400L, provider = AgentSessionProvider.CODEX))
          else -> emptyList()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = projectB,
            name = "Project B",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-b", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      coordinator.refreshProviderScope(provider = AgentSessionProvider.CODEX, scopedPaths = setOf(PROJECT_PATH))
      updates.tryEmit(hintsChangedEvent(threadIds = setOf("codex-a")))
      delay(900.milliseconds)

      assertThat(closedRefreshInvocations[PROJECT_PATH]?.get() ?: 0).isEqualTo(0)
      assertThat(closedRefreshInvocations[projectB]?.get() ?: 0).isEqualTo(0)

      gateActive.set(true)

      waitForCondition {
        val projects = stateStore.snapshot().projects.associateBy { it.path }
        projects[PROJECT_PATH]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt == 300L &&
        projects[projectB]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt == 100L
      }

      assertThat(closedRefreshInvocations[PROJECT_PATH]?.get() ?: 0).isEqualTo(1)
      assertThat(closedRefreshInvocations[projectB]?.get() ?: 0).isEqualTo(0)
    }
  }

  @Test
  fun threadTargetedUpdateSkipsRefreshWhenThreadCannotBeResolved() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val closedRefreshInvocations = LinkedHashMap<String, AtomicInteger>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        closedRefreshInvocations.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        when (path) {
          PROJECT_PATH -> listOf(thread(id = "codex-a", updatedAt = 300L, provider = AgentSessionProvider.CODEX))
          projectB -> listOf(thread(id = "codex-b", updatedAt = 400L, provider = AgentSessionProvider.CODEX))
          else -> emptyList()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = projectB,
            name = "Project B",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-b", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(hintsChangedEvent(threadIds = setOf("missing-thread")))

      delay(700.milliseconds)

      val projects = stateStore.snapshot().projects.associateBy { it.path }
      assertThat(projects[PROJECT_PATH]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt).isEqualTo(100L)
      assertThat(projects[projectB]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt).isEqualTo(100L)
      assertThat(closedRefreshInvocations[PROJECT_PATH]?.get() ?: 0).isEqualTo(0)
      assertThat(closedRefreshInvocations[projectB]?.get() ?: 0).isEqualTo(0)
    }
  }

  @Test
  fun scopedUpdateRemapsCanonicalPathToLoadedSymlinkPath(@TempDir tempDir: Path) = runBlocking(Dispatchers.Default) {
    val realProjectPath = tempDir.resolve("real-project")
    Files.createDirectories(realProjectPath)
    val linkedProjectPath = tempDir.resolve("linked-project")
    createSymbolicLinkOrSkip(link = linkedProjectPath, target = realProjectPath)
    val loadedPath = linkedProjectPath.toString()
    val canonicalPath = realProjectPath.toRealPath().toString()
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val closedRefreshInvocations = LinkedHashMap<String, AtomicInteger>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        closedRefreshInvocations.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        if (path == loadedPath) {
          listOf(thread(id = "codex-a", updatedAt = 300L, provider = AgentSessionProvider.CODEX))
        }
        else {
          emptyList()
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
            path = loadedPath,
            name = "Linked Project",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent(scopedPaths = setOf(canonicalPath), threadIds = setOf("codex-a")))

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == loadedPath }
          ?.threads
          ?.firstOrNull { it.provider == AgentSessionProvider.CODEX }
          ?.updatedAt == 300L
      }

      assertThat(closedRefreshInvocations[loadedPath]?.get() ?: 0).isEqualTo(1)
      assertThat(closedRefreshInvocations[canonicalPath]?.get() ?: 0).isEqualTo(0)
    }
  }

  @Test
  fun unmappedScopedUpdateDoesNotFallBackToFullLoadedRefresh() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val closedRefreshInvocations = LinkedHashMap<String, AtomicInteger>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        closedRefreshInvocations.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        when (path) {
          PROJECT_PATH -> listOf(thread(id = "codex-a", updatedAt = 300L, provider = AgentSessionProvider.CODEX))
          projectB -> listOf(thread(id = "codex-b", updatedAt = 400L, provider = AgentSessionProvider.CODEX))
          else -> emptyList()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = projectB,
            name = "Project B",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-b", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent(scopedPaths = setOf("/missing/project")))

      delay(700.milliseconds)

      val projects = stateStore.snapshot().projects.associateBy { it.path }
      assertThat(projects[PROJECT_PATH]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt).isEqualTo(100L)
      assertThat(projects[projectB]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt).isEqualTo(100L)
      assertThat(closedRefreshInvocations[PROJECT_PATH]?.get() ?: 0).isEqualTo(0)
      assertThat(closedRefreshInvocations[projectB]?.get() ?: 0).isEqualTo(0)
      assertThat(closedRefreshInvocations["/missing/project"]?.get() ?: 0).isEqualTo(0)
    }
  }

  @Test
  fun scopedHintUpdateWithoutHintsDoesNotReloadProviderPath() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val closedRefreshInvocations = LinkedHashMap<String, AtomicInteger>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        closedRefreshInvocations.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        when (path) {
          PROJECT_PATH -> listOf(thread(id = "codex-a", updatedAt = 300L, provider = AgentSessionProvider.CODEX))
          projectB -> listOf(thread(id = "codex-b", updatedAt = 400L, provider = AgentSessionProvider.CODEX))
          else -> emptyList()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = projectB,
            name = "Project B",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-b", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(hintsChangedEvent(scopedPaths = setOf(PROJECT_PATH), threadIds = setOf("codex-a")))

      delay(700.milliseconds)

      val projects = stateStore.snapshot().projects.associateBy { it.path }
      assertThat(projects[PROJECT_PATH]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt).isEqualTo(100L)
      assertThat(projects[projectB]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt).isEqualTo(100L)
      assertThat(closedRefreshInvocations[PROJECT_PATH]?.get() ?: 0).isEqualTo(0)
      assertThat(closedRefreshInvocations[projectB]?.get() ?: 0).isEqualTo(0)
    }
  }

  @Test
  fun repeatedScopedForkRefreshKeepsOriginalAndAddsRenamedFork() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 2)
    val closedRefreshInvocations = LinkedHashMap<String, AtomicInteger>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        val invocation = closedRefreshInvocations.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        when (path) {
          PROJECT_PATH -> {
            val original = thread(
              id = "codex-original",
              updatedAt = 100L,
              title = "Original thread",
              provider = AgentSessionProvider.CODEX,
            )
            if (invocation == 1) {
              listOf(original)
            }
            else {
              listOf(
                original,
                thread(
                  id = "codex-fork",
                  updatedAt = 300L,
                  title = "Renamed fork",
                  provider = AgentSessionProvider.CODEX,
                ),
              )
            }
          }
          projectB -> listOf(
            thread(id = "codex-b", updatedAt = 400L, title = "Project B updated", provider = AgentSessionProvider.CODEX)
          )
          else -> emptyList()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(
                id = "codex-original",
                updatedAt = 100L,
                title = "Original thread",
                provider = AgentSessionProvider.CODEX,
              )
            ),
          ),
          AgentProjectSessions(
            path = projectB,
            name = "Project B",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-b", updatedAt = 100L, title = "Project B stable", provider = AgentSessionProvider.CODEX)
            ),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent(scopedPaths = setOf(PROJECT_PATH)))

      waitForCondition {
        closedRefreshInvocations[PROJECT_PATH]?.get() == 1
      }
      assertThat(
        stateStore.snapshot().projects.single { it.path == PROJECT_PATH }.threads.map { it.id }
      ).containsExactly("codex-original")

      updates.tryEmit(threadsChangedEvent(scopedPaths = setOf(PROJECT_PATH)))

      waitForCondition {
        val projects = stateStore.snapshot().projects.associateBy { it.path }
        val projectAThreads = projects[PROJECT_PATH]?.threads.orEmpty().associateBy { it.id }
        projectAThreads["codex-original"]?.title == "Original thread" &&
        projectAThreads["codex-fork"]?.title == "Renamed fork" &&
        projects[projectB]?.threads?.singleOrNull()?.title == "Project B stable"
      }

      val projects = stateStore.snapshot().projects.associateBy { it.path }
      val projectAThreads = projects.getValue(PROJECT_PATH).threads
      assertThat(projectAThreads.map { it.id }).containsExactly("codex-fork", "codex-original")
      assertThat(projectAThreads.single { it.id == "codex-original" }.title).isEqualTo("Original thread")
      assertThat(projectAThreads.single { it.id == "codex-fork" }.title).isEqualTo("Renamed fork")
      assertThat(projects.getValue(projectB).threads.single().title).isEqualTo("Project B stable")
      assertThat(closedRefreshInvocations[PROJECT_PATH]?.get() ?: 0).isEqualTo(2)
      assertThat(closedRefreshInvocations[projectB]?.get() ?: 0).isEqualTo(0)
    }
  }

  @Test
  fun scopedThreadsChangedUpdateRefreshesOnlyScopedPathForClaude() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val closedRefreshInvocations = LinkedHashMap<String, AtomicInteger>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        closedRefreshInvocations.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        when (path) {
          PROJECT_PATH -> listOf(thread(id = "claude-a", updatedAt = 300L, provider = AgentSessionProvider.CLAUDE))
          projectB -> listOf(thread(id = "claude-b", updatedAt = 400L, provider = AgentSessionProvider.CLAUDE))
          else -> emptyList()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CLAUDE),
            threads = listOf(thread(id = "claude-a", updatedAt = 100L, provider = AgentSessionProvider.CLAUDE)),
          ),
          AgentProjectSessions(
            path = projectB,
            name = "Project B",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CLAUDE),
            threads = listOf(thread(id = "claude-b", updatedAt = 100L, provider = AgentSessionProvider.CLAUDE)),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent(scopedPaths = setOf(PROJECT_PATH), threadIds = setOf("claude-a")))

      waitForCondition {
        val projects = stateStore.snapshot().projects.associateBy { it.path }
        projects[PROJECT_PATH]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CLAUDE }?.updatedAt == 300L &&
        projects[projectB]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CLAUDE }?.updatedAt == 100L
      }

      assertThat(closedRefreshInvocations[PROJECT_PATH]?.get() ?: 0).isEqualTo(1)
      assertThat(closedRefreshInvocations[projectB]?.get() ?: 0).isEqualTo(0)
    }
  }

  @Test
  fun threadTargetedUpdateUsesOpenConcreteTabIdentityWhenStateDoesNotContainThread() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val closedRefreshInvocations = LinkedHashMap<String, AtomicInteger>()
    val presentationModel = AgentSessionThreadPresentationModel()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        closedRefreshInvocations.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        when (path) {
          PROJECT_PATH -> listOf(thread(id = "codex-a", updatedAt = 300L, provider = AgentSessionProvider.CODEX))
          projectB -> listOf(
            thread(
              id = "codex-open",
              updatedAt = 500L,
              title = "Open concrete tab thread",
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.UNREAD,
            )
          )
          else -> emptyList()
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openConcreteChatThreadIdentitiesByPathProvider = {
        mapOf(projectB to setOf(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-open")))
      },
      presentationModel = presentationModel,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(hintsChangedEvent(threadIds = setOf("codex-open")))

      val expectedKey = presentationKey(projectB, AgentSessionProvider.CODEX, "codex-open")
      waitForCondition {
        presentationModel.snapshot()[expectedKey]?.title == "Open concrete tab thread" &&
        presentationModel.snapshot()[expectedKey]?.activity == AgentThreadActivity.UNREAD
      }

      assertThat(closedRefreshInvocations[PROJECT_PATH]?.get() ?: 0).isEqualTo(0)
      assertThat(closedRefreshInvocations[projectB]?.get() ?: 0).isEqualTo(1)
      assertThat(
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.provider == AgentSessionProvider.CODEX }
          ?.updatedAt
      ).isEqualTo(100L)
    }
  }

  @Test
  fun threadTargetedHintUpdateMarksOnlyChangedThreadSeedForForceRefresh() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val capturedRefreshThreadSeedsByPath = mutableListOf<Map<String, Set<AgentSessionRefreshThreadSeed>>>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(id = "codex-a", updatedAt = 300L, provider = AgentSessionProvider.CODEX),
            thread(id = "codex-b", updatedAt = 250L, provider = AgentSessionProvider.CODEX),
          )
        }
      },
      prefetchRefreshThreadSeedsProvider = { paths, refreshThreadSeedsByPath ->
        if (PROJECT_PATH in paths) {
          capturedRefreshThreadSeedsByPath += refreshThreadSeedsByPath.mapValues { (_, refreshThreadSeeds) -> refreshThreadSeeds.toSet() }
        }
        emptyMap()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX),
              thread(id = "codex-b", updatedAt = 90L, provider = AgentSessionProvider.CODEX),
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(hintsChangedEvent(threadIds = setOf("codex-a")))

      waitForCondition {
        capturedRefreshThreadSeedsByPath.isNotEmpty()
      }

      val refreshThreadSeedsById = capturedRefreshThreadSeedsByPath.last().getValue(PROJECT_PATH).associateBy { it.threadId }
      assertThat(refreshThreadSeedsById.keys).containsExactlyInAnyOrder("codex-a", "codex-b")
      assertThat(refreshThreadSeedsById["codex-a"])
        .isEqualTo(AgentSessionRefreshThreadSeed(threadId = "codex-a", updatedAt = 100L, forceRefresh = true))
      assertThat(refreshThreadSeedsById["codex-b"])
        .isEqualTo(AgentSessionRefreshThreadSeed(threadId = "codex-b", updatedAt = 90L, forceRefresh = false))
    }
  }

  @Test
  fun providerRefreshCollectsOpenChatSnapshotOncePerPass() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val openChatSnapshotInvocations = AtomicInteger(0)

    withLoadingCoordinator(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            listFromClosedProject = { path ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                closedRefreshInvocations.incrementAndGet()
                listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX))
              }
            },
          )
        )
      },
      isRefreshGateActive = { true },
      openAgentChatSnapshotProvider = {
        openChatSnapshotInvocations.incrementAndGet()
        buildOpenChatRefreshSnapshot(openProjectPaths = setOf(PROJECT_PATH))
      },
    ) { coordinator, _ ->
      coordinator.refreshProviderScope(provider = AgentSessionProvider.CODEX, scopedPaths = setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() == 1 && openChatSnapshotInvocations.get() == 1
      }

      assertThat(openChatSnapshotInvocations.get()).isEqualTo(1)
      assertThat(closedRefreshInvocations.get()).isEqualTo(1)
    }
  }

  @Test
  fun providerRefreshAppliesScopedOutcomeToOpenProjectThatIsNotFullyLoaded() = runBlocking(Dispatchers.Default) {
    val claudeRefreshInvocations = AtomicInteger(0)
    val piThread = thread(id = "pi-1", updatedAt = 100L, provider = AgentSessionProvider.PI)
    val claudeThread = thread(id = "claude-1", updatedAt = 200L, provider = AgentSessionProvider.CLAUDE)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          claudeRefreshInvocations.incrementAndGet()
          listOf(claudeThread)
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
            threads = listOf(piThread),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.refreshProviderScope(provider = AgentSessionProvider.CLAUDE, scopedPaths = setOf(PROJECT_PATH))

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.any { it.provider == AgentSessionProvider.CLAUDE && it.id == "claude-1" } == true
      }

      val project = stateStore.snapshot().projects.single { it.path == PROJECT_PATH }
      assertThat(claudeRefreshInvocations.get()).isEqualTo(1)
      assertThat(project.providerLoadStates).doesNotContainKey(AgentSessionProvider.PI)
      assertThat(project.providerLoadStates[AgentSessionProvider.CLAUDE]).isEqualTo(AgentSessionProviderLoadState.LOADED)
      assertThat(project.threads.map { it.provider to it.id })
        .containsExactlyInAnyOrder(
          AgentSessionProvider.PI to "pi-1",
          AgentSessionProvider.CLAUDE to "claude-1",
        )
    }
  }

  @Test
  fun providerRefreshAppliesScopedOutcomeWhenDifferentProviderIsAlreadyLoaded() = runBlocking(Dispatchers.Default) {
    val piRefreshInvocations = AtomicInteger(0)
    val claudeThread = thread(id = "claude-1", updatedAt = 100L, provider = AgentSessionProvider.CLAUDE)
    val piThread = thread(id = "pi-1", updatedAt = 200L, provider = AgentSessionProvider.PI)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.PI,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          piRefreshInvocations.incrementAndGet()
          listOf(piThread)
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
            threads = listOf(claudeThread),
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CLAUDE),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.refreshProviderScope(provider = AgentSessionProvider.PI, scopedPaths = setOf(PROJECT_PATH))

      waitForCondition {
        val project = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.PI] == AgentSessionProviderLoadState.LOADED &&
        project.providerLoadStates[AgentSessionProvider.CLAUDE] == AgentSessionProviderLoadState.LOADED &&
        project.threads.any { it.provider == AgentSessionProvider.PI && it.id == "pi-1" } &&
        project.threads.any { it.provider == AgentSessionProvider.CLAUDE && it.id == "claude-1" }
      }

      val project = stateStore.snapshot().projects.single { it.path == PROJECT_PATH }
      assertThat(piRefreshInvocations.get()).isEqualTo(1)
      assertThat(project.providerLoadStates[AgentSessionProvider.PI]).isEqualTo(AgentSessionProviderLoadState.LOADED)
      assertThat(project.providerLoadStates[AgentSessionProvider.CLAUDE]).isEqualTo(AgentSessionProviderLoadState.LOADED)
      assertThat(project.threads.map { it.provider to it.id })
        .containsExactlyInAnyOrder(
          AgentSessionProvider.CLAUDE to "claude-1",
          AgentSessionProvider.PI to "pi-1",
        )
    }
  }

  @Test
  fun providerRefreshAppliesScopedOutcomeToOpenWorktreeThatIsNotFullyLoaded() = runBlocking(Dispatchers.Default) {
    val claudeRefreshInvocations = AtomicInteger(0)
    val piThread = thread(id = "wt-pi-1", updatedAt = 100L, provider = AgentSessionProvider.PI)
    val claudeThread = thread(id = "wt-claude-1", updatedAt = 200L, provider = AgentSessionProvider.CLAUDE)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromClosedProject = { path ->
        if (path != WORKTREE_PATH) {
          emptyList()
        }
        else {
          claudeRefreshInvocations.incrementAndGet()
          listOf(claudeThread)
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
            isOpen = false,
            worktrees = listOf(
              AgentWorktree(
                path = WORKTREE_PATH,
                name = "project-feature",
                branch = null,
                isOpen = true,
                threads = listOf(piThread),
              )
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.refreshProviderScope(provider = AgentSessionProvider.CLAUDE, scopedPaths = setOf(WORKTREE_PATH))

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.worktrees
          ?.firstOrNull { it.path == WORKTREE_PATH }
          ?.threads
          ?.any { it.provider == AgentSessionProvider.CLAUDE && it.id == "wt-claude-1" } == true
      }

      val project = stateStore.snapshot().projects.single { it.path == PROJECT_PATH }
      val worktree = project.worktrees.single { it.path == WORKTREE_PATH }
      assertThat(claudeRefreshInvocations.get()).isEqualTo(1)
      assertThat(project.providerLoadStates).isEmpty()
      assertThat(worktree.providerLoadStates).doesNotContainKey(AgentSessionProvider.PI)
      assertThat(worktree.providerLoadStates[AgentSessionProvider.CLAUDE]).isEqualTo(AgentSessionProviderLoadState.LOADED)
      assertThat(worktree.threads.map { it.provider to it.id })
        .containsExactlyInAnyOrder(
          AgentSessionProvider.PI to "wt-pi-1",
          AgentSessionProvider.CLAUDE to "wt-claude-1",
        )
    }
  }

  @Test
  fun fullRefreshTracksProviderLoadStatesWhileOtherProviderIsStillLoading() = runBlocking(Dispatchers.Default) {
    val claudeLoadStarted = CompletableDeferred<Unit>()
    val releaseClaudeLoad = CompletableDeferred<Unit>()

    val codexSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX))
        }
      },
    )
    val claudeSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromOpenProject = { path, _ ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          claudeLoadStarted.complete(Unit)
          releaseClaudeLoad.await()
          listOf(thread(id = "claude-1", updatedAt = 200L, provider = AgentSessionProvider.CLAUDE))
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(codexSource, claudeSource) },
      projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
      isRefreshGateActive = { true },
    ) { coordinator, stateStore ->
      coordinator.refresh()
      claudeLoadStarted.await()

      waitForCondition {
        val project = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
        project.providerLoadStates[AgentSessionProvider.CLAUDE] == AgentSessionProviderLoadState.LOADING &&
        project.threads.any { it.provider == AgentSessionProvider.CODEX && it.id == "codex-1" }
      }

      releaseClaudeLoad.complete(Unit)

      waitForCondition {
        val project = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
        project.providerLoadStates[AgentSessionProvider.CLAUDE] == AgentSessionProviderLoadState.LOADED
      }

      val project = stateStore.snapshot().projects.single { it.path == PROJECT_PATH }
      assertThat(project.providerLoadStates)
        .containsEntry(AgentSessionProvider.CODEX, AgentSessionProviderLoadState.LOADED)
        .containsEntry(AgentSessionProvider.CLAUDE, AgentSessionProviderLoadState.LOADED)
      assertThat(project.threads.map { it.id }).containsExactly("claude-1", "codex-1")
    }
  }

  @Test
  fun providerRefreshDoesNotTouchStateTimestampWhenOutcomeMatchesState() = runBlocking(Dispatchers.Default) {
    val codexRefreshInvocations = AtomicInteger(0)
    val completionRefreshInvocations = AtomicInteger(0)
    val existingThread = thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)

    withLoadingCoordinator(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            listFromClosedProject = { path ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                codexRefreshInvocations.incrementAndGet()
                listOf(existingThread)
              }
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                completionRefreshInvocations.incrementAndGet()
              }
              emptyList()
            },
          )
        )
      },
      isRefreshGateActive = { true },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            threads = listOf(existingThread),
            providerLoadStates = mapOf(
              AgentSessionProvider.CODEX to AgentSessionProviderLoadState.LOADED,
              AgentSessionProvider.CLAUDE to AgentSessionProviderLoadState.LOADED,
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )
      val lastUpdatedAtBeforeRefresh = stateStore.snapshot().lastUpdatedAt

      coordinator.refreshProviderScope(provider = AgentSessionProvider.CODEX, scopedPaths = setOf(PROJECT_PATH))
      coordinator.refreshProviderScope(provider = AgentSessionProvider.CLAUDE, scopedPaths = setOf(PROJECT_PATH))

      waitForCondition { completionRefreshInvocations.get() == 1 }
      assertThat(codexRefreshInvocations.get()).isEqualTo(1)
      assertThat(stateStore.snapshot().lastUpdatedAt).isEqualTo(lastUpdatedAtBeforeRefresh)
    }
  }

  @Test
  fun providerUpdateIgnoredWhenSourceDoesNotSupportUpdates() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val closedRefreshInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      updateEvents = updates,
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())
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
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    var updatedTitle = "Initial title"
    val presentationModel = AgentSessionThreadPresentationModel()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
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
      presentationModel = presentationModel,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, title = "Initial title", provider = AgentSessionProvider.CODEX)
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updatedTitle = "Renamed from source update"
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.provider == AgentSessionProvider.CODEX }
          ?.title == "Renamed from source update"
      }

      val expectedKey = presentationKey(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1")
      waitForCondition {
        presentationModel.snapshot()[expectedKey]?.title == "Renamed from source update" &&
        presentationModel.snapshot()[expectedKey]?.activity == AgentThreadActivity.READY
      }
    }
  }

  @Test
  fun providerUpdatePublishesProviderTitleInStateAndSharedPresentation() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val presentationModel = AgentSessionThreadPresentationModel()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(id = "codex-1", updatedAt = 300L, title = "Automatic title", provider = AgentSessionProvider.CODEX)
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      presentationModel = presentationModel,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, title = "User title", provider = AgentSessionProvider.CODEX)
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        val thread = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.provider == AgentSessionProvider.CODEX }
        thread?.updatedAt == 300L && thread.title == "Automatic title"
      }

      val expectedKey = presentationKey(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1")
      waitForCondition {
        presentationModel.snapshot()[expectedKey]?.title == "Automatic title"
      }
    }
  }

  @Test
  fun warningOnlyProviderRefreshDoesNotEvictExistingSharedPresentationScope() = runBlocking(Dispatchers.Default) {
    val presentationModel = AgentSessionThreadPresentationModel()
    presentationModel.updateThread(
      path = PROJECT_PATH,
      provider = AgentSessionProvider.CODEX,
      threadId = "codex-1",
      title = "Existing title",
      activity = AgentThreadActivity.UNREAD,
    )

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          throw IllegalStateException("codex failed")
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      presentationModel = presentationModel,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(
                id = "codex-1",
                updatedAt = 100L,
                title = "Existing title",
                provider = AgentSessionProvider.CODEX,
                activity = AgentThreadActivity.UNREAD,
              )
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.refreshProviderScope(provider = AgentSessionProvider.CODEX, scopedPaths = setOf(PROJECT_PATH))

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.providerWarnings
          ?.singleOrNull()
          ?.provider == AgentSessionProvider.CODEX
      }
      val project = stateStore.snapshot().projects.single { it.path == PROJECT_PATH }
      assertThat(project.threads).hasSize(1)
      assertThat(project.threads.single().id).isEqualTo("codex-1")
      assertThat(project.threads.single().title).isEqualTo("Existing title")
      assertThat(project.threads.single().activity).isEqualTo(AgentThreadActivity.UNREAD)
      assertThat(project.providerWarnings).hasSize(1)
      assertThat(project.providerWarnings.single().provider).isEqualTo(AgentSessionProvider.CODEX)
      val presentation = presentationModel.snapshot()[presentationKey(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1")]
      assertThat(presentation?.title).isEqualTo("Existing title")
      assertThat(presentation?.activity).isEqualTo(AgentThreadActivity.UNREAD)
    }
  }

  @Test
  fun providerRefreshKeepsHealthyPathWhenAnotherPathFails() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromClosedProject = { path ->
        when (path) {
          PROJECT_PATH -> throw IllegalStateException("codex failed for project A")
          projectB -> listOf(
            thread(id = "codex-b", updatedAt = 500L, title = "Updated B", provider = AgentSessionProvider.CODEX)
          )
          else -> emptyList()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-a", updatedAt = 100L, title = "Stable A", provider = AgentSessionProvider.CODEX)
            ),
          ),
          AgentProjectSessions(
            path = projectB,
            name = "Project B",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-b", updatedAt = 100L, title = "Stable B", provider = AgentSessionProvider.CODEX)
            ),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.refreshProviderScope(
        provider = AgentSessionProvider.CODEX,
        scopedPaths = linkedSetOf(PROJECT_PATH, projectB),
      )

      waitForCondition {
        val projects = stateStore.snapshot().projects.associateBy { it.path }
        projects[PROJECT_PATH]?.providerWarnings?.singleOrNull()?.provider == AgentSessionProvider.CODEX &&
        projects[projectB]?.threads?.singleOrNull()?.title == "Updated B"
      }

      val projects = stateStore.snapshot().projects.associateBy { it.path }
      val projectAState = projects.getValue(PROJECT_PATH)
      val projectBState = projects.getValue(projectB)
      assertThat(projectAState.threads.single().title).isEqualTo("Stable A")
      assertThat(projectAState.providerWarnings).hasSize(1)
      assertThat(projectAState.providerWarnings.single().provider).isEqualTo(AgentSessionProvider.CODEX)
      assertThat(projectBState.threads.single().title).isEqualTo("Updated B")
      assertThat(projectBState.providerWarnings).isEmpty()
    }
  }

  @Test
  fun providerUpdateBuildsPendingTabRebindTargetsForCodex() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
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
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-1")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-1")
      val target = invocation.target
      assertThat(target.projectPath).isEqualTo(PROJECT_PATH)
      assertThat(target.provider).isEqualTo(AgentSessionProvider.CODEX)
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-2"))
      assertThat(target.threadId).isEqualTo("codex-2")
      assertThat(target.threadTitle).isEqualTo("New Codex thread")
      assertThat(target.threadActivity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun providerUpdateBuildsPendingTabRebindTargetsForClaude() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(thread(id = "claude-2", updatedAt = 300L, title = "New Claude thread", provider = AgentSessionProvider.CLAUDE))
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openAgentChatSnapshotProvider = {
        buildOpenChatRefreshSnapshot(
          openProjectPaths = setOf(PROJECT_PATH),
          pendingTabsByProvider = mapOf(
            AgentSessionProvider.CLAUDE to mapOf(
              PROJECT_PATH to listOf(
                pendingCodexTab(
                  pendingThreadIdentity = "claude:new-1",
                  pendingCreatedAtMs = 200L,
                )
              )
            )
          ),
        )
      },
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CLAUDE),
            threads = listOf(thread(id = "claude-1", updatedAt = 100L, provider = AgentSessionProvider.CLAUDE)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingTabKey).isEqualTo("pending-claude:new-1")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("claude:new-1")
      val target = invocation.target
      assertThat(target.projectPath).isEqualTo(PROJECT_PATH)
      assertThat(target.provider).isEqualTo(AgentSessionProvider.CLAUDE)
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "claude-2"))
      assertThat(target.threadId).isEqualTo("claude-2")
      assertThat(target.threadTitle).isEqualTo("New Claude thread")
      assertThat(target.threadActivity).isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun providerUpdateRebindsOnlyToNewThreadIdsForCodex() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
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
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-2")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-2")
      val target = invocation.target
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-2"))
      assertThat(target.threadId).isEqualTo("codex-2")
    }
  }

  @Test
  fun providerUpdateUsesRefreshHintsForCodexRebindWithoutNewProviderIds() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(id = "codex-existing", updatedAt = 500L, title = "Existing thread", provider = AgentSessionProvider.CODEX)
          )
        }
      },
      prefetchRefreshHintsProvider = { paths, knownThreadIdsByPath ->
        if (PROJECT_PATH !in paths || PROJECT_PATH !in knownThreadIdsByPath) {
          emptyMap()
        }
        else {
          mapOf(
            PROJECT_PATH to AgentSessionRefreshHints(
              rebindCandidates = listOf(
                AgentSessionRebindCandidate(
                  threadId = "codex-hint",
                  title = "Hint-discovered thread",
                  updatedAt = 760L,
                  activity = AgentThreadActivity.UNREAD,
                )
              ),
            )
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
              pendingThreadIdentity = "codex:new-hint",
              pendingCreatedAtMs = 750L,
            )
          )
        )
      },
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-existing", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-hint")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-hint")
      assertThat(invocation.target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-hint"))
      assertThat(invocation.target.threadId).isEqualTo("codex-hint")
      assertThat(invocation.target.threadTitle).isEqualTo("Hint-discovered thread")

      val codexThreadIds = stateStore.snapshot().projects
        .first { it.path == PROJECT_PATH }
        .threads
        .filter { it.provider == AgentSessionProvider.CODEX }
        .map { it.id }
      assertThat(codexThreadIds).containsExactly("codex-existing")
      assertThat(codexThreadIds).doesNotContain("new-hint", "codex-hint")
    }
  }

  @Test
  fun hintSourceUpdateWithThreadIdsUsesRefreshHintsForCodexRebindWhenActivityHintsAvailable() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()
    val prefetchRefreshHintsInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      prefetchRefreshHintsProvider = { paths, _ ->
        prefetchRefreshHintsInvocations.incrementAndGet()
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          mapOf(
            PROJECT_PATH to AgentSessionRefreshHints(
              rebindCandidates = listOf(
                AgentSessionRebindCandidate(
                  threadId = "codex-hint",
                  title = "Hint-discovered thread",
                  updatedAt = 760L,
                  activity = AgentThreadActivity.UNREAD,
                )
              ),
            )
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
              pendingThreadIdentity = "codex:new-hint",
              pendingCreatedAtMs = 750L,
            )
          )
        )
      },
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-existing", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(
        hintsChangedEvent(
          scopedPaths = setOf(PROJECT_PATH),
          threadIds = setOf("codex-hint"),
          activityUpdatesByThreadId = mapOf("codex-hint" to activityUpdate(AgentThreadActivity.UNREAD)),
        )
      )

      waitForCondition {
        rebindInvocations.isNotEmpty()
      }

      assertThat(prefetchRefreshHintsInvocations.get()).isGreaterThan(0)
      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-hint")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-hint")
      assertThat(invocation.target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-hint"))
      assertThat(invocation.target.threadId).isEqualTo("codex-hint")
    }
  }

  @Test
  fun providerUpdateAppliesCodexActivityHintsWhenAvailable() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(
              id = "codex-with-hint",
              updatedAt = 500L,
              title = "Thread with hint",
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.PROCESSING,
            ),
            thread(
              id = "codex-without-hint",
              updatedAt = 450L,
              title = "Thread without hint",
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.UNREAD,
            ),
          )
        }
      },
      prefetchRefreshHintsProvider = { paths, _ ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          mapOf(
            PROJECT_PATH to AgentSessionRefreshHints(
              activityUpdatesByThreadId = mapOf(
                "codex-with-hint" to activityUpdate(AgentThreadActivity.REVIEWING),
              )
            )
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-with-hint", updatedAt = 100L, provider = AgentSessionProvider.CODEX),
              thread(id = "codex-without-hint", updatedAt = 90L, provider = AgentSessionProvider.CODEX),
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        val threadsById = stateStore.snapshot().projects
                            .firstOrNull { it.path == PROJECT_PATH }
                            ?.threads
                            ?.associateBy { it.id }
                          ?: return@waitForCondition false
        threadsById["codex-with-hint"]?.activity == AgentThreadActivity.REVIEWING &&
        threadsById["codex-without-hint"]?.activity == AgentThreadActivity.UNREAD
      }
    }
  }

  @Test
  fun providerUpdateAuthoritativeActivityHintsUpdateLoadedThreadBeforeRefreshGateOpens() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val gateActive = AtomicBoolean(false)
    val closedRefreshInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path == PROJECT_PATH) {
          closedRefreshInvocations.incrementAndGet()
        }
        emptyList()
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CLAUDE),
            threads = listOf(
              thread(id = "claude-1", updatedAt = 100L, provider = AgentSessionProvider.CLAUDE, activity = AgentThreadActivity.READY)
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(
        threadsChangedEvent(
          scopedPaths = setOf(PROJECT_PATH),
          threadIds = setOf("claude-1"),
          activityUpdatesByThreadId = mapOf("claude-1" to activityUpdate(AgentThreadActivity.UNREAD)),
        )
      )

      waitForCondition {
        val thread = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
                       ?.threads
                       ?.firstOrNull { it.id == "claude-1" }
                     ?: return@waitForCondition false
        thread.activity == AgentThreadActivity.UNREAD && thread.summaryActivity == AgentThreadActivity.UNREAD
      }

      assertThat(closedRefreshInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun providerUpdateActivityHintsUpdateLoadedThreadBeforeRefreshGateOpens() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val gateActive = AtomicBoolean(false)
    val closedRefreshInvocations = AtomicInteger(0)
    val presentationModel = AgentSessionThreadPresentationModel()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path == PROJECT_PATH) {
          closedRefreshInvocations.incrementAndGet()
        }
        emptyList()
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { gateActive.get() },
      presentationModel = presentationModel,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX, activity = AgentThreadActivity.READY)
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(
        threadsChangedEvent(
          scopedPaths = setOf(PROJECT_PATH),
          threadIds = setOf("codex-1"),
          activityUpdatesByThreadId = mapOf("codex-1" to activityUpdate(AgentThreadActivity.PROCESSING,
                                                                        chromeActivity = null,
                                                                        updatedAt = 200L)),
        )
      )

      waitForCondition {
        val thread = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
                       ?.threads
                       ?.firstOrNull { it.id == "codex-1" }
                     ?: return@waitForCondition false
        thread.activity == AgentThreadActivity.PROCESSING && thread.summaryActivity == null && thread.updatedAt == 200L
      }

      val expectedKey = presentationKey(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1")
      waitForCondition {
        val presentation = presentationModel.snapshot()[expectedKey] ?: return@waitForCondition false
        presentation.activity == AgentThreadActivity.PROCESSING && presentation.activityReport.chromeActivity == null && presentation.updatedAt == 200L
      }

      assertThat(closedRefreshInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun providerUpdateSummaryOnlyActivityHintsUpdateLoadedThreadBeforeRefreshGateOpens() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val gateActive = AtomicBoolean(false)
    val presentationModel = AgentSessionThreadPresentationModel()
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { gateActive.get() },
      presentationModel = presentationModel,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX, activity = AgentThreadActivity.READY)
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(
        threadsChangedEvent(
          scopedPaths = setOf(PROJECT_PATH),
          threadIds = setOf("codex-1"),
          activityUpdatesByThreadId = mapOf("codex-1" to activityUpdate(
            activity = AgentThreadActivity.READY,
            chromeActivity = AgentThreadActivity.UNREAD,
            updatedAt = 300L,
          )),
        )
      )

      waitForCondition {
        val thread = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
                       ?.threads
                       ?.firstOrNull { it.id == "codex-1" }
                     ?: return@waitForCondition false
        thread.activity == AgentThreadActivity.READY && thread.summaryActivity == AgentThreadActivity.UNREAD && thread.updatedAt == 300L
      }
    }
  }

  @Test
  fun providerRefreshActivityClearsOptimisticActivityHint() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val closedRefreshInvocations = AtomicInteger(0)
    val presentationModel = AgentSessionThreadPresentationModel()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          closedRefreshInvocations.incrementAndGet()
          listOf(
            thread(
              id = "codex-1",
              updatedAt = 500L,
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.UNREAD,
            )
          )
        }
      },
      prefetchRefreshThreadSeedsProvider = { paths, _ ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          mapOf(
            PROJECT_PATH to AgentSessionRefreshHints(
              activityUpdatesByThreadId = mapOf("codex-1" to activityUpdate(AgentThreadActivity.UNREAD))
            )
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      presentationModel = presentationModel,
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX, activity = AgentThreadActivity.READY)
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(
        threadsChangedEvent(
          scopedPaths = setOf(PROJECT_PATH),
          threadIds = setOf("codex-1"),
          activityUpdatesByThreadId = mapOf("codex-1" to activityUpdate(AgentThreadActivity.PROCESSING)),
        )
      )

      waitForCondition {
        val thread = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
                       ?.threads
                       ?.firstOrNull { it.id == "codex-1" }
                     ?: return@waitForCondition false
        closedRefreshInvocations.get() == 1 &&
        thread.updatedAt == 500L &&
        thread.activity == AgentThreadActivity.UNREAD
      }

      val expectedKey = presentationKey(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1")
      waitForCondition {
        presentationModel.snapshot()[expectedKey]?.activity == AgentThreadActivity.UNREAD
      }
    }
  }

  @Test
  fun providerRefreshActivityHintWithoutSummaryPreservesNonContributingSummaryActivity() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(
              id = "codex-sub-agent",
              updatedAt = 500L,
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.READY,
              summaryActivity = null,
            )
          )
        }
      },
      prefetchRefreshThreadSeedsProvider = { paths, _ ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          mapOf(
            PROJECT_PATH to AgentSessionRefreshHints(
              activityUpdatesByThreadId = mapOf("codex-sub-agent" to activityUpdate(
                activity = AgentThreadActivity.UNREAD,
                chromeActivity = null,
              ))
            )
          )
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(
                id = "codex-sub-agent",
                updatedAt = 100L,
                provider = AgentSessionProvider.CODEX,
                activity = AgentThreadActivity.READY,
                summaryActivity = null,
              )
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(
        threadsChangedEvent(
          scopedPaths = setOf(PROJECT_PATH),
          threadIds = setOf("codex-sub-agent"),
        )
      )

      waitForCondition {
        val thread = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
                       ?.threads
                       ?.firstOrNull { it.id == "codex-sub-agent" }
                     ?: return@waitForCondition false
        thread.updatedAt == 500L &&
        thread.activity == AgentThreadActivity.UNREAD &&
        thread.summaryActivity == null
      }
    }
  }

  @Test
  fun providerUpdateWithoutActivityHintsCanClearOptimisticActivity() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(
              id = "codex-1",
              updatedAt = 500L,
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.UNREAD,
            )
          )
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX, activity = AgentThreadActivity.PROCESSING)
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(
        threadsChangedEvent(
          scopedPaths = setOf(PROJECT_PATH),
          threadIds = setOf("codex-1"),
        )
      )

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.id == "codex-1" }
          ?.activity == AgentThreadActivity.UNREAD
      }
    }
  }

  @Test
  fun providerUpdatePassesListedCodexThreadIdsToRefreshHintsWhenBaselineIsEmpty() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val capturedRefreshThreadSeedsByPath = mutableListOf<Map<String, Set<AgentSessionRefreshThreadSeed>>>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(
              id = "codex-listed",
              updatedAt = 610L,
              title = "Listed Codex thread",
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.PROCESSING,
            )
          )
        }
      },
      prefetchRefreshThreadSeedsProvider = { paths, refreshThreadSeedsByPath ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          capturedRefreshThreadSeedsByPath += refreshThreadSeedsByPath.mapValues { (_, refreshThreadSeeds) -> refreshThreadSeeds.toSet() }
          mapOf(
            PROJECT_PATH to AgentSessionRefreshHints(
              activityUpdatesByThreadId = mapOf(
                "codex-listed" to activityUpdate(AgentThreadActivity.UNREAD),
              )
            )
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = emptyList(),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        stateStore.snapshot().projects
          .firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.id == "codex-listed" }
          ?.activity == AgentThreadActivity.UNREAD
      }

      assertThat(capturedRefreshThreadSeedsByPath).isNotEmpty()
      assertThat(capturedRefreshThreadSeedsByPath.last()[PROJECT_PATH]).containsExactly(
        AgentSessionRefreshThreadSeed(threadId = "codex-listed", updatedAt = 610L, forceRefresh = false)
      )
    }
  }

  @Test
  fun providerUpdateDoesNotPassPendingNewThreadIdsToRefreshHints() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val capturedRefreshThreadSeedsByPath = mutableListOf<Map<String, Set<AgentSessionRefreshThreadSeed>>>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(
              id = "codex-listed",
              updatedAt = 610L,
              title = "Listed Codex thread",
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.PROCESSING,
            )
          )
        }
      },
      prefetchRefreshThreadSeedsProvider = { paths, refreshThreadSeedsByPath ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          capturedRefreshThreadSeedsByPath += refreshThreadSeedsByPath.mapValues { (_, refreshThreadSeeds) -> refreshThreadSeeds.toSet() }
          emptyMap()
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "new-pending", updatedAt = 600L, provider = AgentSessionProvider.CODEX),
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        capturedRefreshThreadSeedsByPath.isNotEmpty()
      }

      assertThat(capturedRefreshThreadSeedsByPath.last()[PROJECT_PATH]).containsExactly(
        AgentSessionRefreshThreadSeed(threadId = "codex-listed", updatedAt = 610L, forceRefresh = false)
      )
    }
  }

  @Test
  fun providerUpdateBuildsLightweightPendingTabRebindTargets() {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(thread(id = "codex-env", updatedAt = 320L, title = "Env thread", provider = AgentSessionProvider.CODEX))
        }
      },
    )

    runBlocking(Dispatchers.Default) {
      withLoadingCoordinator(
        sessionSourcesProvider = { listOf(source) },
        isRefreshGateActive = { true },
        openChatPathsProvider = { setOf(PROJECT_PATH) },
        openPendingCodexTabsProvider = {
          mapOf(
            PROJECT_PATH to listOf(
              pendingCodexTab(
                pendingThreadIdentity = "codex:new-env",
                pendingCreatedAtMs = 200L,
              )
            )
          )
        },
        openChatPendingTabsBinder = { requestsByPath ->
          requestsByPath.forEach { (path, requests) ->
            requests.forEach { request ->
              rebindInvocations.add(
                PendingCodexRebindInvocation(
                  path = path,
                  pendingTabKey = request.pendingTabKey,
                  pendingThreadIdentity = request.pendingThreadIdentity,
                  target = request.target,
                )
              )
            }
          }
          successfulPendingCodexRebindReport(requestsByPath)
        },
      ) { coordinator, stateStore ->
        stateStore.replaceProjects(
          projects = listOf(
            AgentProjectSessions(
              path = PROJECT_PATH,
              name = "Project A",
              isOpen = true,
              providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
              threads = listOf(thread(id = "codex-base", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
            )
          ),
          visibleThreadCounts = emptyMap(),
        )

        coordinator.observeSessionSourceUpdates()
        updates.tryEmit(threadsChangedEvent())

        waitForCondition {
          rebindInvocations.isNotEmpty()
        }

        val invocation = rebindInvocations.single()
        assertThat(invocation.target.projectPath).isEqualTo(PROJECT_PATH)
        assertThat(invocation.target.provider).isEqualTo(AgentSessionProvider.CODEX)
        assertThat(invocation.target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-env"))
      }
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
        projects.firstOrNull { it.path == PROJECT_PATH }?.providerLoadStates?.get(AgentSessionProvider.CODEX) ==
        AgentSessionProviderLoadState.LOADED &&
        projects.firstOrNull { it.path == projectB }?.providerLoadStates?.get(AgentSessionProvider.CODEX) ==
        AgentSessionProviderLoadState.LOADED
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
  fun refreshUsesProviderTitleForLoadedOpenProjectThreads() = runBlocking(Dispatchers.Default) {
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(
            thread(id = "codex-1", updatedAt = 100L, title = "Automatic title", provider = AgentSessionProvider.CODEX)
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
      isRefreshGateActive = { true },
    ) { coordinator, stateStore ->
      coordinator.refresh()

      waitForCondition {
        val thread = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.singleOrNull()
        thread?.id == "codex-1" && thread.title == "Automatic title"
      }
    }
  }

  @Test
  fun refreshBootstrapUsesWarmSnapshotThreadTitles() = runBlocking(Dispatchers.Default) {
    val warmState = InMemorySessionWarmState()
    warmState.setPathSnapshot(
      PROJECT_PATH,
      AgentSessionWarmPathSnapshot(
        threads = listOf(
          thread(id = "codex-warm", updatedAt = 100L, title = "Automatic warm title", provider = AgentSessionProvider.CODEX)
        ),
        providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
        updatedAt = 100L,
      ),
    )
    val providerResult = CompletableDeferred<List<AgentSessionThread>>()
    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { _, _ -> providerResult.await() },
    )

    try {
      withLoadingCoordinator(
        sessionSourcesProvider = { listOf(source) },
        projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        isRefreshGateActive = { true },
        warmState = warmState,
      ) { coordinator, stateStore ->
        coordinator.refresh()

        waitForCondition {
          val project = stateStore.snapshot().projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
          project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADING &&
          project.threads.singleOrNull()?.title == "Automatic warm title"
        }
      }
    }
    finally {
      providerResult.complete(emptyList())
    }
  }

  @Test
  fun threadScopedProviderUpdateMergesOnlyRequestedThread() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val refreshRequests = mutableListOf<Pair<List<String>, Set<String>>>()
    val closedRefreshInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = {
        closedRefreshInvocations.incrementAndGet()
        emptyList()
      },
      refreshThreadsProvider = { request ->
        refreshRequests += request.paths to request.threadIds
        AgentSessionSourceRefreshResult(
          partialThreadsByPath = mapOf(
            PROJECT_PATH to listOf(
              thread(id = "codex-1", updatedAt = 900L, title = "Updated", provider = AgentSessionProvider.CODEX)
            )
          )
        )
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, title = "Old", provider = AgentSessionProvider.CODEX),
              thread(id = "codex-2", updatedAt = 200L, title = "Stable", provider = AgentSessionProvider.CODEX),
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent(threadIds = setOf("codex-1")))

      waitForCondition {
        stateStore.snapshot().projects.first().threads.first().id == "codex-1" &&
        stateStore.snapshot().projects.first().threads.first().updatedAt == 900L
      }

      val project = stateStore.snapshot().projects.first()
      assertThat(project.threads.map { it.id }).containsExactly("codex-1", "codex-2")
      assertThat(project.threads.first { it.id == "codex-1" }.title).isEqualTo("Updated")
      assertThat(project.threads.first { it.id == "codex-2" }.title).isEqualTo("Stable")
      assertThat(refreshRequests).containsExactly(listOf(PROJECT_PATH) to setOf("codex-1"))
      assertThat(closedRefreshInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun threadScopedProviderUpdatePreservesRelativeOrderForStillWorkingThreads() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      refreshThreadsProvider = {
        AgentSessionSourceRefreshResult(
          partialThreadsByPath = mapOf(
            PROJECT_PATH to listOf(
              thread(
                id = "codex-2",
                updatedAt = 900L,
                provider = AgentSessionProvider.CODEX,
                activity = AgentThreadActivity.PROCESSING,
              )
            )
          )
        )
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX, activity = AgentThreadActivity.PROCESSING),
              thread(id = "codex-2", updatedAt = 200L, provider = AgentSessionProvider.CODEX, activity = AgentThreadActivity.PROCESSING),
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent(threadIds = setOf("codex-2")))

      waitForCondition {
        stateStore.snapshot().projects.first().threads.first { it.id == "codex-2" }.updatedAt == 900L
      }

      assertThat(stateStore.snapshot().projects.first().threads.map { it.id }).containsExactly("codex-1", "codex-2")
    }
  }

  @Test
  fun threadScopedProviderUpdatePreservesRelativeOrderWhenThreadLeavesWorkingState() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      refreshThreadsProvider = {
        AgentSessionSourceRefreshResult(
          partialThreadsByPath = mapOf(
            PROJECT_PATH to listOf(
              thread(
                id = "codex-2",
                updatedAt = 900L,
                provider = AgentSessionProvider.CODEX,
                activity = AgentThreadActivity.READY,
              )
            )
          )
        )
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX, activity = AgentThreadActivity.PROCESSING),
              thread(id = "codex-2", updatedAt = 200L, provider = AgentSessionProvider.CODEX, activity = AgentThreadActivity.PROCESSING),
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent(threadIds = setOf("codex-2")))

      waitForCondition {
        val threads = stateStore.snapshot().projects.first().threads
        threads.first { it.id == "codex-2" }.updatedAt == 900L
      }

      assertThat(stateStore.snapshot().projects.first().threads.map { it.id }).containsExactly("codex-1", "codex-2")
    }
  }

  @Test
  fun threadScopedProviderUpdateMergesReturnedSubAgentsWithExistingParentSubAgents() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val refreshRequests = mutableListOf<Pair<List<String>, Set<String>>>()

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      refreshThreadsProvider = { request ->
        refreshRequests += request.paths to request.threadIds
        AgentSessionSourceRefreshResult(
          partialThreadsByPath = mapOf(
            PROJECT_PATH to listOf(
              thread(
                id = "codex-parent",
                updatedAt = 900L,
                title = "Parent updated",
                provider = AgentSessionProvider.CODEX,
                subAgents = listOf(
                  AgentSubAgent(id = "codex-sub-2", name = "Sub-agent 2 updated"),
                  AgentSubAgent(id = "codex-sub-3", name = "Sub-agent 3"),
                ),
              )
            )
          )
        )
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
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(
              thread(
                id = "codex-parent",
                updatedAt = 100L,
                title = "Parent old",
                provider = AgentSessionProvider.CODEX,
                subAgents = listOf(
                  AgentSubAgent(id = "codex-sub-1", name = "Sub-agent 1"),
                  AgentSubAgent(id = "codex-sub-2", name = "Sub-agent 2"),
                ),
              ),
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent(threadIds = setOf("codex-parent")))

      waitForCondition {
        stateStore.snapshot().projects.first().threads.single().updatedAt == 900L
      }

      val thread = stateStore.snapshot().projects.first().threads.single()
      assertThat(thread.title).isEqualTo("Parent updated")
      assertThat(thread.subAgents).containsExactly(
        AgentSubAgent(id = "codex-sub-1", name = "Sub-agent 1"),
        AgentSubAgent(id = "codex-sub-2", name = "Sub-agent 2 updated"),
        AgentSubAgent(id = "codex-sub-3", name = "Sub-agent 3"),
      )
      assertThat(refreshRequests).containsExactly(listOf(PROJECT_PATH) to setOf("codex-parent"))
    }
  }

  @Test
  fun unresolvedThreadScopedProviderUpdateDoesNotFallBackToFullRefresh() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val refreshInvocations = AtomicInteger(0)
    val closedRefreshInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = true,
      updateEvents = updates,
      listFromClosedProject = {
        closedRefreshInvocations.incrementAndGet()
        emptyList()
      },
      refreshThreadsProvider = {
        refreshInvocations.incrementAndGet()
        AgentSessionSourceRefreshResult()
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-known", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(threadsChangedEvent(threadIds = setOf("codex-missing")))
      delay(700.milliseconds)

      assertThat(refreshInvocations.get()).isEqualTo(0)
      assertThat(closedRefreshInvocations.get()).isEqualTo(0)
      assertThat(stateStore.snapshot().projects.first().threads.map { it.id }).containsExactly("codex-known")
    }
  }

  @Test
  fun pendingCodexScopedRefreshWithoutKnownBaselineDoesNotTriggerRebindWithoutSafeMetadata() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val binderInvocations = AtomicInteger(0)
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)

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
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
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
      openChatPendingTabsBinder = { requestsByPath ->
        binderInvocations.incrementAndGet()
        failingPendingCodexRebindReport(
          requestsByPath = requestsByPath,
        )
      },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() > 0
      }
      delay(150.milliseconds)

      assertThat(binderInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun scopedRefreshObserverRestartsAfterFailure() = runBlocking(Dispatchers.Default) {
    val recoveredSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val scopedSignalsProviderCalls = AtomicInteger(0)
    val closedRefreshInvocations = AtomicInteger(0)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
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
      providerDescriptors = testRefreshCoordinatorProviderDescriptors().filter { descriptor ->
        descriptor.provider == AgentSessionProvider.CODEX
      },
      codexScopedRefreshSignalsProvider = {
        val call = scopedSignalsProviderCalls.incrementAndGet()
        if (call == 1) {
          flow {
            throw IllegalStateException("scoped refresh failed")
          }
        }
        else {
          recoveredSignals
        }
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-1", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()

      waitForCondition(timeoutMs = 4_000) {
        scopedSignalsProviderCalls.get() >= 2
      }

      recoveredSignals.tryEmit(setOf(PROJECT_PATH))

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
  fun pendingCodexScopedRefreshWithoutKnownBaselineRebindsRecentCreateFlowTabs() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val pendingCreatedAtMs = System.currentTimeMillis() - 1_000L

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
            thread(
              id = "codex-polled",
              updatedAt = pendingCreatedAtMs + 200L,
              title = "Polled thread",
              provider = AgentSessionProvider.CODEX,
            )
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-recent",
              pendingCreatedAtMs = pendingCreatedAtMs,
              pendingLaunchMode = "standard",
            )
          )
        )
      },
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() > 0 && rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-recent")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-recent")
      assertThat(invocation.target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-polled"))
      assertThat(invocation.target.threadId).isEqualTo("codex-polled")
    }
  }

  @Test
  fun pendingCodexScopedRefreshRebindsWhenConcreteThreadAppearsOnLaterRefresh() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val pendingFirstInputAtMs = System.currentTimeMillis() - 1_000L

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          when (closedRefreshInvocations.incrementAndGet()) {
            1 -> emptyList()
            else -> listOf(
              thread(
                id = "codex-resolved-later",
                updatedAt = pendingFirstInputAtMs + 200L,
                title = "Resolved later",
                provider = AgentSessionProvider.CODEX,
              )
            )
          }
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-retry",
              pendingCreatedAtMs = pendingFirstInputAtMs - 100L,
              pendingFirstInputAtMs = pendingFirstInputAtMs,
              pendingLaunchMode = "standard",
            )
          )
        )
      },
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() >= 1
      }
      delay(150.milliseconds)

      assertThat(rebindInvocations).isEmpty()

      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() >= 2 && rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-retry")
      assertThat(invocation.target.threadIdentity)
        .isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-resolved-later"))
      assertThat(invocation.target.threadTitle).isEqualTo("Resolved later")
    }
  }

  @Test
  fun pendingCodexScopedRefreshWithoutKnownBaselineDoesNotRebindStaleCreateFlowTabs() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val binderInvocations = AtomicInteger(0)
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val stalePendingCreatedAtMs = System.currentTimeMillis() - 10 * 60 * 1000L

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
            thread(
              id = "codex-polled",
              updatedAt = stalePendingCreatedAtMs + 200L,
              title = "Polled thread",
              provider = AgentSessionProvider.CODEX,
            )
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-stale",
              pendingCreatedAtMs = stalePendingCreatedAtMs,
              pendingLaunchMode = "standard",
            )
          )
        )
      },
      openChatPendingTabsBinder = { requestsByPath ->
        binderInvocations.incrementAndGet()
        failingPendingCodexRebindReport(
          requestsByPath = requestsByPath,
        )
      },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() > 0
      }
      delay(150.milliseconds)

      assertThat(binderInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun pendingCodexScopedRefreshRebindsWhenResolvedThreadIsAlreadyOpen() = runBlocking(Dispatchers.Default) {
    assertPendingScopedRefreshRebindsWhenResolvedThreadIsAlreadyOpen(AgentSessionProvider.CODEX)
  }

  @Test
  fun pendingClaudeScopedRefreshRebindsWhenResolvedThreadIsAlreadyOpen() = runBlocking(Dispatchers.Default) {
    assertPendingScopedRefreshRebindsWhenResolvedThreadIsAlreadyOpen(AgentSessionProvider.CLAUDE)
  }

  @Test
  fun pendingCodexScopedRefreshRebindsOnlyNewThreadIdsWhenBaselineKnown() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)

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
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
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
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-existing", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() > 0 && rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-3")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-3")
      val target = invocation.target
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-new"))
      assertThat(target.threadId).isEqualTo("codex-new")
      assertThat(target.threadTitle).isEqualTo("New thread")
    }
  }

  @Test
  fun pendingCodexScopedRefreshDoesNotRebindToPreviouslyKnownThreadIds() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val binderInvocations = AtomicInteger(0)
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)

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
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
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
      openChatPendingTabsBinder = { requestsByPath ->
        binderInvocations.incrementAndGet()
        failingPendingCodexRebindReport(
          requestsByPath = requestsByPath,
        )
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-existing", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() > 0
      }
      delay(150.milliseconds)

      assertThat(binderInvocations.get()).isEqualTo(0)
    }
  }

  @Test
  fun pendingCodexScopedRefreshScopesToSignaledPathsOnly() = runBlocking(Dispatchers.Default) {
    val pendingPath = "/work/project-pending"
    val refreshedPaths = mutableListOf<String>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        synchronized(refreshedPaths) {
          refreshedPaths.add(path)
        }
        when (path) {
          pendingPath -> listOf(thread(id = "codex-polled",
                                       updatedAt = 700L,
                                       title = "Polled thread",
                                       provider = AgentSessionProvider.CODEX))
          PROJECT_PATH -> listOf(thread(id = "codex-loaded",
                                        updatedAt = 999L,
                                        title = "Loaded thread",
                                        provider = AgentSessionProvider.CODEX))
          else -> emptyList()
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH, pendingPath) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Loaded",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-loaded", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = pendingPath,
            name = "Pending",
            isOpen = true,
            threads = emptyList(),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(pendingPath))

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
  fun codexScopedRefreshSignalsTriggerRefreshWithoutPendingTabs() = runBlocking(Dispatchers.Default) {
    val outputPath = "/work/project-output"
    val refreshedPaths = mutableListOf<String>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        synchronized(refreshedPaths) {
          refreshedPaths.add(path)
        }
        when (path) {
          outputPath -> listOf(thread(id = "codex-output",
                                      updatedAt = 700L,
                                      title = "Output thread",
                                      provider = AgentSessionProvider.CODEX))
          PROJECT_PATH -> listOf(thread(id = "codex-loaded",
                                        updatedAt = 999L,
                                        title = "Loaded thread",
                                        provider = AgentSessionProvider.CODEX))
          else -> emptyList()
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH, outputPath) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Loaded",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-loaded", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = outputPath,
            name = "Output",
            isOpen = true,
            threads = emptyList(),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(outputPath))

      waitForCondition {
        synchronized(refreshedPaths) {
          refreshedPaths.contains(outputPath)
        }
      }

      val refreshedPathsSnapshot = synchronized(refreshedPaths) {
        refreshedPaths.toList()
      }
      assertThat(refreshedPathsSnapshot).containsOnly(outputPath)

      val loadedProject = stateStore.snapshot().projects.first { it.path == PROJECT_PATH }
      assertThat(
        loadedProject.threads.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt
      ).isEqualTo(100L)
    }
  }

  @Test
  fun pendingCodexScopedRefreshDoesNotPersistPendingThreadsForUnloadedPath() = runBlocking(Dispatchers.Default) {
    val pendingPath = "/work/project-pending"
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)

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
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
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
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = pendingPath,
            name = "Pending",
            isOpen = true,
            threads = emptyList(),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(pendingPath))

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == pendingPath }
          ?.providerLoadStates
          ?.get(AgentSessionProvider.CODEX) == AgentSessionProviderLoadState.LOADED
      }

      val pendingProject = stateStore.snapshot().projects.first { it.path == pendingPath }
      assertThat(pendingProject.providerLoadStates[AgentSessionProvider.CODEX]).isEqualTo(AgentSessionProviderLoadState.LOADED)
      assertThat(pendingProject.threads).noneMatch { thread ->
        thread.provider == AgentSessionProvider.CODEX && thread.id == "new-pending"
      }
    }
  }

  @Test
  fun pendingClaudeScopedRefreshDoesNotPersistPendingThreadsForUnloadedPath() = runBlocking(Dispatchers.Default) {
    val pendingPath = "/work/project-pending"
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      supportsUpdates = false,
      listFromClosedProject = { _ ->
        emptyList()
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openPendingClaudeTabsProvider = {
        mapOf(
          pendingPath to listOf(
            pendingTab(
              provider = AgentSessionProvider.CLAUDE,
              pendingThreadIdentity = buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "new-pending"),
              projectPath = pendingPath,
              pendingCreatedAtMs = 700L,
            )
          )
        )
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = pendingPath,
            name = "Pending",
            isOpen = true,
            threads = emptyList(),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(pendingPath))

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == pendingPath }
          ?.providerLoadStates
          ?.get(AgentSessionProvider.CLAUDE) == AgentSessionProviderLoadState.LOADED
      }

      val pendingProject = stateStore.snapshot().projects.first { it.path == pendingPath }
      assertThat(pendingProject.providerLoadStates[AgentSessionProvider.CLAUDE]).isEqualTo(AgentSessionProviderLoadState.LOADED)
      assertThat(pendingProject.threads).noneMatch { thread ->
        thread.provider == AgentSessionProvider.CLAUDE && thread.id == "new-pending"
      }
    }
  }

  @Test
  fun pendingCodexScopedRefreshDoesNotProjectStalePendingRowsAfterSuccessfulRebind() = runBlocking(Dispatchers.Default) {
    val pendingPath = "/work/project-pending"
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()
    val pendingCreatedAtMs = System.currentTimeMillis() - 1_000L

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != pendingPath) {
          emptyList()
        }
        else {
          listOf(
            thread(
              id = "codex-resolved",
              updatedAt = pendingCreatedAtMs + 200L,
              title = "Resolved thread",
              provider = AgentSessionProvider.CODEX,
            )
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(pendingPath) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openPendingCodexTabsProvider = {
        mapOf(
          pendingPath to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-pending",
              projectPath = pendingPath,
              pendingCreatedAtMs = pendingCreatedAtMs,
              pendingLaunchMode = "standard",
            )
          )
        )
      },
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = pendingPath,
            name = "Pending",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-existing", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(pendingPath))

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == pendingPath }
          ?.threads
          ?.map { it.id } == listOf("codex-resolved")
      }

      assertThat(rebindInvocations).hasSize(1)
      val project = stateStore.snapshot().projects.first { it.path == pendingPath }
      assertThat(project.threads.map { it.id }).containsExactly("codex-resolved")
    }
  }

  @Test
  fun scopedRefreshDoesNotBindPendingTabsForProviderWithoutPendingEditorRebindSupport() = runBlocking(Dispatchers.Default) {
    val pendingPath = "/work/project-pending"
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val rebindRequests = mutableListOf<AgentChatPendingTabRebindRequest>()
    val pendingCreatedAtMs = System.currentTimeMillis() - 1_000L

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != pendingPath) {
          emptyList()
        }
        else {
          listOf(
            thread(
              id = "codex-resolved",
              updatedAt = pendingCreatedAtMs + 200L,
              title = "Resolved thread",
              provider = AgentSessionProvider.CODEX,
            )
          )
        }
      },
    )
    val providerDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = emptySet(),
      cliAvailable = true,
      supportsPendingEditorTabRebind = false,
      supportsNewThreadRebind = true,
      emitsScopedRefreshSignals = true,
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      providerDescriptors = listOf(providerDescriptor),
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(pendingPath) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openPendingCodexTabsProvider = {
        mapOf(
          pendingPath to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-pending",
              projectPath = pendingPath,
              pendingCreatedAtMs = pendingCreatedAtMs,
              pendingLaunchMode = "standard",
            )
          )
        )
      },
      openChatPendingTabsBinder = { requestsByPath ->
        rebindRequests += requestsByPath.values.flatten()
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = pendingPath,
            name = "Pending",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CODEX),
            threads = listOf(thread(id = "codex-existing", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(pendingPath))

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == pendingPath }
          ?.threads
          ?.map { it.id } == listOf("codex-resolved")
      }

      assertThat(rebindRequests).isEmpty()
    }
  }

  @Test
  fun pendingClaudeScopedRefreshDoesNotProjectStalePendingRowsAfterSuccessfulRebind() = runBlocking(Dispatchers.Default) {
    val pendingPath = "/work/project-pending"
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()
    val pendingCreatedAtMs = System.currentTimeMillis() - 1_000L

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != pendingPath) {
          emptyList()
        }
        else {
          listOf(
            thread(
              id = "claude-resolved",
              updatedAt = pendingCreatedAtMs + 200L,
              title = "Resolved Claude thread",
              provider = AgentSessionProvider.CLAUDE,
            )
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(pendingPath) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openPendingClaudeTabsProvider = {
        mapOf(
          pendingPath to listOf(
            pendingTab(
              provider = AgentSessionProvider.CLAUDE,
              pendingThreadIdentity = buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "new-pending"),
              projectPath = pendingPath,
              pendingCreatedAtMs = pendingCreatedAtMs,
              pendingLaunchMode = "standard",
            )
          )
        )
      },
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = pendingPath,
            name = "Pending",
            isOpen = true,
            providerLoadStates = loadedProviderStates(AgentSessionProvider.CLAUDE),
            threads = listOf(thread(id = "claude-existing", updatedAt = 100L, provider = AgentSessionProvider.CLAUDE)),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(pendingPath))

      waitForCondition {
        stateStore.snapshot().projects.firstOrNull { it.path == pendingPath }
          ?.threads
          ?.map { it.id } == listOf("claude-resolved")
      }

      assertThat(rebindInvocations).hasSize(1)
      val invocation = rebindInvocations.single()
      assertThat(invocation.pendingThreadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "new-pending"))
      assertThat(invocation.target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "claude-resolved"))
      val project = stateStore.snapshot().projects.first { it.path == pendingPath }
      assertThat(project.threads.map { it.id }).containsExactly("claude-resolved")
    }
  }

  @Test
  fun codexScopedRefreshBindsPendingTabEvenWhenConcreteNewThreadAnchorCouldMatch() = runBlocking(Dispatchers.Default) {
    val pendingRebindInvocations = mutableListOf<PendingCodexRebindInvocation>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val rebindRequestedAtMs = System.currentTimeMillis() - 1_000L
    val oldIdentity = buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-old")
    val targetIdentity = buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-hinted-new")

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          listOf(thread(id = "codex-old", updatedAt = 100L, title = "Old thread", provider = AgentSessionProvider.CODEX))
        }
      },
      prefetchRefreshHintsProvider = { paths, _ ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          mapOf(
            PROJECT_PATH to AgentSessionRefreshHints(
              rebindCandidates = listOf(
                AgentSessionRebindCandidate(
                  threadId = "codex-hinted-new",
                  title = "Hinted thread",
                  updatedAt = rebindRequestedAtMs + 200L,
                  activity = AgentThreadActivity.UNREAD,
                )
              )
            )
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            pendingCodexTab(
              pendingThreadIdentity = "codex:new-1",
              pendingCreatedAtMs = rebindRequestedAtMs,
              pendingLaunchMode = "default",
            )
          )
        )
      },
      openConcreteCodexTabsAwaitingNewThreadRebindProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            concreteCodexTab(
              currentThreadIdentity = oldIdentity,
              tabKey = "concrete-codex-old",
              newThreadRebindRequestedAtMs = rebindRequestedAtMs,
            )
          )
        )
      },
      openConcreteChatThreadIdentitiesByPathProvider = {
        mapOf(PROJECT_PATH to setOf(oldIdentity))
      },
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            pendingRebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        pendingRebindInvocations.isNotEmpty()
      }
      delay(150.milliseconds)

      val invocation = pendingRebindInvocations.single()
      assertThat(invocation.target.threadIdentity).isEqualTo(targetIdentity)
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-1")
    }
  }

  @Test
  fun concreteCodexScopedRefreshClearsStaleAnchorsWithoutRebinding() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val clearedTabs = mutableListOf<AgentChatConcreteTabSnapshot>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val staleRequestedAtMs = System.currentTimeMillis() - 60_000L
    val oldIdentity = buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-old")

    val source = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          closedRefreshInvocations.incrementAndGet()
          listOf(thread(id = "codex-old", updatedAt = 100L, title = "Old thread", provider = AgentSessionProvider.CODEX))
        }
      },
      prefetchRefreshHintsProvider = { paths, _ ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          mapOf(
            PROJECT_PATH to AgentSessionRefreshHints(
              rebindCandidates = listOf(
                AgentSessionRebindCandidate(
                  threadId = "codex-hinted-new",
                  title = "Hinted thread",
                  updatedAt = staleRequestedAtMs + 200L,
                  activity = AgentThreadActivity.UNREAD,
                )
              )
            )
          )
        }
      },
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openConcreteCodexTabsAwaitingNewThreadRebindProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            concreteCodexTab(
              currentThreadIdentity = oldIdentity,
              tabKey = "stale-concrete-codex-old",
              newThreadRebindRequestedAtMs = staleRequestedAtMs,
            )
          )
        )
      },
      openConcreteChatThreadIdentitiesByPathProvider = {
        mapOf(PROJECT_PATH to setOf(oldIdentity))
      },
      clearOpenConcreteCodexTabAnchors = { tabsByPath ->
        tabsByPath.values.forEach(clearedTabs::addAll)
        clearedTabs.size
      },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() > 0 && clearedTabs.isNotEmpty()
      }
      delay(150.milliseconds)

      assertThat(clearedTabs).containsExactly(
        concreteCodexTab(
          currentThreadIdentity = oldIdentity,
          tabKey = "stale-concrete-codex-old",
          newThreadRebindRequestedAtMs = staleRequestedAtMs,
        )
      )
    }
  }

  private suspend fun assertPendingScopedRefreshRebindsWhenResolvedThreadIsAlreadyOpen(provider: AgentSessionProvider) {
    val closedRefreshInvocations = AtomicInteger(0)
    val rebindInvocations = mutableListOf<PendingCodexRebindInvocation>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val pendingCreatedAtMs = System.currentTimeMillis() - 1_000L
    val pendingThreadId = "new-already-open-${provider.value}"
    val resolvedThreadId = "${provider.value}-resolved-already-open"
    val resolvedThreadIdentity = buildAgentSessionIdentity(provider, resolvedThreadId)

    val source = ScriptedSessionSource(
      provider = provider,
      supportsUpdates = false,
      listFromClosedProject = { path ->
        if (path != PROJECT_PATH) {
          emptyList()
        }
        else {
          closedRefreshInvocations.incrementAndGet()
          listOf(
            thread(
              id = resolvedThreadId,
              updatedAt = pendingCreatedAtMs + 200L,
              title = "Resolved ${provider.value}",
              provider = provider,
            )
          )
        }
      },
    )
    val pendingTab = pendingTab(
      provider = provider,
      pendingThreadIdentity = buildAgentSessionIdentity(provider, pendingThreadId),
      pendingCreatedAtMs = pendingCreatedAtMs,
      pendingLaunchMode = "standard",
    )

    withLoadingCoordinator(
      sessionSourcesProvider = { listOf(source) },
      isRefreshGateActive = { true },
      openChatPathsProvider = { setOf(PROJECT_PATH) },
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
      openPendingCodexTabsProvider = {
        if (provider == AgentSessionProvider.CODEX) mapOf(PROJECT_PATH to listOf(pendingTab)) else emptyMap()
      },
      openPendingClaudeTabsProvider = {
        if (provider == AgentSessionProvider.CLAUDE) mapOf(PROJECT_PATH to listOf(pendingTab)) else emptyMap()
      },
      openConcreteChatThreadIdentitiesByPathProvider = {
        mapOf(PROJECT_PATH to setOf(resolvedThreadIdentity))
      },
      openChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              PendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { coordinator, stateStore ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() > 0 && rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingThreadIdentity).isEqualTo(buildAgentSessionIdentity(provider, pendingThreadId))
      assertThat(invocation.target.threadIdentity).isEqualTo(resolvedThreadIdentity)
      assertThat(invocation.target.threadId).isEqualTo(resolvedThreadId)
      assertThat(invocation.target.provider).isEqualTo(provider)
      assertThat(
        stateStore.snapshot().projects.firstOrNull { project -> project.path == PROJECT_PATH }
          ?.threads
          .orEmpty()
          .map { thread -> thread.id }
      ).doesNotContain(pendingThreadId)
    }
  }

}

private data class PendingCodexRebindInvocation(
  @JvmField val path: String,
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val target: AgentChatTabRebindTarget,
)

private fun pendingCodexTab(
  pendingThreadIdentity: String,
  projectPath: String = PROJECT_PATH,
  pendingTabKey: String = "pending-$pendingThreadIdentity",
  pendingCreatedAtMs: Long? = null,
  pendingFirstInputAtMs: Long? = null,
  pendingLaunchMode: String? = null,
): AgentChatPendingTabSnapshot {
  return pendingTab(
    provider = AgentSessionProvider.CODEX,
    pendingThreadIdentity = pendingThreadIdentity,
    projectPath = projectPath,
    pendingTabKey = pendingTabKey,
    pendingCreatedAtMs = pendingCreatedAtMs,
    pendingFirstInputAtMs = pendingFirstInputAtMs,
    pendingLaunchMode = pendingLaunchMode,
  )
}

private fun pendingTab(
  provider: AgentSessionProvider,
  pendingThreadIdentity: String,
  projectPath: String = PROJECT_PATH,
  pendingTabKey: String = "pending-$pendingThreadIdentity",
  pendingCreatedAtMs: Long? = null,
  pendingFirstInputAtMs: Long? = null,
  pendingLaunchMode: String? = null,
): AgentChatPendingTabSnapshot {
  require(provider in setOf(AgentSessionProvider.CLAUDE, AgentSessionProvider.CODEX))
  return AgentChatPendingTabSnapshot(
    projectPath = projectPath,
    pendingTabKey = pendingTabKey,
    pendingThreadIdentity = pendingThreadIdentity,
    pendingCreatedAtMs = pendingCreatedAtMs,
    pendingFirstInputAtMs = pendingFirstInputAtMs,
    pendingLaunchMode = pendingLaunchMode,
  )
}

private fun concreteCodexTab(
  currentThreadIdentity: String,
  projectPath: String = PROJECT_PATH,
  tabKey: String = "tab-$currentThreadIdentity",
  newThreadRebindRequestedAtMs: Long,
): AgentChatConcreteTabSnapshot {
  return AgentChatConcreteTabSnapshot(
    projectPath = projectPath,
    tabKey = tabKey,
    currentThreadIdentity = currentThreadIdentity,
    newThreadRebindRequestedAtMs = newThreadRebindRequestedAtMs,
  )
}

private fun successfulPendingCodexRebindReport(
  requestsByPath: Map<String, List<AgentChatPendingTabRebindRequest>>,
): AgentChatPendingTabRebindReport {
  val outcomesByPath = LinkedHashMap<String, List<AgentChatPendingTabRebindOutcome>>()
  var requestedBindings = 0
  for ((path, requests) in requestsByPath) {
    requestedBindings += requests.size
    outcomesByPath[path] = requests.map { request ->
      AgentChatPendingTabRebindOutcome(
        projectPath = path,
        request = request,
        status = AgentChatPendingTabRebindStatus.REBOUND,
        reboundFiles = 1,
      )
    }
  }
  return AgentChatPendingTabRebindReport(
    requestedBindings = requestedBindings,
    reboundBindings = requestedBindings,
    reboundFiles = requestedBindings,
    updatedPresentations = requestedBindings,
    outcomesByPath = outcomesByPath,
  )
}

private fun failingPendingCodexRebindReport(
  requestsByPath: Map<String, List<AgentChatPendingTabRebindRequest>>,
): AgentChatPendingTabRebindReport {
  val outcomesByPath = LinkedHashMap<String, List<AgentChatPendingTabRebindOutcome>>()
  var requestedBindings = 0
  for ((path, requests) in requestsByPath) {
    requestedBindings += requests.size
    outcomesByPath[path] = requests.map { request ->
      AgentChatPendingTabRebindOutcome(
        projectPath = path,
        request = request,
        status = AgentChatPendingTabRebindStatus.PENDING_TAB_NOT_OPEN,
        reboundFiles = 0,
      )
    }
  }
  return AgentChatPendingTabRebindReport(
    requestedBindings = requestedBindings,
    reboundBindings = 0,
    reboundFiles = 0,
    updatedPresentations = 0,
    outcomesByPath = outcomesByPath,
  )
}

private fun createSymbolicLinkOrSkip(link: Path, target: Path) {
  try {
    Files.createSymbolicLink(link, target)
  }
  catch (t: Throwable) {
    assumeTrue(false, "Symbolic links are unavailable: ${t.message}")
  }
}

private suspend fun withLoadingCoordinator(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<ProjectEntry> = { emptyList() },
  isRefreshGateActive: suspend () -> Boolean,
  providerDescriptors: List<AgentSessionProviderDescriptor> = testRefreshCoordinatorProviderDescriptors(),
  openChatPathsProvider: suspend () -> Set<String> = { emptySet() },
  selectedChatThreadIdentityProvider: suspend () -> Pair<AgentSessionProvider, String>? = { null },
  codexScopedRefreshSignalsProvider: () -> kotlinx.coroutines.flow.Flow<Set<String>> = {
    kotlinx.coroutines.flow.emptyFlow()
  },
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingTabSnapshot>> = { emptyMap() },
  openPendingClaudeTabsProvider: suspend () -> Map<String, List<AgentChatPendingTabSnapshot>> = { emptyMap() },
  openConcreteCodexTabsAwaitingNewThreadRebindProvider: suspend () -> Map<String, List<AgentChatConcreteTabSnapshot>> = { emptyMap() },
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> = { emptyMap() },
  openAgentChatSnapshotProvider: (suspend () -> AgentChatOpenTabsRefreshSnapshot)? = null,
  presentationModel: AgentSessionThreadPresentationModel = AgentSessionThreadPresentationModel(),
  openChatPendingTabsBinder: suspend (Map<String, List<AgentChatPendingTabRebindRequest>>) -> AgentChatPendingTabRebindReport = {
    failingPendingCodexRebindReport(
      requestsByPath = it,
    )
  },
  clearOpenConcreteCodexTabAnchors: (Map<String, List<AgentChatConcreteTabSnapshot>>) -> Int = { tabsByPath ->
    tabsByPath.values.sumOf { it.size }
  },
  scheduleVfsRefresh: (Set<String>) -> Unit = { _ -> },
  isVfsRefreshOnStatusUpdatesEnabled: (String) -> Boolean = { true },
  warmState: InMemorySessionWarmState = InMemorySessionWarmState(),
  action: suspend (AgentSessionRefreshCoordinator, AgentSessionsStateStore) -> Unit,
) {
  @Suppress("RAW_SCOPE_CREATION")
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val stateStore = AgentSessionsStateStore()
  val contentRepository = AgentSessionContentRepository(
    stateStore = stateStore,
    warmState = warmState,
  )
  try {
    val coordinator = AgentSessionRefreshCoordinator(
      serviceScope = scope,
      sessionSourcesProvider = sessionSourcesProvider,
      projectEntriesProvider = projectEntriesProvider,
      stateStore = stateStore,
      contentRepository = contentRepository,
      isRefreshGateActive = isRefreshGateActive,
      scheduleVfsRefresh = scheduleVfsRefresh,
      isVfsRefreshOnStatusUpdatesEnabled = isVfsRefreshOnStatusUpdatesEnabled,
      providerDescriptorsByIdProvider = { providerDescriptors },
      providerDescriptorProvider = { provider -> providerDescriptors.firstOrNull { it.provider == provider } },
      openAgentChatSnapshotProvider = openAgentChatSnapshotProvider ?: {
        buildOpenChatRefreshSnapshot(
          openProjectPaths = openChatPathsProvider(),
          selectedChatThreadIdentity = selectedChatThreadIdentityProvider(),
          pendingTabsByProvider = mapOf(
            AgentSessionProvider.CODEX to openPendingCodexTabsProvider(),
            AgentSessionProvider.CLAUDE to openPendingClaudeTabsProvider(),
          ),
          concreteTabsAwaitingNewThreadRebindByProvider = mapOf(
            AgentSessionProvider.CODEX to openConcreteCodexTabsAwaitingNewThreadRebindProvider(),
          ),
          concreteThreadIdentitiesByPath = openConcreteChatThreadIdentitiesByPathProvider(),
        )
      },
      scopedRefreshSignalsProvider = { _ -> codexScopedRefreshSignalsProvider().map { paths -> threadsChangedEvent(scopedPaths = paths) } },
      presentationModel = presentationModel,
      openAgentChatPendingTabsBinder = { _, requestsByPath -> openChatPendingTabsBinder(requestsByPath) },
      clearOpenConcreteNewThreadRebindAnchors = { _, tabsByPath -> clearOpenConcreteCodexTabAnchors(tabsByPath) },
    )
    action(coordinator, stateStore)
  }
  finally {
    scope.cancel()
  }
}

private fun presentationKey(
  path: String,
  provider: AgentSessionProvider,
  threadId: String,
): AgentSessionThreadPresentationKey {
  return checkNotNull(AgentSessionThreadPresentationKey.create(path, provider, threadId))
}

private fun testRefreshCoordinatorProviderDescriptors(): List<AgentSessionProviderDescriptor> {
  return listOf(
    TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = emptySet(),
      cliAvailable = true,
      supportsPendingEditorTabRebind = true,
      supportsNewThreadRebind = true,
      emitsScopedRefreshSignals = true,
      refreshPathAfterCreateNewSession = true,
    ),
    TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = emptySet(),
      cliAvailable = true,
      supportsPendingEditorTabRebind = true,
      emitsScopedRefreshSignals = true,
      refreshPathAfterCreateNewSession = true,
    ),
  )
}
