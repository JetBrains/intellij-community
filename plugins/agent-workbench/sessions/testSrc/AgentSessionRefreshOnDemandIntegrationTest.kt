// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.WorktreeEntry
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_THREAD_COUNT
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionRefreshOnDemandIntegrationTest {
  @Test
  fun loadProjectThreadsOnDemandCompletesBeforeLoadingDelayWithoutPublishingLoading() = runBlocking(Dispatchers.Default) {
    val testScope = this
    val provider = AgentSessionProvider.from("codex")

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = provider,
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 100, provider = provider)) else emptyList()
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(closedProjectEntry(PROJECT_PATH, "Project A"))
      },
      loadingDelayMs = 300L,
    ) { service ->
      service.refresh()
      waitForCondition { service.state.value.projects.any { project -> project.path == PROJECT_PATH } }

      val loadingObserved = AtomicBoolean(false)
      val collector = testScope.launch {
        service.state.collect { state ->
          if (state.projects.firstOrNull { project -> project.path == PROJECT_PATH }?.isLoading == true) {
            loadingObserved.set(true)
          }
        }
      }
      try {
        service.loadProjectThreadsOnDemand(PROJECT_PATH)
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
  fun loadProjectThreadsOnDemandPublishesLoadingAfterDelay() = runBlocking(Dispatchers.Default) {
    val provider = AgentSessionProvider.from("codex")
    val loadStarted = CompletableDeferred<Unit>()
    val releaseLoad = CompletableDeferred<Unit>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = provider,
            listFromClosedProject = { path ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                loadStarted.complete(Unit)
                releaseLoad.await()
                listOf(thread(id = "codex-1", updatedAt = 100, provider = provider))
              }
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(closedProjectEntry(PROJECT_PATH, "Project A"))
      },
      loadingDelayMs = 300L,
    ) { service ->
      service.refresh()
      waitForCondition { service.state.value.projects.any { project -> project.path == PROJECT_PATH } }

      service.loadProjectThreadsOnDemand(PROJECT_PATH)
      loadStarted.await()

      waitForCondition(timeoutMs = 6_000) {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.providerLoadStates
          ?.get(provider) == AgentSessionProviderLoadState.LOADING
      }

      releaseLoad.complete(Unit)
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.providerLoadStates
          ?.get(provider) == AgentSessionProviderLoadState.LOADED
      }
    }
  }

  @Test
  fun ensureThreadVisibleExpandsProjectVisibleCountForHiddenThread() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = true,
            listFromClosedProject = { path ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                listOf(
                  thread(id = "codex-1", updatedAt = 500, provider = AgentSessionProvider.from("codex")),
                  thread(id = "codex-2", updatedAt = 400, provider = AgentSessionProvider.from("codex")),
                  thread(id = "codex-3", updatedAt = 300, provider = AgentSessionProvider.from("codex")),
                  thread(id = "codex-4", updatedAt = 200, provider = AgentSessionProvider.from("codex")),
                  thread(id = "codex-5", updatedAt = 100, provider = AgentSessionProvider.from("codex")),
                )
              }
            },
          ),
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

      service.loadProjectThreadsOnDemand(PROJECT_PATH)
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED
      }

      service.ensureThreadVisible(PROJECT_PATH, AgentSessionProvider.from("codex"), "codex-5")

      assertThat(service.state.value.visibleThreadCounts[PROJECT_PATH])
        .isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + DEFAULT_VISIBLE_THREAD_COUNT)
    }
  }

  @Test
  fun ensureThreadVisibleExpandsProjectVisibleCountForHiddenSubAgent() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = true,
            listFromClosedProject = { path ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                listOf(
                  thread(id = "codex-1", updatedAt = 500, provider = AgentSessionProvider.from("codex")),
                  thread(id = "codex-2", updatedAt = 400, provider = AgentSessionProvider.from("codex")),
                  thread(id = "codex-3", updatedAt = 300, provider = AgentSessionProvider.from("codex")),
                  thread(id = "codex-4", updatedAt = 200, provider = AgentSessionProvider.from("codex")),
                  thread(
                    id = "codex-parent",
                    updatedAt = 100,
                    provider = AgentSessionProvider.from("codex"),
                    subAgents = listOf(AgentSubAgent(id = "codex-sub-1", name = "Sub-agent 1")),
                  ),
                )
              }
            },
          ),
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

      service.loadProjectThreadsOnDemand(PROJECT_PATH)
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED
      }

      service.ensureThreadVisible(PROJECT_PATH, AgentSessionProvider.from("codex"), "codex-sub-1")

      assertThat(service.state.value.visibleThreadCounts[PROJECT_PATH])
        .isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + DEFAULT_VISIBLE_THREAD_COUNT)
    }
  }

  @Test
  fun showMoreThreadsUpdatesRuntimeVisibleCount() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = true,
          ),
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
    }
  }

  @Test
  fun ensureProjectVisibleExpandsClosedProjectVisibleCount() = runBlocking(Dispatchers.Default) {
    val hiddenProjectPath = "/work/project-hidden"
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = true,
          ),
        )
      },
      projectEntriesProvider = {
        listOf(
          closedProjectEntry("/work/project-a", "Project A"),
          closedProjectEntry("/work/project-b", "Project B"),
          closedProjectEntry("/work/project-c", "Project C"),
          closedProjectEntry(hiddenProjectPath, "Project Hidden"),
        )
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.size == 4
      }

      service.ensureProjectVisible(hiddenProjectPath)

      assertThat(service.state.value.visibleClosedProjectCount).isEqualTo(4)
    }
  }

  @Test
  fun ensureThreadVisibleDoesNotChangeVisibleCountForAlreadyVisibleThread() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = true,
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                listOf(
                  thread(id = "codex-1", updatedAt = 300, provider = AgentSessionProvider.from("codex")),
                  thread(id = "codex-2", updatedAt = 200, provider = AgentSessionProvider.from("codex")),
                  thread(id = "codex-3", updatedAt = 100, provider = AgentSessionProvider.from("codex")),
                )
              }
              else {
                emptyList()
              }
            },
          ),
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

      service.loadProjectThreadsOnDemand(PROJECT_PATH)
      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED
      }

      service.ensureThreadVisible(PROJECT_PATH, AgentSessionProvider.from("codex"), "codex-1")

      assertThat(service.state.value.visibleThreadCounts).doesNotContainKey(PROJECT_PATH)
    }
  }

  @Test
  fun loadProjectThreadsOnDemandDeduplicatesConcurrentRequests() = runBlocking(Dispatchers.Default) {
    val invocationCount = AtomicInteger(0)
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = false,
            listFromClosedProject = { path ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                invocationCount.incrementAndGet()
                started.complete(Unit)
                release.await()
                listOf(thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.from("codex")))
              }
            },
          ),
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

      service.loadProjectThreadsOnDemand(PROJECT_PATH)
      started.await()
      service.loadProjectThreadsOnDemand(PROJECT_PATH)
      release.complete(Unit)

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(invocationCount.get()).isEqualTo(1)
      assertThat(project.threads.map { it.id }).containsExactly("codex-1")
    }
  }

  @Test
  fun loadWorktreeThreadsOnDemandDeduplicatesConcurrentRequestsWhileRefreshLoadsWorktree() = runBlocking(Dispatchers.Default) {
    val invocationCount = AtomicInteger(0)
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.from("codex"),
            canReportExactThreadCount = false,
            listFromClosedProject = { path ->
              if (path != WORKTREE_PATH) {
                emptyList()
              }
              else {
                invocationCount.incrementAndGet()
                started.complete(Unit)
                release.await()
                listOf(thread(id = "wt-codex-1", updatedAt = 100, provider = AgentSessionProvider.from("codex")))
              }
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(
          closedProjectEntry(
            PROJECT_PATH,
            "Project A",
            worktrees = listOf(
              WorktreeEntry(
                path = WORKTREE_PATH,
                name = "project-feature",
                branch = "feature",
                project = null,
              )
            ),
          )
        )
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.worktrees?.isNotEmpty() == true
      }

      service.loadWorktreeThreadsOnDemand(PROJECT_PATH, WORKTREE_PATH)
      started.await()
      service.loadWorktreeThreadsOnDemand(PROJECT_PATH, WORKTREE_PATH)
      release.complete(Unit)

      waitForCondition {
        val worktree = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.worktrees
          ?.firstOrNull { it.path == WORKTREE_PATH }
          ?: return@waitForCondition false
        worktree.providerLoadStates[AgentSessionProvider.from("codex")] == AgentSessionProviderLoadState.LOADED
      }

      val worktree = service.state.value.projects
        .single { it.path == PROJECT_PATH }
        .worktrees
        .single { it.path == WORKTREE_PATH }
      // refresh() skips closed worktrees; only the first on-demand request loads, the second is deduplicated.
      assertThat(invocationCount.get()).isEqualTo(1)
      assertThat(worktree.threads.map { it.id }).containsExactly("wt-codex-1")
    }
  }
}
