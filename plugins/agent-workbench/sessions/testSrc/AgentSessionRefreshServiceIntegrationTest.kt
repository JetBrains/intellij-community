// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindOutcome
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.session.AgentSessionCostKind
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmPathSnapshot
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmStateService
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_THREAD_COUNT
import com.intellij.agent.workbench.sessions.state.InMemorySessionWarmState
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionRefreshServiceIntegrationTest {
  @BeforeEach
  fun setUp() {
    AgentSessionCostPresentationSettings.setEnabled(true)
    service<AgentSessionThreadPresentationModel>().clearForTests()
  }

  @AfterEach
  fun tearDown() {
    AgentSessionCostPresentationSettings.setEnabled(false)
    service<AgentSessionThreadPresentationModel>().clearForTests()
  }

  @Test
  fun refreshHydratesCostsOnlyForVisibleThreadsAndShowMoreLoadsNewlyVisibleThread() = runBlocking(Dispatchers.Default) {
    val costLoadRequests = CopyOnWriteArrayList<List<String>>()
    val threads = listOf(
      thread(id = "thread-4", updatedAt = 400, provider = AgentSessionProvider.from("codex")),
      thread(id = "thread-3", updatedAt = 300, provider = AgentSessionProvider.from("codex")),
      thread(id = "thread-2", updatedAt = 200, provider = AgentSessionProvider.from("codex")),
      thread(id = "thread-1", updatedAt = 100, provider = AgentSessionProvider.from("codex")),
    )

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            listFromOpenProject = { _, _ -> threads },
            loadThreadCostsProvider = { _, requestedThreads ->
              costLoadRequests += requestedThreads.map { thread -> thread.id }
              requestedThreads.associate { thread ->
                thread.id to AgentSessionCost(
                  amountUsd = BigDecimal(thread.id.substringAfterLast('-')),
                  kind = AgentSessionCostKind.ESTIMATED,
                )
              }
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.threads.size == threads.size &&
        project.threads.take(3).all { thread -> thread.cost != null } &&
        project.threads.drop(3).all { thread -> thread.cost == null }
      }

      assertThat(costLoadRequests.toList()).containsExactly(listOf("thread-4", "thread-3", "thread-2"))

      service.showMoreThreads(PROJECT_PATH)

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.threads.size == threads.size && project.threads.all { thread -> thread.cost != null }
      }

      assertThat(costLoadRequests.toList()).containsExactly(
        listOf("thread-4", "thread-3", "thread-2"),
        listOf("thread-1"),
      )
    }
  }

  @Test
  fun visibleThreadCostCacheSkipsReloadUntilThreadUpdatedAtChanges() = runBlocking(Dispatchers.Default) {
    var nowMs = 1_000L
    var updatedAt = 100L
    val costLoadCount = AtomicInteger(0)

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { _, _ ->
              listOf(thread(id = "claude-1", updatedAt = updatedAt, provider = AgentSessionProvider.from("claude")))
            },
            loadThreadCostsProvider = { _, requestedThreads ->
              val loadNumber = costLoadCount.incrementAndGet()
              requestedThreads.associate { thread ->
                thread.id to AgentSessionCost(
                  amountUsd = BigDecimal(loadNumber),
                  kind = AgentSessionCostKind.EXACT,
                )
              }
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      currentTimeMillis = { nowMs },
    ) { service ->
      service.refresh()

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.singleOrNull()
          ?.cost
          ?.amountUsd == BigDecimal.ONE
      }
      assertThat(costLoadCount.get()).isEqualTo(1)

      service.refresh()
      delay(300.milliseconds)
      assertThat(costLoadCount.get()).isEqualTo(1)

      nowMs += 59_000L
      service.refresh()
      delay(300.milliseconds)
      assertThat(costLoadCount.get()).isEqualTo(1)

      nowMs += 3600_000L
      service.refresh()
      delay(300.milliseconds)
      assertThat(costLoadCount.get()).isEqualTo(1)

      updatedAt = 200L
      nowMs += 1_000L
      service.refresh()
      waitForCondition {
        val thread = service.state.value.projects
                       .firstOrNull { it.path == PROJECT_PATH }
                       ?.threads
                       ?.singleOrNull() ?: return@waitForCondition false
        thread.updatedAt == 200L && thread.cost?.amountUsd == BigDecimal.valueOf(2)
      }
      assertThat(costLoadCount.get()).isEqualTo(2)
    }
  }

  @Test
  fun persistedWarmSnapshotCostSurvivesRefreshAndSkipsReload() = runBlocking(Dispatchers.Default) {
    val persistedWarmState = AgentSessionWarmStateService()
    persistedWarmState.setPathSnapshot(
      PROJECT_PATH,
      AgentSessionWarmPathSnapshot(
        threads = listOf(
          thread(
            id = "claude-1",
            updatedAt = 100L,
            provider = AgentSessionProvider.from("claude"),
            cost = AgentSessionCost(
              amountUsd = BigDecimal("1.50"),
              kind = AgentSessionCostKind.EXACT,
            ),
          )
        ),
        providerLoadStates = loadedProviderStates(AgentSessionProvider.from("claude")),
        updatedAt = 100L,
      ),
    )
    val reloadedWarmState = AgentSessionWarmStateService()
    reloadedWarmState.loadState(persistedWarmState.state)
    val costLoadCount = AtomicInteger(0)

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { _, _ ->
              listOf(thread(id = "claude-1", updatedAt = 100L, provider = AgentSessionProvider.from("claude")))
            },
            loadThreadCostsProvider = { _, _ ->
              costLoadCount.incrementAndGet()
              emptyMap()
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      warmState = reloadedWarmState,
    ) { service ->
      service.refresh()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.LOADED &&
        project.threads.singleOrNull()?.cost?.amountUsd == BigDecimal("1.50")
      }

      delay(300.milliseconds)
      assertThat(costLoadCount.get()).isZero()
    }
  }

  @Test
  fun costHydrationStartsOnlyAfterToolWindowBecomesVisible() = runBlocking(Dispatchers.Default) {
    val toolWindowVisibleFlow = MutableStateFlow(false)
    val costLoadCount = AtomicInteger(0)

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            listFromOpenProject = { _, _ ->
              listOf(thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.from("codex")))
            },
            loadThreadCostsProvider = { _, requestedThreads ->
              costLoadCount.incrementAndGet()
              requestedThreads.associate { thread ->
                thread.id to AgentSessionCost(
                  amountUsd = BigDecimal.ONE,
                  kind = AgentSessionCostKind.ESTIMATED,
                )
              }
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      toolWindowVisibleFlow = toolWindowVisibleFlow,
    ) { service ->
      service.refresh()

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.singleOrNull()?.id == "codex-1"
      }
      delay(300.milliseconds)

      assertThat(costLoadCount.get()).isZero()
      assertThat(service.state.value.projects.first { it.path == PROJECT_PATH }.threads.single().cost).isNull()

      toolWindowVisibleFlow.value = true

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.singleOrNull()
          ?.cost
          ?.amountUsd == BigDecimal.ONE
      }

      assertThat(costLoadCount.get()).isEqualTo(1)
    }
  }

  @Test
  fun costHydrationRefreshesVisibleWorkingThreadWithTtl() = runBlocking(Dispatchers.Default) {
    var nowMs = 1_000L
    val costLoadCount = AtomicInteger(0)
    var activity = AgentThreadActivity.PROCESSING

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            listFromOpenProject = { _, _ ->
              listOf(
                thread(
                  id = "codex-1",
                  updatedAt = 100L,
                  provider = AgentSessionProvider.from("codex"),
                  activity = activity,
                )
              )
            },
            loadThreadCostsProvider = { _, requestedThreads ->
              val loadNumber = costLoadCount.incrementAndGet()
              requestedThreads.associate { thread ->
                thread.id to AgentSessionCost(
                  amountUsd = BigDecimal(loadNumber),
                  kind = AgentSessionCostKind.ESTIMATED,
                )
              }
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      currentTimeMillis = { nowMs },
    ) { service ->
      service.refresh()

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.singleOrNull()
          ?.activity == AgentThreadActivity.PROCESSING
      }

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.singleOrNull()
          ?.cost
          ?.amountUsd == BigDecimal.ONE
      }
      assertThat(costLoadCount.get()).isEqualTo(1)

      service.refresh()
      delay(300.milliseconds)
      assertThat(costLoadCount.get()).isEqualTo(1)

      nowMs += 59_000L
      service.refresh()
      delay(300.milliseconds)
      assertThat(costLoadCount.get()).isEqualTo(1)

      nowMs += 1_001L
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.singleOrNull()
          ?.cost
          ?.amountUsd == BigDecimal.valueOf(2)
      }
      assertThat(costLoadCount.get()).isEqualTo(2)

      activity = AgentThreadActivity.READY
      service.refresh()
      delay(300.milliseconds)
      assertThat(costLoadCount.get()).isEqualTo(2)
    }
  }

  @Test
  fun costHydrationSkipsPendingThreadIds() = runBlocking(Dispatchers.Default) {
    val codexUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val costLoadCount = AtomicInteger(0)
    val refreshCount = AtomicInteger(0)

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            supportsUpdates = true,
            updateEvents = codexUpdates,
            listFromOpenProject = { _, _ -> emptyList() },
            refreshThreadsProvider = { request ->
              refreshCount.incrementAndGet()
              AgentSessionSourceRefreshResult(
                completeThreadsByPath = request.paths.associateWith { emptyList() },
              )
            },
            loadThreadCostsProvider = { _, _ ->
              costLoadCount.incrementAndGet()
              emptyMap()
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            AgentChatPendingTabSnapshot(
              projectPath = PROJECT_PATH,
              pendingTabKey = "pending-codex:new-cost",
              pendingThreadIdentity = "codex:new-cost",
              pendingCreatedAtMs = 200L,
              pendingFirstInputAtMs = null,
              pendingLaunchMode = null,
            )
          )
        )
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.isOpen == true
      }

      codexUpdates.emit(threadsChangedEvent())

      waitForCondition { refreshCount.get() == 1 }

      assertThat(costLoadCount.get()).isZero()
      assertThat(service.state.value.projects.single { it.path == PROJECT_PATH }.threads)
        .isEmpty()
    }
  }

  @Test
  fun refreshCompletesBeforeLoadingDelayWithoutPublishingLoading() = runBlocking(Dispatchers.Default) {
    val testScope = this
    val provider = AgentSessionProvider.from("codex")

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = provider,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 100, provider = provider)) else emptyList()
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      loadingDelayMs = 300L,
    ) { service ->
      val loadingObserved = AtomicBoolean(false)
      val collector = testScope.launch {
        service.state.collect { state ->
          if (state.projects.any { project -> project.isLoading || project.worktrees.any { worktree -> worktree.isLoading } }) {
            loadingObserved.set(true)
          }
        }
      }
      try {
        service.refresh()
        waitForCondition {
          service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
            ?.providerLoadStates
            ?.get(provider) == AgentSessionProviderLoadState.LOADED
        }

        delay(350.milliseconds)

        assertThat(loadingObserved.get()).isFalse()
      }
      finally {
        collector.cancel()
      }
    }
  }

  @Test
  fun refreshPublishesLoadingAfterDelayWhilePrefetchRuns() = runBlocking(Dispatchers.Default) {
    val provider = AgentSessionProvider.from("codex")
    val prefetchStarted = CompletableDeferred<Unit>()
    val releasePrefetch = CompletableDeferred<Unit>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = provider,
            prefetch = { paths ->
              if (PROJECT_PATH in paths) {
                prefetchStarted.complete(Unit)
                releasePrefetch.await()
              }
              emptyMap()
            },
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 100, provider = provider)) else emptyList()
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      loadingDelayMs = 300L,
    ) { service ->
      service.refresh()
      prefetchStarted.await()

      waitForCondition(timeoutMs = 6_000) {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.providerLoadStates
          ?.get(provider) == AgentSessionProviderLoadState.LOADING
      }

      releasePrefetch.complete(Unit)
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.providerLoadStates
          ?.get(provider) == AgentSessionProviderLoadState.LOADED
      }
    }
  }

  @Test
  fun refreshShowsWarmSnapshotThreadsBeforeProviderLoadCompletes() = runBlocking(Dispatchers.Default) {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val warmState = InMemorySessionWarmState()
    warmState.setPathSnapshot(
      PROJECT_PATH,
      AgentSessionWarmPathSnapshot(
        threads = listOf(
          thread(id = "cached-1", updatedAt = 100, title = "Cached", provider = AgentSessionProvider.from("claude"))
        ),
        providerLoadStates = loadedProviderStates(AgentSessionProvider.from("claude")),
        updatedAt = 100,
      ),
    )

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { path, _ ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                started.complete(Unit)
                release.await()
                listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.from("claude")))
              }
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      warmState = warmState,
    ) { service ->
      service.refresh()
      started.await()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.LOADING &&
        project.threads.map { it.id } == listOf("cached-1")
      }

      release.complete(Unit)
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { it.id } == listOf("claude-1")
      }

      waitForCondition {
        warmState.getPathSnapshot(PROJECT_PATH)?.threads.orEmpty().map { it.id } == listOf("claude-1")
      }
    }
  }

  @Test
  fun refreshKeepsProjectLoadingUntilAllProvidersFinish() = runBlocking(Dispatchers.Default) {
    val codexStarted = CompletableDeferred<Unit>()
    val claudeStarted = CompletableDeferred<Unit>()
    val releaseClaude = CompletableDeferred<Unit>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            listFromOpenProject = { path, _ ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                codexStarted.complete(Unit)
                listOf(thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.from("codex")))
              }
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { path, _ ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                claudeStarted.complete(Unit)
                releaseClaude.await()
                listOf(thread(id = "claude-1", updatedAt = 100, provider = AgentSessionProvider.from("claude")))
              }
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      codexStarted.await()
      claudeStarted.await()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED &&
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.LOADING &&
        project.threads.map { it.id } == listOf("codex-1")
      }

      releaseClaude.complete(Unit)

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED &&
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.LOADED &&
        project.threads.map { it.id } == listOf("codex-1", "claude-1")
      }
    }
  }

  @Test
  fun refreshKeepsRuntimeVisibleThreadCountForKnownPath() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
          )
        )
      },
      projectEntriesProvider = {
        listOf(closedProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.any { it.path == PROJECT_PATH }
      }

      service.showMoreThreads(PROJECT_PATH)
      assertThat(service.state.value.visibleThreadCounts[PROJECT_PATH])
        .isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + DEFAULT_VISIBLE_THREAD_COUNT)

      service.refresh()
      waitForCondition {
        service.state.value.projects.any { it.path == PROJECT_PATH } &&
        service.state.value.visibleThreadCounts[PROJECT_PATH] == DEFAULT_VISIBLE_THREAD_COUNT + DEFAULT_VISIBLE_THREAD_COUNT
      }

      assertThat(service.state.value.visibleThreadCounts[PROJECT_PATH])
        .isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + DEFAULT_VISIBLE_THREAD_COUNT)
    }
  }

  @Test
  fun lifecycleCatalogSyncLoadsOnlyNewlyOpenedProjects() = runBlocking(Dispatchers.Default) {
    val secondProjectPath = "/work/project-b"
    var entries = listOf(openProjectEntry(PROJECT_PATH, "Project A"))
    val openLoadCounts = LinkedHashMap<String, AtomicInteger>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            listFromOpenProject = { path, _ ->
              openLoadCounts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
              listOf(
                thread(
                  id = "thread-${path.substringAfterLast('/')}",
                  updatedAt = 100,
                  provider = AgentSessionProvider.from("codex"),
                )
              )
            },
          )
        )
      },
      projectEntriesProvider = { entries },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.lastUpdatedAt != null
      }

      entries = listOf(
        openProjectEntry(PROJECT_PATH, "Project A"),
        openProjectEntry(secondProjectPath, "Project B"),
      )

      service.refreshCatalogAndLoadNewlyOpened()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == secondProjectPath } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED
      }

      assertThat(openLoadCounts[PROJECT_PATH]?.get()).isEqualTo(1)
      assertThat(openLoadCounts[secondProjectPath]?.get()).isEqualTo(1)
    }
  }

  @Test
  fun refreshCatalogClearsStandaloneProjectBranchWhenCatalogStopsProvidingIt() = runBlocking(Dispatchers.Default) {
    var entries = listOf(openProjectEntry(PROJECT_PATH, "Project A", branch = "feature-x"))

    withService(
      sessionSourcesProvider = { emptyList() },
      projectEntriesProvider = { entries },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.branch == "feature-x"
      }

      entries = listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      service.refreshCatalogAndLoadNewlyOpened()

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.branch == null
      }
    }
  }

  @Test
  fun lifecycleCatalogSyncMarksClosedProjectWithoutReloading() = runBlocking(Dispatchers.Default) {
    var entries = listOf(openProjectEntry(PROJECT_PATH, "Project A"))
    val openLoadCount = AtomicInteger(0)

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                openLoadCount.incrementAndGet()
              }
              listOf(thread(id = "claude-1", updatedAt = 100, provider = AgentSessionProvider.from("claude")))
            },
          )
        )
      },
      projectEntriesProvider = { entries },
    ) { service ->
      service.refresh()
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.LOADED
      }

      entries = listOf(closedProjectEntry(PROJECT_PATH, "Project A"))
      service.refreshCatalogAndLoadNewlyOpened()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        !project.isOpen
      }

      assertThat(openLoadCount.get()).isEqualTo(1)
    }
  }

  @Test
  fun refreshMergesMixedProviderThreadsAndMarksUnknownCount() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = false,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.from("codex")))
              else emptyList()
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.from("claude")))
              else emptyList()
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED &&
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.LOADED
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNull()
      assertThat(project.providerWarnings).isEmpty()
      assertThat(project.hasUnknownThreadCount).isTrue()
      assertThat(project.threads.map { it.id }).containsExactly("claude-1", "codex-1")
    }
  }

  @Test
  fun refreshShowsProviderWarningWhenOneProviderFails() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = false,
            listFromOpenProject = { _, _ -> throw IllegalStateException("codex failed") },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.from("claude")))
              else emptyList()
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.FAILED &&
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.LOADED
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNull()
      assertThat(project.providerWarnings).hasSize(1)
      assertThat(project.providerWarnings.single().provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(project.hasUnknownThreadCount).isFalse()
      assertThat(project.threads.map { it.id }).containsExactly("claude-1")
    }
  }

  @Test
  fun refreshShowsBlockingErrorWhenAllProvidersFail() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = false,
            listFromOpenProject = { _, _ -> throw IllegalStateException("codex failed") },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { _, _ -> throw IllegalStateException("claude failed") },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.FAILED &&
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.FAILED
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNotNull()
      assertThat(project.providerWarnings).isEmpty()
      assertThat(project.threads).isEmpty()
    }
  }

  @Test
  fun refreshDoesNotMarkUnknownCountWhenOnlyUnknownProviderFails() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = false,
            listFromOpenProject = { _, _ -> throw IllegalStateException("codex failed") },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            canReportExactThreadCount = true,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.from("claude")))
              else emptyList()
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.FAILED &&
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.LOADED
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNull()
      assertThat(project.hasUnknownThreadCount).isFalse()
      assertThat(project.threads.map { it.id }).containsExactly("claude-1")
    }
  }

  @Test
  fun refreshUsesLatestSessionSourcesFromProvider() = runBlocking(Dispatchers.Default) {
    var sessionSources = listOf(
      ScriptedSessionSource(
        provider = AgentSessionProvider.from("codex"),
        listFromOpenProject = { path, _ ->
          if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.from("codex")))
          else emptyList()
        },
      ),
    )

    withService(
      sessionSourcesProvider = { sessionSources },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { it.id } == listOf("codex-1")
      }

      sessionSources = listOf(
        ScriptedSessionSource(
          provider = AgentSessionProvider.from("claude"),
          listFromOpenProject = { path, _ ->
            if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.from("claude")))
            else emptyList()
          },
        )
      )

      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { it.id } == listOf("claude-1")
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.threads.map { it.id }).containsExactly("claude-1")
    }
  }

  @Test
  fun providerUpdateRefreshesOnlyMatchingProviderThreads() = runBlocking(Dispatchers.Default) {
    val codexUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 1)
    var codexUpdatedAt = 100L

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = false,
            supportsUpdates = true,
            updateEvents = codexUpdates,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                listOf(thread(id = "codex-1", updatedAt = codexUpdatedAt, provider = AgentSessionProvider.from("codex")))
              }
              else {
                emptyList()
              }
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                listOf(thread(id = "codex-1", updatedAt = codexUpdatedAt, provider = AgentSessionProvider.from("codex")))
              }
              else {
                emptyList()
              }
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.from("claude")))
              else emptyList()
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.from("claude")))
              else emptyList()
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED &&
        project.providerLoadStates[AgentSessionProvider.from("claude")] == AgentSessionProviderLoadState.LOADED
      }

      codexUpdatedAt = 300L
      codexUpdates.emit(threadsChangedEvent())

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        val codexThread = project.threads.firstOrNull { it.provider == AgentSessionProvider.from("codex") } ?: return@waitForCondition false
        codexThread.updatedAt == 300L
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.threads.map { it.id }).containsExactly("claude-1", "codex-1")
      assertThat(project.threads.first { it.provider == AgentSessionProvider.from("claude") }.updatedAt).isEqualTo(200L)
      assertThat(project.threads.first { it.provider == AgentSessionProvider.from("codex") }.updatedAt).isEqualTo(300L)
    }
  }

  @Test
  fun providerUpdateObservedAfterSourceAppearsAfterRefresh() = runBlocking(Dispatchers.Default) {
    val codexUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    var codexUpdatedAt = 100L
    var sessionSources: List<AgentSessionSource> = emptyList()

    val codexSource = ScriptedSessionSource(
      provider = AgentSessionProvider.from("codex"),
      canReportExactThreadCount = false,
      supportsUpdates = true,
      updateEvents = codexUpdates,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listOf(thread(id = "codex-1", updatedAt = codexUpdatedAt, provider = AgentSessionProvider.from("codex")))
        }
        else {
          emptyList()
        }
      },
      listFromClosedProject = { path ->
        if (path == PROJECT_PATH) {
          listOf(thread(id = "codex-1", updatedAt = codexUpdatedAt, provider = AgentSessionProvider.from("codex")))
        }
        else {
          emptyList()
        }
      },
    )

    withService(
      sessionSourcesProvider = { sessionSources },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()

      sessionSources = listOf(codexSource)
      service.refresh()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.threads.firstOrNull { it.provider == AgentSessionProvider.from("codex") }?.updatedAt == 100L
      }

      codexUpdatedAt = 300L
      codexUpdates.emit(threadsChangedEvent())

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.threads.firstOrNull { it.provider == AgentSessionProvider.from("codex") }?.updatedAt == 300L
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.threads.map { it.id }).containsExactly("codex-1")
      assertThat(project.threads.first().updatedAt).isEqualTo(300L)
    }
  }

  @Test
  fun providerUpdateBuildsPendingTabRebindTargetsForCodex() = runBlocking(Dispatchers.Default) {
    val codexUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    var codexThreads = listOf(
      thread(id = "codex-1", updatedAt = 100L, title = "Existing Codex thread", provider = AgentSessionProvider.from("codex"))
    )
    val rebindInvocations = mutableListOf<ServicePendingCodexRebindInvocation>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            supportsUpdates = true,
            updateEvents = codexUpdates,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) codexThreads else emptyList()
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) codexThreads else emptyList()
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            AgentChatPendingTabSnapshot(
              projectPath = PROJECT_PATH,
              pendingTabKey = "pending-codex:new-1",
              pendingThreadIdentity = "codex:new-1",
              pendingCreatedAtMs = 200L,
              pendingFirstInputAtMs = null,
              pendingLaunchMode = null,
            )
          )
        )
      },
      openConcreteChatThreadIdentitiesByPathProvider = { emptyMap() },
      openAgentChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              ServicePendingCodexRebindInvocation(
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
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.map { it.id } == listOf("codex-1")
      }

      codexThreads = listOf(
        thread(id = "codex-2", updatedAt = 300L, title = "New Codex thread", provider = AgentSessionProvider.from("codex"))
      )
      codexUpdates.emit(threadsChangedEvent())

      waitForCondition {
        rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-1")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-1")
      val target = invocation.target
      assertThat(target.projectPath).isEqualTo(PROJECT_PATH)
      assertThat(target.provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.from("codex"), "codex-2"))
      assertThat(target.threadId).isEqualTo("codex-2")
      assertThat(target.threadTitle).isEqualTo("New Codex thread")
      assertThat(target.threadActivity).isEqualTo(AgentThreadActivity.READY)

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.map { it.id } == listOf("codex-2")
      }
      assertThat(
        service.state.value.projects.single { it.path == PROJECT_PATH }
          .threads
          .map { it.id }
      ).containsExactly("codex-2")
    }
  }

  @Test
  fun providerUpdateDoesNotPassPendingNewThreadIdsToRefreshHints() = runBlocking(Dispatchers.Default) {
    val codexUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val capturedRefreshThreadSeedsByPath = mutableListOf<Map<String, Set<AgentSessionRefreshThreadSeed>>>()

    val listedThread = thread(
      id = "codex-listed",
      updatedAt = 320L,
      title = "Listed Codex thread",
      provider = AgentSessionProvider.from("codex"),
    )

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            supportsUpdates = true,
            updateEvents = codexUpdates,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(listedThread) else emptyList()
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) listOf(listedThread) else emptyList()
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
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            AgentChatPendingTabSnapshot(
              projectPath = PROJECT_PATH,
              pendingTabKey = "pending-codex:new-ea4cecdd-f115-410d-9c73-f652c21558a9",
              pendingThreadIdentity = "codex:new-ea4cecdd-f115-410d-9c73-f652c21558a9",
              pendingCreatedAtMs = 200L,
              pendingFirstInputAtMs = null,
              pendingLaunchMode = null,
            )
          )
        )
      },
      openConcreteChatThreadIdentitiesByPathProvider = {
        mapOf(
          PROJECT_PATH to setOf(
            "codex:new-ea4cecdd-f115-410d-9c73-f652c21558a9",
            "codex:codex-open",
          )
        )
      },
    ) { service ->
      service.refresh()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.threads.map { it.id } == listOf("codex-listed")
      }

      capturedRefreshThreadSeedsByPath.clear()

      codexUpdates.emit(threadsChangedEvent())

      waitForCondition {
        capturedRefreshThreadSeedsByPath.isNotEmpty()
      }
      assertThat(service.state.value.projects.single { it.path == PROJECT_PATH }.threads.map { it.id })
        .containsExactly("codex-listed")

      codexUpdates.emit(threadsChangedEvent())

      waitForCondition {
        capturedRefreshThreadSeedsByPath.size >= 2
      }

      assertThat(capturedRefreshThreadSeedsByPath.last()[PROJECT_PATH])
        .containsExactlyInAnyOrder(
          AgentSessionRefreshThreadSeed(threadId = "codex-listed", updatedAt = 320L, forceRefresh = false),
          AgentSessionRefreshThreadSeed(
            threadId = "codex-open",
            updatedAt = UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT,
            forceRefresh = false,
          ),
        )
    }
  }

  @Test
  fun markThreadAsReadUpdatesRuntimeAndWarmSnapshotImmediately() = runBlocking(Dispatchers.Default) {
    val warmState = InMemorySessionWarmState()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                listOf(
                  thread(
                    id = "claude-1",
                    updatedAt = 100,
                    provider = AgentSessionProvider.from("claude"),
                    activity = AgentThreadActivity.UNREAD,
                  )
                )
              }
              else {
                emptyList()
              }
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      warmState = warmState,
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull()
          ?.activity == AgentThreadActivity.UNREAD
      }

      service.markThreadAsRead(PROJECT_PATH, AgentSessionProvider.from("claude"), "claude-1", 100)

      assertThat(
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull()
          ?.activity
      ).isEqualTo(AgentThreadActivity.READY)
      assertThat(warmState.getPathSnapshot(PROJECT_PATH)?.threads?.firstOrNull()?.activity)
        .isEqualTo(AgentThreadActivity.READY)
      val presentationKey = checkNotNull(
        AgentSessionThreadPresentationKey.create(PROJECT_PATH, AgentSessionProvider.from("claude"), "claude-1")
      )
      assertThat(service<AgentSessionThreadPresentationModel>().resolve(presentationKey)?.activity)
        .isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun markThreadAsReadClearsUnreadChromeActivityWithoutChangingNonUnreadRowActivity() = runBlocking(Dispatchers.Default) {
    val warmState = InMemorySessionWarmState()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                listOf(
                  thread(
                    id = "claude-1",
                    updatedAt = 100,
                    provider = AgentSessionProvider.from("claude"),
                    activity = AgentThreadActivity.PROCESSING,
                    summaryActivity = AgentThreadActivity.UNREAD,
                  )
                )
              }
              else {
                emptyList()
              }
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      warmState = warmState,
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull()
          ?.summaryActivity == AgentThreadActivity.UNREAD
      }

      service.markThreadAsRead(PROJECT_PATH, AgentSessionProvider.from("claude"), "claude-1", 100)

      val runtimeThread = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
        ?.threads
        ?.firstOrNull()
      assertThat(runtimeThread?.activity).isEqualTo(AgentThreadActivity.PROCESSING)
      assertThat(runtimeThread?.summaryActivity).isEqualTo(AgentThreadActivity.READY)

      val warmThread = warmState.getPathSnapshot(PROJECT_PATH)?.threads?.firstOrNull()
      assertThat(warmThread?.activity).isEqualTo(AgentThreadActivity.PROCESSING)
      assertThat(warmThread?.summaryActivity).isEqualTo(AgentThreadActivity.READY)

      val presentationKey = checkNotNull(AgentSessionThreadPresentationKey.create(PROJECT_PATH, AgentSessionProvider.from("claude"), "claude-1"))
      assertThat(service<AgentSessionThreadPresentationModel>().resolve(presentationKey)?.activityReport)
        .isEqualTo(
          AgentThreadActivityReport(
            rowActivity = AgentThreadActivity.PROCESSING,
            chromeActivity = AgentThreadActivity.READY,
          )
        )
    }
  }

  @Test
  fun blockingRefreshFailureKeepsPreviousWarmSnapshot() = runBlocking(Dispatchers.Default) {
    val warmState = InMemorySessionWarmState()
    warmState.setPathSnapshot(
      PROJECT_PATH,
      AgentSessionWarmPathSnapshot(
        threads = listOf(thread(id = "cached-1", updatedAt = 100, provider = AgentSessionProvider.from("claude"))),
        providerLoadStates = loadedProviderStates(AgentSessionProvider.from("claude")),
        updatedAt = 100,
      ),
    )

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("claude"),
            listFromOpenProject = { _, _ -> throw IllegalStateException("boom") },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      warmState = warmState,
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.errorMessage != null
      }

      assertThat(warmState.getPathSnapshot(PROJECT_PATH)?.threads.orEmpty().map { it.id })
        .containsExactly("cached-1")
    }
  }

  @Test
  fun rebindPendingTabsInBackgroundRefreshesTreeToDropPendingProjection() = runBlocking(Dispatchers.Default) {
    val codexUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(replay = 1, extraBufferCapacity = 1)
    val pendingIdentity = "codex:new-rebind-tree"
    val refreshCount = AtomicInteger(0)
    val pendingTabsRef = AtomicReference(
      mapOf(
        PROJECT_PATH to listOf(
          AgentChatPendingTabSnapshot(
            projectPath = PROJECT_PATH,
            pendingTabKey = "pending-codex:rebind-tree",
            pendingThreadIdentity = pendingIdentity,
            pendingCreatedAtMs = 200L,
            pendingFirstInputAtMs = null,
            pendingLaunchMode = null,
          )
        )
      )
    )
    val rebindInvocations = mutableListOf<ServicePendingCodexRebindInvocation>()
    val concreteTarget = AgentChatTabRebindTarget(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.from("codex"),
      threadIdentity = buildAgentSessionIdentity(AgentSessionProvider.from("codex"), "codex-1"),
      threadId = "codex-1",
      threadTitle = "Codex thread",
      threadActivity = AgentThreadActivity.READY,
      threadUpdatedAt = 300L,
    )

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            supportsUpdates = true,
            updateEvents = codexUpdates,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                listOf(thread(id = "codex-1", updatedAt = 300L, title = "Codex thread", provider = AgentSessionProvider.from("codex")))
              }
              else {
                emptyList()
              }
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                listOf(thread(id = "codex-1", updatedAt = 300L, title = "Codex thread", provider = AgentSessionProvider.from("codex")))
              }
              else {
                emptyList()
              }
            },
            refreshThreadsProvider = { request ->
              refreshCount.incrementAndGet()
              AgentSessionSourceRefreshResult(
                completeThreadsByPath = request.paths.associateWith {
                  listOf(thread(id = "codex-1", updatedAt = 300L, title = "Codex thread", provider = AgentSessionProvider.from("codex")))
                },
              )
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      openPendingCodexTabsProvider = { pendingTabsRef.get() },
      openConcreteChatThreadIdentitiesByPathProvider = { emptyMap() },
      openAgentChatPendingTabsBinder = { requestsByPath ->
        requestsByPath.forEach { (path, requests) ->
          requests.forEach { request ->
            rebindInvocations.add(
              ServicePendingCodexRebindInvocation(
                path = path,
                pendingTabKey = request.pendingTabKey,
                pendingThreadIdentity = request.pendingThreadIdentity,
                target = request.target,
              )
            )
          }
        }
        // Real binder drops the rebound pending tab from the AgentChatTabsService snapshot.
        pendingTabsRef.set(emptyMap())
        successfulPendingCodexRebindReport(requestsByPath)
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED
      }

      codexUpdates.emit(threadsChangedEvent())
      waitForCondition { refreshCount.get() == 1 }
      assertThat(service.state.value.projects.single { it.path == PROJECT_PATH }.threads.map { it.id })
        .containsExactly("codex-1")

      val refreshCountBeforeRebind = refreshCount.get()
      service.rebindPendingTabsInBackground(
        provider = AgentSessionProvider.from("codex"),
        requestsByProjectPath = mapOf(
          PROJECT_PATH to listOf(
            AgentChatPendingTabRebindRequest(
              pendingTabKey = "pending-codex:rebind-tree",
              pendingThreadIdentity = pendingIdentity,
              target = concreteTarget,
            )
          )
        ),
      )

      waitForCondition { rebindInvocations.isNotEmpty() }
      waitForCondition { refreshCount.get() > refreshCountBeforeRebind }
      assertThat(service.state.value.projects.single { it.path == PROJECT_PATH }.threads.map { it.id })
        .containsExactly("codex-1")

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingThreadIdentity).isEqualTo(pendingIdentity)
    }
  }
}

private data class ServicePendingCodexRebindInvocation(
  @JvmField val path: String,
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val target: AgentChatTabRebindTarget,
)

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
