// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindStatus
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
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.service.AgentSessionContentRepository
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
            hasLoaded = true,
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
            hasLoaded = true,
            threads = listOf(thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = projectB,
            name = "Project B",
            isOpen = true,
            hasLoaded = true,
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
  fun threadTargetedUpdateFallsBackToFullRefreshWhenThreadCannotBeResolved() = runBlocking(Dispatchers.Default) {
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
            hasLoaded = true,
            threads = listOf(thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
          AgentProjectSessions(
            path = projectB,
            name = "Project B",
            isOpen = true,
            hasLoaded = true,
            threads = listOf(thread(id = "codex-b", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(hintsChangedEvent(threadIds = setOf("missing-thread")))

      waitForCondition {
        val projects = stateStore.snapshot().projects.associateBy { it.path }
        projects[PROJECT_PATH]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt == 300L &&
        projects[projectB]?.threads?.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt == 400L
      }

      assertThat(closedRefreshInvocations[PROJECT_PATH]?.get() ?: 0).isEqualTo(1)
      assertThat(closedRefreshInvocations[projectB]?.get() ?: 0).isEqualTo(1)
    }
  }

  @Test
  fun threadTargetedUpdateUsesOpenConcreteTabIdentityWhenStateDoesNotContainThread() = runBlocking(Dispatchers.Default) {
    val projectB = "/work/project-b"
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val closedRefreshInvocations = LinkedHashMap<String, AtomicInteger>()
    val receivedTitleMaps = mutableListOf<Map<Pair<String, String>, String>>()
    val receivedActivityMaps = mutableListOf<Map<Pair<String, String>, AgentThreadActivity>>()

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
            threads = listOf(thread(id = "codex-a", updatedAt = 100L, provider = AgentSessionProvider.CODEX)),
          ),
        ),
        visibleThreadCounts = emptyMap(),
      )

      coordinator.observeSessionSourceUpdates()
      updates.tryEmit(hintsChangedEvent(threadIds = setOf("codex-open")))

      val expectedKey = projectB to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-open")
      waitForCondition {
        receivedTitleMaps.any { it[expectedKey] == "Open concrete tab thread" }
      }
      waitForCondition {
        receivedActivityMaps.any { it[expectedKey] == AgentThreadActivity.UNREAD }
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
            hasLoaded = true,
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
    val receivedTitleMaps = mutableListOf<Map<Pair<String, String>, String>>()
    val receivedActivityMaps = mutableListOf<Map<Pair<String, String>, AgentThreadActivity>>()

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
      updates.tryEmit(threadsChangedEvent())

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
            hasLoaded = true,
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
      openChatConcreteTabsBinder = { _ ->
        error("Claude pending refresh must not use concrete /new rebind")
      },
    ) { coordinator, stateStore ->
      stateStore.replaceProjects(
        projects = listOf(
          AgentProjectSessions(
            path = PROJECT_PATH,
            name = "Project A",
            isOpen = true,
            hasLoaded = true,
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
            hasLoaded = true,
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
            hasLoaded = true,
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
  fun providerUpdatePassesListedCodexThreadIdsToRefreshHintsWhenBaselineIsEmpty() = runBlocking(Dispatchers.Default) {
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val capturedKnownThreadIdsByPath = mutableListOf<Map<String, Set<String>>>()

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
      updates.tryEmit(threadsChangedEvent())

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
    val updates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val capturedKnownThreadIdsByPath = mutableListOf<Map<String, Set<String>>>()

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
      updates.tryEmit(threadsChangedEvent())

      waitForCondition {
        capturedKnownThreadIdsByPath.isNotEmpty()
      }

      assertThat(capturedKnownThreadIdsByPath.last()[PROJECT_PATH]).containsExactly("codex-listed")
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
              hasLoaded = true,
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

  @Test
  fun concreteCodexScopedRefreshRebindsAnchoredTabsFromRefreshHintsOnly() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val rebindInvocations = mutableListOf<ConcreteCodexRebindInvocation>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val rebindRequestedAtMs = System.currentTimeMillis() - 1_000L
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
          listOf(
            thread(id = "codex-old", updatedAt = 100L, title = "Old thread", provider = AgentSessionProvider.CODEX),
            thread(id = "codex-listed-new", updatedAt = rebindRequestedAtMs + 100L, title = "Listed thread", provider = AgentSessionProvider.CODEX),
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
      openChatConcreteTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              ConcreteCodexRebindInvocation(
                path = path,
                tabKey = request.tabKey,
                currentThreadIdentity = request.currentThreadIdentity,
                newThreadRebindRequestedAtMs = request.newThreadRebindRequestedAtMs,
                target = request.target,
              )
            )
          }
        }
        successfulConcreteCodexRebindReport(requestsByPath)
      },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() > 0 && rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.tabKey).isEqualTo("concrete-codex-old")
      assertThat(invocation.currentThreadIdentity).isEqualTo(oldIdentity)
      assertThat(invocation.newThreadRebindRequestedAtMs).isEqualTo(rebindRequestedAtMs)
      assertThat(invocation.target.threadIdentity)
        .isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-hinted-new"))
      assertThat(invocation.target.threadId).isEqualTo("codex-hinted-new")
      assertThat(invocation.target.threadTitle).isEqualTo("Hinted thread")
      assertThat(invocation.target.threadActivity).isEqualTo(AgentThreadActivity.UNREAD)
    }
  }

  @Test
  fun concreteCodexScopedRefreshUsesHintCandidateForListedNewThread() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val rebindInvocations = mutableListOf<ConcreteCodexRebindInvocation>()
    val capturedKnownThreadIdsByPath = mutableListOf<Map<String, Set<String>>>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val rebindRequestedAtMs = System.currentTimeMillis() - 1_000L
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
          listOf(
            thread(id = "codex-old", updatedAt = 100L, title = "Old thread", provider = AgentSessionProvider.CODEX),
            thread(id = "codex-listed-new", updatedAt = rebindRequestedAtMs + 100L, title = "Listed new thread", provider = AgentSessionProvider.CODEX),
          )
        }
      },
      prefetchRefreshHintsProvider = { paths, knownThreadIdsByPath ->
        if (PROJECT_PATH !in paths) {
          emptyMap()
        }
        else {
          capturedKnownThreadIdsByPath += knownThreadIdsByPath.mapValues { (_, ids) -> ids.toSet() }
          if ("codex-listed-new" in knownThreadIdsByPath[PROJECT_PATH].orEmpty()) {
            emptyMap()
          }
          else {
            mapOf(
              PROJECT_PATH to AgentSessionRefreshHints(
                rebindCandidates = listOf(
                  AgentSessionRebindCandidate(
                    threadId = "codex-listed-new",
                    title = "Listed new thread",
                    updatedAt = rebindRequestedAtMs + 100L,
                    activity = AgentThreadActivity.UNREAD,
                  )
                )
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
      openConcreteCodexTabsAwaitingNewThreadRebindProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            concreteCodexTab(
              currentThreadIdentity = oldIdentity,
              tabKey = "concrete-codex-listed",
              newThreadRebindRequestedAtMs = rebindRequestedAtMs,
            )
          )
        )
      },
      openConcreteChatThreadIdentitiesByPathProvider = {
        mapOf(PROJECT_PATH to setOf(oldIdentity))
      },
      openChatConcreteTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              ConcreteCodexRebindInvocation(
                path = path,
                tabKey = request.tabKey,
                currentThreadIdentity = request.currentThreadIdentity,
                newThreadRebindRequestedAtMs = request.newThreadRebindRequestedAtMs,
                target = request.target,
              )
            )
          }
        }
        successfulConcreteCodexRebindReport(requestsByPath)
      },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        closedRefreshInvocations.get() > 0 && rebindInvocations.isNotEmpty()
      }

      assertThat(capturedKnownThreadIdsByPath).isNotEmpty()
      assertThat(capturedKnownThreadIdsByPath.last()[PROJECT_PATH]).containsExactly("codex-old")
      val invocation = rebindInvocations.single()
      assertThat(invocation.target.threadIdentity)
        .isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-listed-new"))
    }
  }

  @Test
  fun concreteCodexScopedRefreshPrioritizesExplicitNewThreadRebindOverPendingTabs() = runBlocking(Dispatchers.Default) {
    val concreteRebindInvocations = mutableListOf<ConcreteCodexRebindInvocation>()
    val pendingRebindInvocations = mutableListOf<PendingCodexRebindInvocation>()
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val rebindRequestedAtMs = System.currentTimeMillis() - 1_000L
    val oldIdentity = buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-old")
    val targetIdentity = buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-hinted-new")
    val openConcreteIdentities = LinkedHashSet<String>().apply { add(oldIdentity) }

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
        mapOf(PROJECT_PATH to openConcreteIdentities.toSet())
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
      openChatConcreteTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            concreteRebindInvocations.add(
              ConcreteCodexRebindInvocation(
                path = path,
                tabKey = request.tabKey,
                currentThreadIdentity = request.currentThreadIdentity,
                newThreadRebindRequestedAtMs = request.newThreadRebindRequestedAtMs,
                target = request.target,
              )
            )
            openConcreteIdentities.add(request.target.threadIdentity)
          }
        }
        successfulConcreteCodexRebindReport(requestsByPath)
      },
    ) { coordinator, _ ->
      coordinator.observeSessionSourceUpdates()
      scopedRefreshSignals.tryEmit(setOf(PROJECT_PATH))

      waitForCondition {
        concreteRebindInvocations.isNotEmpty()
      }
      delay(150.milliseconds)

      val invocation = concreteRebindInvocations.single()
      assertThat(invocation.target.threadIdentity).isEqualTo(targetIdentity)
      assertThat(pendingRebindInvocations).isEmpty()
    }
  }

  @Test
  fun concreteCodexScopedRefreshSkipsAmbiguousMatches() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val binderInvocations = AtomicInteger(0)
    val scopedRefreshSignals = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val rebindRequestedAtMs = System.currentTimeMillis() - 1_000L
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
                  threadId = "codex-hinted-a",
                  title = "Hinted thread A",
                  updatedAt = rebindRequestedAtMs + 100L,
                  activity = AgentThreadActivity.UNREAD,
                ),
                AgentSessionRebindCandidate(
                  threadId = "codex-hinted-b",
                  title = "Hinted thread B",
                  updatedAt = rebindRequestedAtMs + 150L,
                  activity = AgentThreadActivity.UNREAD,
                ),
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
              tabKey = "ambiguous-concrete-codex-old",
              newThreadRebindRequestedAtMs = rebindRequestedAtMs,
            )
          )
        )
      },
      openConcreteChatThreadIdentitiesByPathProvider = {
        mapOf(PROJECT_PATH to setOf(oldIdentity))
      },
      openChatConcreteTabsBinder = { requestsByPath ->
        binderInvocations.incrementAndGet()
        successfulConcreteCodexRebindReport(requestsByPath)
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
  fun concreteCodexScopedRefreshClearsStaleAnchorsWithoutRebinding() = runBlocking(Dispatchers.Default) {
    val closedRefreshInvocations = AtomicInteger(0)
    val binderInvocations = AtomicInteger(0)
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
      openChatConcreteTabsBinder = { requestsByPath ->
        binderInvocations.incrementAndGet()
        successfulConcreteCodexRebindReport(requestsByPath)
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

      assertThat(binderInvocations.get()).isEqualTo(0)
      assertThat(clearedTabs).containsExactly(
        concreteCodexTab(
          currentThreadIdentity = oldIdentity,
          tabKey = "stale-concrete-codex-old",
          newThreadRebindRequestedAtMs = staleRequestedAtMs,
        )
      )
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
): AgentChatPendingTabSnapshot {
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

private fun successfulConcreteCodexRebindReport(
  requestsByPath: Map<String, List<AgentChatConcreteTabRebindRequest>>,
): AgentChatConcreteTabRebindReport {
  val outcomesByPath = LinkedHashMap<String, List<com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindOutcome>>()
  var requestedBindings = 0
  for ((path, requests) in requestsByPath) {
    requestedBindings += requests.size
    outcomesByPath[path] = requests.map { request ->
      com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindOutcome(
        projectPath = path,
        request = request,
        status = AgentChatConcreteTabRebindStatus.REBOUND,
        reboundFiles = 1,
      )
    }
  }
  return AgentChatConcreteTabRebindReport(
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
  providerDescriptors: List<AgentSessionProviderDescriptor> = testRefreshCoordinatorProviderDescriptors(),
  openChatPathsProvider: suspend () -> Set<String> = { emptySet() },
  selectedChatThreadIdentityProvider: suspend () -> Pair<AgentSessionProvider, String>? = { null },
  codexScopedRefreshSignalsProvider: () -> kotlinx.coroutines.flow.Flow<Set<String>> = {
    kotlinx.coroutines.flow.emptyFlow()
  },
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingTabSnapshot>> = { emptyMap() },
  openConcreteCodexTabsAwaitingNewThreadRebindProvider: suspend () -> Map<String, List<AgentChatConcreteTabSnapshot>> = { emptyMap() },
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> = { emptyMap() },
  openAgentChatSnapshotProvider: (suspend () -> AgentChatOpenTabsRefreshSnapshot)? = null,
  openChatTabPresentationUpdater: suspend (
    Map<Pair<String, String>, String>,
    Map<Pair<String, String>, AgentThreadActivity>,
  ) -> Int = { _, _ -> 0 },
  openChatPendingTabsBinder: suspend (Map<String, List<AgentChatPendingTabRebindRequest>>) -> AgentChatPendingTabRebindReport = {
    failingPendingCodexRebindReport(
      requestsByPath = it,
    )
  },
  openChatConcreteTabsBinder: suspend (Map<String, List<AgentChatConcreteTabRebindRequest>>) -> AgentChatConcreteTabRebindReport = {
    successfulConcreteCodexRebindReport(requestsByPath = it)
  },
  clearOpenConcreteCodexTabAnchors: (Map<String, List<AgentChatConcreteTabSnapshot>>) -> Int = { tabsByPath ->
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
      providerDescriptorsByIdProvider = { providerDescriptors },
      providerDescriptorProvider = { provider -> providerDescriptors.firstOrNull { it.provider == provider } },
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
      scopedRefreshSignalsProvider = { _ -> codexScopedRefreshSignalsProvider() },
      openAgentChatTabPresentationUpdater = openChatTabPresentationUpdater,
      openAgentChatPendingTabsBinder = { _, requestsByPath -> openChatPendingTabsBinder(requestsByPath) },
      openAgentChatConcreteTabsBinder = { _, requestsByPath -> openChatConcreteTabsBinder(requestsByPath) },
      clearOpenConcreteNewThreadRebindAnchors = { _, tabsByPath -> clearOpenConcreteCodexTabAnchors(tabsByPath) },
    )
    action(coordinator, stateStore)
  }
  finally {
    scope.cancel()
  }
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
