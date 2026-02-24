// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindOutcome
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmPathSnapshot
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_THREAD_COUNT
import com.intellij.agent.workbench.sessions.state.InMemorySessionWarmState
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentSessionRefreshServiceIntegrationTest {
  @Test
  fun refreshShowsWarmSnapshotThreadsBeforeProviderLoadCompletes() = runBlocking(Dispatchers.Default) {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val warmState = InMemorySessionWarmState()
    warmState.setPathSnapshot(
      PROJECT_PATH,
      AgentSessionWarmPathSnapshot(
        threads = listOf(
          thread(id = "cached-1", updatedAt = 100, title = "Cached", provider = AgentSessionProvider.CLAUDE)
        ),
        hasUnknownThreadCount = false,
        updatedAt = 100,
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
      warmState = warmState,
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

      assertThat(warmState.getPathSnapshot(PROJECT_PATH)?.threads.orEmpty().map { it.id })
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
            listFromOpenProject = { path, _ ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                codexStarted.complete(Unit)
                listOf(thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX))
              }
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path != PROJECT_PATH) {
                emptyList()
              }
              else {
                claudeStarted.complete(Unit)
                releaseClaude.await()
                listOf(thread(id = "claude-1", updatedAt = 100, provider = AgentSessionProvider.CLAUDE))
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
        project.isLoading && project.threads.map { it.id } == listOf("codex-1")
      }

      releaseClaude.complete(Unit)

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        !project.isLoading && project.threads.map { it.id } == listOf("codex-1", "claude-1")
      }
    }
  }

  @Test
  fun refreshKeepsRuntimeVisibleThreadCountForKnownPath() = runBlocking(Dispatchers.Default) {
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
  fun lifecycleCatalogSyncLoadsOnlyNewlyOpenedProjects() = runBlocking(Dispatchers.Default) {
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
  fun refreshMergesMixedProviderThreadsAndMarksUnknownCount() = runBlocking(Dispatchers.Default) {
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
  fun refreshShowsProviderWarningWhenOneProviderFails() = runBlocking(Dispatchers.Default) {
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
  fun refreshShowsBlockingErrorWhenAllProvidersFail() = runBlocking(Dispatchers.Default) {
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
  fun refreshDoesNotMarkUnknownCountWhenOnlyUnknownProviderFails() = runBlocking(Dispatchers.Default) {
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
  fun refreshUsesLatestSessionSourcesFromProvider() = runBlocking(Dispatchers.Default) {
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
  fun providerUpdateRefreshesOnlyMatchingProviderThreads() = runBlocking(Dispatchers.Default) {
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
  fun providerUpdateObservedAfterSourceAppearsAfterRefresh() = runBlocking(Dispatchers.Default) {
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

  @Test
  fun providerUpdateBuildsPendingTabRebindTargetsForCodex() = runBlocking(Dispatchers.Default) {
    val codexUpdates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    var codexThreads = listOf(
      thread(id = "codex-1", updatedAt = 100L, title = "Existing Codex thread", provider = AgentSessionProvider.CODEX)
    )
    val rebindInvocations = mutableListOf<ServicePendingCodexRebindInvocation>()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            supportsUpdates = true,
            updates = codexUpdates,
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
            AgentChatPendingCodexTabSnapshot(
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
        thread(id = "codex-2", updatedAt = 300L, title = "New Codex thread", provider = AgentSessionProvider.CODEX)
      )
      codexUpdates.emit(Unit)

      waitForCondition {
        rebindInvocations.isNotEmpty()
      }

      val invocation = rebindInvocations.single()
      assertThat(invocation.path).isEqualTo(PROJECT_PATH)
      assertThat(invocation.pendingTabKey).isEqualTo("pending-codex:new-1")
      assertThat(invocation.pendingThreadIdentity).isEqualTo("codex:new-1")
      val target = invocation.target
      assertThat(target.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-2"))
      assertThat(target.threadId).isEqualTo("codex-2")
      assertThat(target.shellCommand)
        .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "codex-2")
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
    val codexUpdates = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val capturedKnownThreadIdsByPath = mutableListOf<Map<String, Set<String>>>()

    val listedThread = thread(
      id = "codex-listed",
      updatedAt = 320L,
      title = "Listed Codex thread",
      provider = AgentSessionProvider.CODEX,
    )

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            supportsUpdates = true,
            updates = codexUpdates,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(listedThread) else emptyList()
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) listOf(listedThread) else emptyList()
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
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
      openPendingCodexTabsProvider = {
        mapOf(
          PROJECT_PATH to listOf(
            AgentChatPendingCodexTabSnapshot(
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

      capturedKnownThreadIdsByPath.clear()

      codexUpdates.emit(Unit)

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.threads.map { it.id }.containsAll(
          listOf(
            "codex-listed",
            "new-ea4cecdd-f115-410d-9c73-f652c21558a9",
          )
        )
      }

      waitForCondition {
        capturedKnownThreadIdsByPath.isNotEmpty()
      }

      codexUpdates.emit(Unit)

      waitForCondition {
        capturedKnownThreadIdsByPath.size >= 2
      }

      assertThat(capturedKnownThreadIdsByPath.last()[PROJECT_PATH])
        .containsExactlyInAnyOrder("codex-listed", "codex-open")
    }
  }

  @Test
  fun markThreadAsReadUpdatesRuntimeAndWarmSnapshotImmediately() = runBlocking(Dispatchers.Default) {
    val warmState = InMemorySessionWarmState()

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                listOf(
                  thread(
                    id = "claude-1",
                    updatedAt = 100,
                    provider = AgentSessionProvider.CLAUDE,
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

      service.markThreadAsRead(PROJECT_PATH, AgentSessionProvider.CLAUDE, "claude-1", 100)

      assertThat(
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }
          ?.threads
          ?.firstOrNull()
          ?.activity
      ).isEqualTo(AgentThreadActivity.READY)
      assertThat(warmState.getPathSnapshot(PROJECT_PATH)?.threads?.firstOrNull()?.activity)
        .isEqualTo(AgentThreadActivity.READY)
    }
  }

  @Test
  fun blockingRefreshFailureKeepsPreviousWarmSnapshot() = runBlocking(Dispatchers.Default) {
    val warmState = InMemorySessionWarmState()
    warmState.setPathSnapshot(
      PROJECT_PATH,
      AgentSessionWarmPathSnapshot(
        threads = listOf(thread(id = "cached-1", updatedAt = 100, provider = AgentSessionProvider.CLAUDE)),
        hasUnknownThreadCount = false,
        updatedAt = 100,
      ),
    )

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
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
}

private data class ServicePendingCodexRebindInvocation(
  @JvmField val path: String,
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val target: AgentChatTabRebindTarget,
)

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
