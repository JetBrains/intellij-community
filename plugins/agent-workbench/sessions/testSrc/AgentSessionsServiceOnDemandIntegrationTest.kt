// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentSessionsServiceOnDemandIntegrationTest {
  @Test
  fun ensureThreadVisibleExpandsProjectVisibleCountForHiddenThread() = runBlocking(Dispatchers.Default) {
    val treeUiState = InMemorySessionsTreeUiState()
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = true,
            listFromClosedProject = { path ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                listOf(
                  thread(id = "codex-1", updatedAt = 500, provider = AgentSessionProvider.CODEX),
                  thread(id = "codex-2", updatedAt = 400, provider = AgentSessionProvider.CODEX),
                  thread(id = "codex-3", updatedAt = 300, provider = AgentSessionProvider.CODEX),
                  thread(id = "codex-4", updatedAt = 200, provider = AgentSessionProvider.CODEX),
                  thread(id = "codex-5", updatedAt = 100, provider = AgentSessionProvider.CODEX),
                )
              }
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(closedProjectEntry(PROJECT_PATH, "Project A"))
      },
      treeUiState = treeUiState,
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.any { it.path == PROJECT_PATH }
      }

      service.loadProjectThreadsOnDemand(PROJECT_PATH)
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      service.ensureThreadVisible(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-5")

      assertThat(service.state.value.visibleThreadCounts[PROJECT_PATH])
        .isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + DEFAULT_VISIBLE_THREAD_COUNT)
      assertThat(treeUiState.getVisibleThreadCount(PROJECT_PATH))
        .isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT)
    }
  }

  @Test
  fun showMoreThreadsUpdatesRuntimeVisibleCountWithoutPersistingUiState() = runBlocking(Dispatchers.Default) {
    val treeUiState = InMemorySessionsTreeUiState()
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = true,
          ),
        )
      },
      projectEntriesProvider = {
        listOf(closedProjectEntry(PROJECT_PATH, "Project A"))
      },
      treeUiState = treeUiState,
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.any { it.path == PROJECT_PATH }
      }

      service.showMoreThreads(PROJECT_PATH)

      assertThat(service.state.value.visibleThreadCounts[PROJECT_PATH])
        .isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + DEFAULT_VISIBLE_THREAD_COUNT)
      assertThat(treeUiState.getVisibleThreadCount(PROJECT_PATH))
        .isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT)
    }
  }

  @Test
  fun ensureThreadVisibleDoesNotChangeVisibleCountForAlreadyVisibleThread() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = true,
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                listOf(
                  thread(id = "codex-1", updatedAt = 300, provider = AgentSessionProvider.CODEX),
                  thread(id = "codex-2", updatedAt = 200, provider = AgentSessionProvider.CODEX),
                  thread(id = "codex-3", updatedAt = 100, provider = AgentSessionProvider.CODEX),
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
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      service.ensureThreadVisible(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1")

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
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = false,
            listFromClosedProject = { path ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                invocationCount.incrementAndGet()
                started.complete(Unit)
                release.await()
                listOf(thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.CODEX))
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
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
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
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = false,
            listFromClosedProject = { path ->
              if (path != WORKTREE_PATH) {
                emptyList()
              }
              else {
                invocationCount.incrementAndGet()
                started.complete(Unit)
                release.await()
                listOf(thread(id = "wt-codex-1", updatedAt = 100, provider = AgentSessionProvider.CODEX))
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
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.worktrees
          ?.firstOrNull { it.path == WORKTREE_PATH }
          ?.hasLoaded == true
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
