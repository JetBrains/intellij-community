// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindOutcome
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshCoordinator
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
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class AgentSessionRefreshCoordinatorTest {
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
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-2")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-2")
      val target = invocation.target
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-2"))
      assertThat(target.threadId).isEqualTo("codex-2")
    }
  }

  @Test
  fun providerUpdateUsesRefreshHintsForCodexRebindWithoutNewProviderIds() = runBlocking(Dispatchers.Default) {
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
            hasLoaded = true,
            threads = listOf(thread(id = "codex-existing", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
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
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-hint")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-hint")
      assertThat(invocation.target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-hint"))
      assertThat(invocation.target.threadId).isEqualTo("codex-hint")

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
  fun providerUpdateAppliesCodexActivityHintsWhenAvailable() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

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
              activityByThreadId = mapOf(
                "codex-with-hint" to AgentThreadActivity.REVIEWING,
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
            hasLoaded = true,
            threads = listOf(
              thread(id = "codex-with-hint", updatedAt = 100L, provider = AgentSessionProvider.CODEX),
              thread(id = "codex-without-hint", updatedAt = 90L, provider = AgentSessionProvider.CODEX),
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(Unit)

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
  fun providerUpdatePassesListedCodexThreadIdsToRefreshHintsWhenBaselineIsEmpty() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val capturedKnownThreadIdsByPath = mutableListOf<Map<String, Set<String>>>()

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
      prefetchRefreshHintsProvider = { paths, knownThreadIdsByPath ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          capturedKnownThreadIdsByPath += knownThreadIdsByPath.mapValues { (_, ids) -> ids.toSet() }
          mapOf(
            PROJECT_PATH to AgentSessionRefreshHints(
              activityByThreadId = mapOf(
                "codex-listed" to AgentThreadActivity.UNREAD,
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
            hasLoaded = true,
            threads = emptyList(),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(Unit)

      waitForCondition {
        stateStore.snapshot().projects
          .firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull { it.id == "codex-listed" }
          ?.activity == AgentThreadActivity.UNREAD
      }

      assertThat(capturedKnownThreadIdsByPath).isNotEmpty()
      assertThat(capturedKnownThreadIdsByPath.last()[PROJECT_PATH]).containsExactly("codex-listed")
    }
  }

  @Test
  fun providerUpdateDoesNotPassPendingNewThreadIdsToRefreshHints() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val capturedKnownThreadIdsByPath = mutableListOf<Map<String, Set<String>>>()

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
      prefetchRefreshHintsProvider = { paths, knownThreadIdsByPath ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          capturedKnownThreadIdsByPath += knownThreadIdsByPath.mapValues { (_, ids) -> ids.toSet() }
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
            hasLoaded = true,
            threads = listOf(
              thread(id = "new-pending", updatedAt = 600L, provider = AgentSessionProvider.CODEX),
            ),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(Unit)

      waitForCondition {
        capturedKnownThreadIdsByPath.isNotEmpty()
      }

      assertThat(capturedKnownThreadIdsByPath.last()[PROJECT_PATH]).containsExactly("codex-listed")
    }
  }

  @Test
  fun providerUpdateBuildsLightweightPendingTabRebindTargets() {
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
              hasLoaded = true,
              threads = listOf(thread(id = "codex-base", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
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
            hasLoaded = true,
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
            hasLoaded = true,
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
      codexScopedRefreshSignalsProvider = { scopedRefreshSignals },
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
          outputPath -> listOf(thread(id = "codex-output", updatedAt = 700L, title = "Output thread", provider = AgentSessionProvider.CODEX))
          PROJECT_PATH -> listOf(thread(id = "codex-loaded", updatedAt = 999L, title = "Loaded thread", provider = AgentSessionProvider.CODEX))
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
            hasLoaded = true,
            threads = listOf(thread(id = "codex-loaded", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = outputPath,
            name = "Output",
            isOpen = true,
            hasLoaded = false,
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
  fun pendingCodexScopedRefreshProjectsPendingThreadsForUnloadedPath() = runBlocking(Dispatchers.Default) {
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
            hasLoaded = false,
            threads = emptyList(),
          )
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(pendingPath))

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
            hasLoaded = true,
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

}

private data class PendingCodexRebindInvocation(
  @JvmField val path: String,
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val target: AgentChatTabRebindTarget,
)

private data class ConcreteCodexRebindInvocation(
  @JvmField val path: String,
  @JvmField val tabKey: String,
  @JvmField val currentThreadIdentity: String,
  @JvmField val newThreadRebindRequestedAtMs: Long,
  @JvmField val target: AgentChatTabRebindTarget,
)

private fun pendingCodexTab(
  pendingThreadIdentity: String,
  projectPath: String = PROJECT_PATH,
  pendingTabKey: String = "pending-$pendingThreadIdentity",
  pendingCreatedAtMs: Long? = null,
  pendingFirstInputAtMs: Long? = null,
  pendingLaunchMode: String? = null,
): AgentChatPendingCodexTabSnapshot {
  return AgentChatPendingCodexTabSnapshot(
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
): AgentChatConcreteCodexTabSnapshot {
  return AgentChatConcreteCodexTabSnapshot(
    projectPath = projectPath,
    tabKey = tabKey,
    currentThreadIdentity = currentThreadIdentity,
    newThreadRebindRequestedAtMs = newThreadRebindRequestedAtMs,
  )
}

private fun successfulPendingCodexRebindReport(
  requestsByPath: Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
): AgentChatPendingCodexTabRebindReport {
  val outcomesByPath = LinkedHashMap<String, List<AgentChatPendingCodexTabRebindOutcome>>()
  var requestedBindings = 0
  for ((path, requests) in requestsByPath) {
    requestedBindings += requests.size
    outcomesByPath[path] = requests.map { request ->
      AgentChatPendingCodexTabRebindOutcome(
        projectPath = path,
        request = request,
        status = AgentChatPendingCodexTabRebindStatus.REBOUND,
        reboundFiles = 1,
      )
    }
  }
  return AgentChatPendingCodexTabRebindReport(
    requestedBindings = requestedBindings,
    reboundBindings = requestedBindings,
    reboundFiles = requestedBindings,
    updatedPresentations = requestedBindings,
    outcomesByPath = outcomesByPath,
  )
}

private fun failingPendingCodexRebindReport(
  requestsByPath: Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
): AgentChatPendingCodexTabRebindReport {
  val outcomesByPath = LinkedHashMap<String, List<AgentChatPendingCodexTabRebindOutcome>>()
  var requestedBindings = 0
  for ((path, requests) in requestsByPath) {
    requestedBindings += requests.size
    outcomesByPath[path] = requests.map { request ->
      AgentChatPendingCodexTabRebindOutcome(
        projectPath = path,
        request = request,
        status = AgentChatPendingCodexTabRebindStatus.PENDING_TAB_NOT_OPEN,
        reboundFiles = 0,
      )
    }
  }
  return AgentChatPendingCodexTabRebindReport(
    requestedBindings = requestedBindings,
    reboundBindings = 0,
    reboundFiles = 0,
    updatedPresentations = 0,
    outcomesByPath = outcomesByPath,
  )
}

private fun successfulConcreteCodexRebindReport(
  requestsByPath: Map<String, List<AgentChatConcreteCodexTabRebindRequest>>,
): AgentChatConcreteCodexTabRebindReport {
  val outcomesByPath = LinkedHashMap<String, List<com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindOutcome>>()
  var requestedBindings = 0
  for ((path, requests) in requestsByPath) {
    requestedBindings += requests.size
    outcomesByPath[path] = requests.map { request ->
      com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindOutcome(
        projectPath = path,
        request = request,
        status = AgentChatConcreteCodexTabRebindStatus.REBOUND,
        reboundFiles = 1,
      )
    }
  }
  return AgentChatConcreteCodexTabRebindReport(
    requestedBindings = requestedBindings,
    reboundBindings = requestedBindings,
    reboundFiles = requestedBindings,
    updatedPresentations = requestedBindings,
    outcomesByPath = outcomesByPath,
  )
}

private suspend fun withLoadingCoordinator(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<ProjectEntry> = { emptyList() },
  isRefreshGateActive: suspend () -> Boolean,
  openChatPathsProvider: suspend () -> Set<String> = { emptySet() },
  selectedChatThreadIdentityProvider: suspend () -> Pair<AgentSessionProvider, String>? = { null },
  codexScopedRefreshSignalsProvider: () -> kotlinx.coroutines.flow.Flow<Set<String>> = {
    kotlinx.coroutines.flow.emptyFlow()
  },
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingCodexTabSnapshot>> = { emptyMap() },
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> = { emptyMap() },
  openAgentChatSnapshotProvider: (suspend () -> AgentChatOpenTabsRefreshSnapshot)? = null,
  openChatTabPresentationUpdater: suspend (
    Map<Pair<String, String>, String>,
    Map<Pair<String, String>, AgentThreadActivity>,
  ) -> Int = { _, _ -> 0 },
  openChatPendingTabsBinder: suspend (Map<String, List<AgentChatPendingCodexTabRebindRequest>>) -> AgentChatPendingCodexTabRebindReport = {
    failingPendingCodexRebindReport(
      requestsByPath = it,
    )
  },
  openChatConcreteTabsBinder: suspend (Map<String, List<AgentChatConcreteCodexTabRebindRequest>>) -> AgentChatConcreteCodexTabRebindReport = {
    successfulConcreteCodexRebindReport(requestsByPath = it)
  },
  clearOpenConcreteCodexTabAnchors: (Map<String, List<AgentChatConcreteCodexTabSnapshot>>) -> Int = { tabsByPath ->
    tabsByPath.values.sumOf { it.size }
  },
  action: suspend (AgentSessionRefreshCoordinator, AgentSessionsStateStore) -> Unit,
) {
  @Suppress("RAW_SCOPE_CREATION")
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val stateStore = AgentSessionsStateStore()
  val contentRepository = AgentSessionContentRepository(
    stateStore = stateStore,
    warmState = InMemorySessionWarmState(),
  )
  try {
    val coordinator = AgentSessionRefreshCoordinator(
      serviceScope = scope,
      sessionSourcesProvider = sessionSourcesProvider,
      projectEntriesProvider = projectEntriesProvider,
      stateStore = stateStore,
      contentRepository = contentRepository,
      isRefreshGateActive = isRefreshGateActive,
      openAgentChatSnapshotProvider = openAgentChatSnapshotProvider ?: {
        buildOpenChatRefreshSnapshot(
          openProjectPaths = openChatPathsProvider(),
          selectedChatThreadIdentity = selectedChatThreadIdentityProvider(),
          pendingTabsByProvider = mapOf(AgentSessionProvider.CODEX to openPendingCodexTabsProvider()),
          concreteTabsAwaitingNewThreadRebindByProvider = mapOf(
            AgentSessionProvider.CODEX to openConcreteCodexTabsAwaitingNewThreadRebindProvider(),
          ),
          concreteThreadIdentitiesByPath = openConcreteChatThreadIdentitiesByPathProvider(),
        )
      },
      codexScopedRefreshSignalsProvider = { _ -> codexScopedRefreshSignalsProvider() },
      openAgentChatTabPresentationUpdater = openChatTabPresentationUpdater,
      openAgentChatPendingTabsBinder = { _, requestsByPath -> openChatPendingTabsBinder(requestsByPath) },
      openAgentChatConcreteTabsBinder = { _, requestsByPath -> openChatConcreteTabsBinder(requestsByPath) },
      clearOpenConcreteCodexTabAnchors = { _, tabsByPath -> clearOpenConcreteCodexTabAnchors(tabsByPath) },
    )
    action(coordinator, stateStore)
  }
  finally {
    scope.cancel()
  }
}
