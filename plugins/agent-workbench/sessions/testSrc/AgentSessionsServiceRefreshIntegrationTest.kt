// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentSessionsServiceRefreshIntegrationTest {
  @Test
  fun refreshShowsCachedOpenProjectThreadsBeforeProviderLoadCompletes() = runBlocking {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val treeUiState = InMemorySessionsTreeUiState()
    treeUiState.setOpenProjectThreadPreviews(
      PROJECT_PATH,
      listOf(
        AgentSessionThreadPreview(
          id = "cached-1",
          title = "Cached",
          updatedAt = 100,
          provider = AgentSessionProvider.CLAUDE,
        )
      ),
    )

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                started.complete(Unit)
                release.await()
                listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
              }
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      treeUiState = treeUiState,
    ) { service ->
      service.refresh()
      started.await()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.hasLoaded && project.threads.map { it.id } == listOf("cached-1")
      }

      release.complete(Unit)
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { it.id } == listOf("claude-1")
      }

      assertThat(treeUiState.getOpenProjectThreadPreviews(PROJECT_PATH).orEmpty().map { it.id })
        .containsExactly("claude-1")
    }
  }

  @Test
  fun refreshIgnoresPersistedVisibleThreadCountForKnownPath() = runBlocking {
    val treeUiState = InMemorySessionsTreeUiState()
    treeUiState.incrementVisibleThreadCount(PROJECT_PATH, delta = 6)

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
          )
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

      assertThat(service.state.value.visibleThreadCounts)
        .doesNotContainKey(PROJECT_PATH)
    }
  }

  @Test
  fun lifecycleCatalogSyncLoadsOnlyNewlyOpenedProjects() = runBlocking {
    val secondProjectPath = "/work/project-b"
    var entries = listOf(openProjectEntry(PROJECT_PATH, "Project A"))
    val openLoadCounts = LinkedHashMap<String, AtomicInteger>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            listFromOpenProject = { path, _ ->
              openLoadCounts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
              listOf(
                thread(
                  id = "thread-${path.substringAfterLast('/')}",
                  updatedAt = 100,
                  provider = AgentSessionProvider.CODEX,
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
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      entries = listOf(
        openProjectEntry(PROJECT_PATH, "Project A"),
        openProjectEntry(secondProjectPath, "Project B"),
      )

      service.refreshCatalogAndLoadNewlyOpened()

      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == secondProjectPath }?.hasLoaded == true
      }

      assertThat(openLoadCounts[PROJECT_PATH]?.get()).isEqualTo(1)
      assertThat(openLoadCounts[secondProjectPath]?.get()).isEqualTo(1)
    }
  }

  @Test
  fun lifecycleCatalogSyncMarksClosedProjectWithoutReloading() = runBlocking {
    var entries = listOf(openProjectEntry(PROJECT_PATH, "Project A"))
    val openLoadCount = AtomicInteger(0)

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                openLoadCount.incrementAndGet()
              }
              listOf(thread(id = "claude-1", updatedAt = 100, provider = AgentSessionProvider.CLAUDE))
            },
          )
        )
      },
      projectEntriesProvider = { entries },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
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
  fun refreshMergesMixedProviderThreadsAndMarksUnknownCount() = runBlocking {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = false,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.CODEX))
              else emptyList()
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
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
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNull()
      assertThat(project.providerWarnings).isEmpty()
      assertThat(project.hasUnknownThreadCount).isTrue()
      assertThat(project.threads.map { it.id }).containsExactly("claude-1", "codex-1")
    }
  }

  @Test
  fun refreshShowsProviderWarningWhenOneProviderFails() = runBlocking {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = false,
            listFromOpenProject = { _, _ -> throw IllegalStateException("codex failed") },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
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
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNull()
      assertThat(project.providerWarnings).hasSize(1)
      assertThat(project.providerWarnings.single().provider).isEqualTo(AgentSessionProvider.CODEX)
      assertThat(project.hasUnknownThreadCount).isFalse()
      assertThat(project.threads.map { it.id }).containsExactly("claude-1")
    }
  }

  @Test
  fun refreshShowsBlockingErrorWhenAllProvidersFail() = runBlocking {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = false,
            listFromOpenProject = { _, _ -> throw IllegalStateException("codex failed") },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
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
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNotNull()
      assertThat(project.providerWarnings).isEmpty()
      assertThat(project.threads).isEmpty()
    }
  }

  @Test
  fun refreshDoesNotMarkUnknownCountWhenOnlyUnknownProviderFails() = runBlocking {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = false,
            listFromOpenProject = { _, _ -> throw IllegalStateException("codex failed") },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            canReportExactThreadCount = true,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
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
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNull()
      assertThat(project.hasUnknownThreadCount).isFalse()
      assertThat(project.threads.map { it.id }).containsExactly("claude-1")
    }
  }

  @Test
  fun refreshUsesLatestSessionSourcesFromProvider() = runBlocking {
    var sessionSources = listOf(
      ScriptedSessionSource(
        provider = AgentSessionProvider.CODEX,
        listFromOpenProject = { path, _ ->
          if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.CODEX))
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
          provider = AgentSessionProvider.CLAUDE,
          listFromOpenProject = { path, _ ->
            if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
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
  fun providerUpdateRefreshesOnlyMatchingProviderThreads() = runBlocking {
    val codexUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var codexUpdatedAt = 100L

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            canReportExactThreadCount = false,
            supportsUpdates = true,
            updates = codexUpdates,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                listOf(thread(id = "codex-1", updatedAt = codexUpdatedAt, provider = AgentSessionProvider.CODEX))
              }
              else {
                emptyList()
              }
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                listOf(thread(id = "codex-1", updatedAt = codexUpdatedAt, provider = AgentSessionProvider.CODEX))
              }
              else {
                emptyList()
              }
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
              else emptyList()
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
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
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      codexUpdatedAt = 300L
      codexUpdates.emit(Unit)

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        val codexThread = project.threads.firstOrNull { it.provider == AgentSessionProvider.CODEX } ?: return@waitForCondition false
        codexThread.updatedAt == 300L
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.threads.map { it.id }).containsExactly("codex-1", "claude-1")
      assertThat(project.threads.first { it.provider == AgentSessionProvider.CLAUDE }.updatedAt).isEqualTo(200L)
      assertThat(project.threads.first { it.provider == AgentSessionProvider.CODEX }.updatedAt).isEqualTo(300L)
    }
  }

  @Test
  fun providerUpdateObservedAfterSourceAppearsAfterRefresh() = runBlocking {
    val codexUpdates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    var codexUpdatedAt = 100L
    var sessionSources: List<AgentSessionSource> = emptyList()

    val codexSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      canReportExactThreadCount = false,
      supportsUpdates = true,
      updates = codexUpdates,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listOf(thread(id = "codex-1", updatedAt = codexUpdatedAt, provider = AgentSessionProvider.CODEX))
        }
        else {
          emptyList()
        }
      },
      listFromClosedProject = { path ->
        if (path == PROJECT_PATH) {
          listOf(thread(id = "codex-1", updatedAt = codexUpdatedAt, provider = AgentSessionProvider.CODEX))
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
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      sessionSources = listOf(codexSource)
      service.refresh()

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.threads.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt == 100L
      }

      codexUpdatedAt = 300L
      codexUpdates.emit(Unit)

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.threads.firstOrNull { it.provider == AgentSessionProvider.CODEX }?.updatedAt == 300L
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.threads.map { it.id }).containsExactly("codex-1")
      assertThat(project.threads.first().updatedAt).isEqualTo(300L)
    }
  }
}
