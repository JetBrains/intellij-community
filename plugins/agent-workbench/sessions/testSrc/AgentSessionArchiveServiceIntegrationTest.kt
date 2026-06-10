// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetryEvent
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetryProvider
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveBackgroundTaskRunner
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmPathSnapshot
import com.intellij.agent.workbench.sessions.state.InMemorySessionWarmState
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionArchiveServiceIntegrationTest {
  @Test
  fun archiveThreadsArchivesAllSupportedTargetsAndSkipsUnsupportedOnes() = runBlocking(Dispatchers.Default) {
    val codexThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 300, provider = AgentSessionProvider.CODEX),
    )
    val claudeThreads = mutableListOf(
      thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE),
    )
    val codexSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) codexThreads.toList() else emptyList()
      },
    )
    val claudeSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) claudeThreads.toList() else emptyList()
      },
    )
    val codexBridge = testCodexBridge(
      sessionSource = codexSource,
      onArchive = { _, threadId ->
        codexThreads.removeIf { it.id == threadId }
        true
      },
    )
    val claudeBridge = object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider
        get() = AgentSessionProvider.CLAUDE
      override val displayNameKey: String
        get() = "toolwindow.provider.claude"
      override val newSessionLabelKey: String
        get() = "toolwindow.action.new.session.claude"
      override val icon: Icon
        get() = AgentWorkbenchCommonIcons.Claude
      override val sessionSource: AgentSessionSource = claudeSource
      override val cliMissingMessageKey: String
        get() = "toolwindow.error.claude.cli"

      override suspend fun isCliAvailable(): Boolean = true
      override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = listOf("claude", "--resume", sessionId))
      }

      override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = listOf("claude"))
      }

      override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
        return AgentInitialMessagePlan.composeDefault(request)
      }
    }

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(codexBridge, claudeBridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(codexSource, claudeSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            threads.any { it.id == "codex-1" } && threads.any { it.id == "claude-1" }
          }

          val threads = service.state.value.projects.first().threads
          val codexTarget = threads.first { it.id == "codex-1" }
          val claudeTarget = threads.first { it.id == "claude-1" }
          archiveService.archiveThreadsForTest(
            listOf(
              ArchiveThreadTarget.Thread(path = PROJECT_PATH, provider = codexTarget.provider, threadId = codexTarget.id),
              ArchiveThreadTarget.Thread(path = PROJECT_PATH, provider = claudeTarget.provider, threadId = claudeTarget.id),
            )
          )

          waitForCondition {
            val currentThreads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            currentThreads.none { it.id == "codex-1" } && currentThreads.any { it.id == "claude-1" }
          }
        }
      }
    }
  }

  @Test
  fun archiveMultipleThreadsUsesPluralProgressAndArchivesSequentially() = runBlocking(Dispatchers.Default) {
    val backgroundRunner = RecordingArchiveBackgroundTaskRunner()
    val archiveCalls = mutableListOf<String>()
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 300, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-3", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ -> if (path == PROJECT_PATH) sourceThreads.toList() else emptyList() },
      listFromClosedProject = { path -> if (path == PROJECT_PATH) sourceThreads.toList() else emptyList() },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        archiveCalls.add(threadId)
        sourceThreads.removeIf { it.id == threadId }
        true
      },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveBackgroundTaskRunner = backgroundRunner,
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads.orEmpty().map { it.id } == listOf("codex-1", "codex-2", "codex-3")
          }

          archiveService.archiveThreadsForTest(
            listOf(
              ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1"),
              ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-2"),
              ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-3"),
            )
          )

          waitForCondition {
            archiveCalls == listOf("codex-1", "codex-2", "codex-3") &&
            service.state.value.projects.firstOrNull()?.threads.orEmpty().isEmpty()
          }
          assertThat(backgroundRunner.titles)
            .containsExactly(AgentSessionsBundle.message("toolwindow.progress.archiving.threads"))
        }
      }
    }
  }

  @Test
  fun archiveThreadRemovesThreadAndRefreshesState() = runBlocking(Dispatchers.Default) {
    val cleanupCalls = mutableListOf<Pair<String, String>>()
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        sourceThreads.removeIf { it.id == threadId }
        true
      },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { projectPath, threadIdentity, _ ->
            cleanupCalls.add(projectPath to threadIdentity)
          },
        ) { service, archiveService ->
          val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
          val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

          try {
            service.refresh()
            waitForCondition {
              service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
            }

            val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
            archiveService.archiveThreadsForTest(
              listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, threadToArchive.provider, threadToArchive.id))
            )

            waitForCondition {
              val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
              threads.none { it.id == "codex-1" } && threads.any { it.id == "codex-2" }
            }

            assertThat(cleanupCalls)
              .containsExactly(PROJECT_PATH to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-1"))
            assertThat(telemetryEvents).contains(
              AgentWorkbenchTelemetryEvent(
                id = AgentWorkbenchTelemetry.THREAD_ARCHIVE_REQUESTED_EVENT_ID,
                entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
                provider = AgentWorkbenchTelemetryProvider.CODEX,
              )
            )
          }
          finally {
            token.finish()
          }
        }
      }
    }
  }

  @Test
  fun archiveThreadKeepsThreadHiddenWhenRefreshReturnsStaleData() = runBlocking(Dispatchers.Default) {
    val cleanupCalls = mutableListOf<Pair<String, String>>()
    val listCalls = AtomicInteger(0)
    val staleSourceThreads = listOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listCalls.incrementAndGet()
          staleSourceThreads
        }
        else {
          emptyList()
        }
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, _ -> true },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { projectPath, threadIdentity, _ ->
            cleanupCalls.add(projectPath to threadIdentity)
          },
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            listCalls.get() > 0 && threads.any { it.id == "codex-1" }
          }

          val callsBeforeArchive = listCalls.get()
          val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
          archiveService.archiveThreadsForTest(
            listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, threadToArchive.provider, threadToArchive.id))
          )

          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            threads.none { it.id == "codex-1" } && threads.any { it.id == "codex-2" }
          }

          waitForCondition(timeoutMs = 6_000) {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            listCalls.get() > callsBeforeArchive && threads.none { it.id == "codex-1" } && threads.any { it.id == "codex-2" }
          }

          assertThat(cleanupCalls)
            .containsExactly(PROJECT_PATH to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-1"))
        }
      }
    }
  }

  @Test
  fun archiveThreadHidesThreadBeforeBackgroundArchiveCompletes() = runBlocking(Dispatchers.Default) {
    val backgroundRunner = PausedArchiveBackgroundTaskRunner()
    val listCalls = AtomicInteger(0)
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listCalls.incrementAndGet()
          sourceThreads.toList()
        }
        else {
          emptyList()
        }
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        sourceThreads.removeIf { it.id == threadId }
        true
      },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveBackgroundTaskRunner = backgroundRunner,
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }

          archiveService.archiveThreadsForTest(listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1")))
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads.orEmpty().map { it.id } == listOf("codex-2")
          }
          assertThat(backgroundRunner.hasPendingTask()).isTrue()

          val callsBeforeArchive = listCalls.get()
          backgroundRunner.resume()
          waitForCondition(timeoutMs = 6_000) {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            listCalls.get() > callsBeforeArchive && threads.none { it.id == "codex-1" }
          }
        }
      }
    }
  }

  @Test
  fun archiveThreadStaysHiddenWhenFullRefreshCompletesWithStaleSourceResult() = runBlocking(Dispatchers.Default) {
    val backgroundRunner = PausedArchiveBackgroundTaskRunner()
    val claudeSourceStarted = CompletableDeferred<Unit>()
    val releaseClaudeSource = CompletableDeferred<Unit>()
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val codexSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
    )
    val claudeSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          claudeSourceStarted.complete(Unit)
          releaseClaudeSource.await()
        }
        emptyList()
      },
    )
    val codexBridge = testCodexBridge(
      sessionSource = codexSource,
      onArchive = { _, threadId ->
        sourceThreads.removeIf { it.id == threadId }
        true
      },
    )
    val claudeBridge = testClaudeBridge(
      sessionSource = claudeSource,
      onArchive = { _, _ -> true },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(codexBridge, claudeBridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(codexSource, claudeSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveBackgroundTaskRunner = backgroundRunner,
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            claudeSourceStarted.isCompleted
          }
          waitForCondition {
            val project = service.state.value.projects.firstOrNull()
            project?.providerLoadStates?.get(AgentSessionProvider.CODEX) == AgentSessionProviderLoadState.LOADED &&
            project.providerLoadStates[AgentSessionProvider.CLAUDE] == AgentSessionProviderLoadState.LOADING &&
            project.threads.map { it.id } == listOf("codex-1", "codex-2")
          }

          archiveService.archiveThreadsForTest(
            listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1"))
          )
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads.orEmpty().map { it.id } == listOf("codex-2")
          }
          assertThat(backgroundRunner.hasPendingTask()).isTrue()

          releaseClaudeSource.complete(Unit)
          waitForCondition {
            val project = service.state.value.projects.firstOrNull()
            project?.providerLoadStates?.get(AgentSessionProvider.CODEX) == AgentSessionProviderLoadState.LOADED &&
            project.providerLoadStates[AgentSessionProvider.CLAUDE] == AgentSessionProviderLoadState.LOADED &&
            project.threads.map { it.id } == listOf("codex-2")
          }

          backgroundRunner.resume()
          waitForCondition(timeoutMs = 6_000) {
            backgroundRunner.completed
          }
        }
      }
    }
  }

  @Test
  fun archiveThreadRestoresThreadWhenBackgroundArchiveFails() = runBlocking(Dispatchers.Default) {
    val backgroundRunner = PausedArchiveBackgroundTaskRunner()
    val cleanupCalls = mutableListOf<Pair<String, String>>()
    val normalizedProjectPath = normalizeAgentWorkbenchPath(PROJECT_PATH)
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (normalizeAgentWorkbenchPath(path) == normalizedProjectPath) sourceThreads.toList() else emptyList()
      },
      listFromClosedProject = { path ->
        if (normalizeAgentWorkbenchPath(path) == normalizedProjectPath) sourceThreads.toList() else emptyList()
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, _ -> false },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { projectPath, threadIdentity, _ -> cleanupCalls.add(projectPath to threadIdentity) },
          archiveBackgroundTaskRunner = backgroundRunner,
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }

          archiveService.archiveThreadsForTest(listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1")))
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads.orEmpty().none { it.id == "codex-1" }
          }
          assertThat(backgroundRunner.hasPendingTask()).isTrue()

          backgroundRunner.resume()
          var restored = false
          var lastObservedThreads = emptyList<String>()
          var attempts = 0
          while (attempts < 300) {
            lastObservedThreads = service.state.value.projects.firstOrNull()?.threads.orEmpty().map { it.id }
            if (lastObservedThreads == listOf("codex-1", "codex-2")) {
              restored = true
              break
            }
            attempts++
            delay(20.milliseconds)
          }
          assertThat(restored)
            .withFailMessage(
              "Expected restored threads after background archive failure, observed threads=%s, backgroundCompleted=%s",
              lastObservedThreads,
              backgroundRunner.completed,
            )
            .isTrue()
          assertThat(cleanupCalls).isEmpty()
        }
      }
    }
  }

  @Test
  fun archiveThreadEagerlyPrunesWarmSnapshotBeforeRefreshRewrite() = runBlocking(Dispatchers.Default) {
    val listCalls = AtomicInteger(0)
    val staleSourceThreads = listOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val warmState = InMemorySessionWarmState()
    warmState.setPathSnapshot(
      PROJECT_PATH,
      AgentSessionWarmPathSnapshot(
        threads = staleSourceThreads,
        updatedAt = 100,
      ),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listCalls.incrementAndGet()
          staleSourceThreads
        }
        else {
          emptyList()
        }
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, _ -> true },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          warmState = warmState,
        ) { _, archiveService ->
          assertThat(warmState.getPathSnapshot(PROJECT_PATH)?.threads.orEmpty().map { it.id })
            .containsExactly("codex-1", "codex-2")

          val callsBeforeArchive = listCalls.get()
          archiveService.archiveThreadsForTest(
            listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "codex-1"))
          )

          waitForCondition {
            listCalls.get() == callsBeforeArchive &&
            warmState.getPathSnapshot(PROJECT_PATH)?.threads.orEmpty().map { it.id } == listOf("codex-2")
          }
        }
      }
    }
  }

  @Test
  fun archiveThreadDoesNotCleanupChatMetadataWhenArchiveFails() = runBlocking(Dispatchers.Default) {
    val archiveCalls = AtomicInteger(0)
    val cleanupCalls = mutableListOf<Pair<String, String>>()
    val backgroundRunner = PausedArchiveBackgroundTaskRunner()
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
      listFromClosedProject = { path ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, _ ->
        archiveCalls.incrementAndGet()
        false
      },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { projectPath, threadIdentity, _ ->
            cleanupCalls.add(projectPath to threadIdentity)
          },
          archiveBackgroundTaskRunner = backgroundRunner,
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }

          val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
          archiveService.archiveThreadsForTest(
            listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, threadToArchive.provider, threadToArchive.id))
          )
          assertThat(backgroundRunner.hasPendingTask()).isTrue()

          backgroundRunner.resume()
          waitForCondition {
            backgroundRunner.completed && archiveCalls.get() == 1
          }

          assertThat(cleanupCalls).isEmpty()
        }
      }
    }
  }

  @Test
  fun archivePendingCodexThreadPerformsLocalCleanupWithoutBackendArchiveCall() = runBlocking(Dispatchers.Default) {
    val cleanupCalls = mutableListOf<Pair<String, String>>()
    val archiveCalls = AtomicInteger(0)
    val sourceThreads = mutableListOf(
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, _ ->
        archiveCalls.incrementAndGet()
        false
      },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { projectPath, threadIdentity, _ ->
            cleanupCalls.add(projectPath to threadIdentity)
          },
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-2" } == true
          }

          archiveService.archiveThreadsForTest(
            listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CODEX, "new-pending"))
          )

          waitForCondition {
            cleanupCalls.isNotEmpty()
          }

          assertThat(archiveCalls.get()).isEqualTo(0)
          assertThat(cleanupCalls)
            .containsExactly(PROJECT_PATH to buildAgentSessionIdentity(AgentSessionProvider.CODEX, "new-pending"))
        }
      }
    }
  }

  @Test
  fun archiveSubAgentKeepsSubAgentHiddenWhenRefreshReturnsStaleData() = runBlocking(Dispatchers.Default) {
    val cleanupCalls = mutableListOf<Triple<String, String, String?>>()
    val archiveCalls = mutableListOf<String>()
    val listCalls = AtomicInteger(0)
    val warmState = InMemorySessionWarmState()
    val sourceThreads = mutableListOf(
      thread(
        id = "codex-parent",
        updatedAt = 200,
        provider = AgentSessionProvider.CODEX,
        subAgents = listOf(AgentSubAgent(id = "codex-sub-1", name = "Sub-agent 1")),
      ),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listCalls.incrementAndGet()
          sourceThreads.toList()
        }
        else {
          emptyList()
        }
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        archiveCalls.add(threadId)
        true
      },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          warmState = warmState,
          archiveChatCleanup = { projectPath, threadIdentity, subAgentId ->
            cleanupCalls.add(Triple(projectPath, threadIdentity, subAgentId))
          },
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads
              ?.firstOrNull { it.id == "codex-parent" }
              ?.subAgents
              ?.any { it.id == "codex-sub-1" } == true
          }
          assertThat(warmState.getPathSnapshot(PROJECT_PATH)?.threads?.single()?.subAgents)
            .containsExactly(AgentSubAgent(id = "codex-sub-1", name = "Sub-agent 1"))

          val callsBeforeArchive = listCalls.get()
          archiveService.archiveThreadsForTest(
            listOf(
              ArchiveThreadTarget.SubAgent(
                path = PROJECT_PATH,
                provider = AgentSessionProvider.CODEX,
                parentThreadId = "codex-parent",
                subAgentId = "codex-sub-1",
              )
            )
          )

          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads
              ?.firstOrNull { it.id == "codex-parent" }
              ?.subAgents
              ?.none { it.id == "codex-sub-1" } == true
          }
          waitForCondition(timeoutMs = 6_000) {
            listCalls.get() > callsBeforeArchive &&
            service.state.value.projects.firstOrNull()?.threads
              ?.firstOrNull { it.id == "codex-parent" }
              ?.subAgents
              ?.none { it.id == "codex-sub-1" } == true
          }

          assertThat(archiveCalls).containsExactly("codex-sub-1")
          assertThat(cleanupCalls)
            .containsExactly(
              Triple(
                PROJECT_PATH,
                buildAgentSessionIdentity(AgentSessionProvider.CODEX, "codex-parent"),
                "codex-sub-1",
              )
            )
          assertThat(warmState.getPathSnapshot(PROJECT_PATH)?.threads?.single()?.subAgents).isEmpty()
        }
      }
    }
  }

  @Test
  fun archiveThreadRefreshesAndRemovesThreadWhenCleanupFails() = runBlocking(Dispatchers.Default) {
    val listCalls = AtomicInteger(0)
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) {
          listCalls.incrementAndGet()
          sourceThreads.toList()
        }
        else {
          emptyList()
        }
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        sourceThreads.removeIf { it.id == threadId }
        true
      },
    )

    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        return false
      }
    }) {
      AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
        runBlocking(Dispatchers.Default) {
          withServiceAndArchive(
            sessionSourcesProvider = { listOf(sessionSource) },
            projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
            archiveChatCleanup = { _, _, _ -> error("cleanup failed") },
          ) { service, archiveService ->
            service.refresh()
            waitForCondition {
              service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
            }

            val callsBeforeArchive = listCalls.get()
            val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
            archiveService.archiveThreadsForTest(
              listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, threadToArchive.provider, threadToArchive.id))
            )

            waitForCondition {
              val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
              threads.none { it.id == "codex-1" } && threads.any { it.id == "codex-2" }
            }

            waitForCondition(timeoutMs = 6_000) {
              val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
              listCalls.get() > callsBeforeArchive && threads.none { it.id == "codex-1" }
            }
          }
        }
      }
    }
  }

  @Test
  fun archiveOpenedClaudeThreadFromEditorTabUsesProviderArchiveAndCleanupTarget() = runBlocking(Dispatchers.Default) {
    val operations = CopyOnWriteArrayList<List<String?>>()
    val sourceThreads = mutableListOf(
      thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE),
      thread(id = "claude-2", updatedAt = 100, provider = AgentSessionProvider.CLAUDE),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromOpenProject = { path, _ -> if (path == PROJECT_PATH) sourceThreads.toList() else emptyList() },
    )
    val bridge = testClaudeBridge(
      sessionSource = sessionSource,
      onArchive = { path, threadId ->
        operations.add(listOf("archive", path, threadId, null))
        sourceThreads.removeIf { it.id == threadId }
        true
      },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          archiveChatCleanup = { projectPath, threadIdentity, subAgentId ->
            operations.add(listOf("cleanup", projectPath, threadIdentity, subAgentId))
          },
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "claude-1" } == true
          }

          archiveService.archiveThreads(
            targets = listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CLAUDE, "claude-1")),
            entryPoint = AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP,
            preferredSingleArchivedLabel = "Claude thread",
          )

          waitForCondition {
            val threads = service.state.value.projects.firstOrNull()?.threads.orEmpty()
            operations == listOf(
              listOf("cleanup", PROJECT_PATH, buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "claude-1"), null),
              listOf("archive", PROJECT_PATH, "claude-1", null),
              listOf("cleanup", PROJECT_PATH, buildAgentSessionIdentity(AgentSessionProvider.CLAUDE, "claude-1"), null),
            ) &&
            threads.none { it.id == "claude-1" } &&
            threads.any { it.id == "claude-2" }
          }
        }
      }
    }
  }

  @Test
  fun archiveOpenedClaudeThreadSkipsProviderArchiveWhenPreArchiveCleanupFails() = runBlocking(Dispatchers.Default) {
    val archiveCalls = AtomicInteger(0)
    val cleanupCalls = AtomicInteger(0)
    val sourceThreads = mutableListOf(
      thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE),
      thread(id = "claude-2", updatedAt = 100, provider = AgentSessionProvider.CLAUDE),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromOpenProject = { path, _ -> if (path == PROJECT_PATH) sourceThreads.toList() else emptyList() },
    )
    val bridge = testClaudeBridge(
      sessionSource = sessionSource,
      onArchive = { _, _ ->
        archiveCalls.incrementAndGet()
        true
      },
    )

    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        return false
      }
    }) {
      AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
        runBlocking(Dispatchers.Default) {
          withServiceAndArchive(
            sessionSourcesProvider = { listOf(sessionSource) },
            projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
            archiveChatCleanup = { _, _, _ ->
              cleanupCalls.incrementAndGet()
              error("pre-archive cleanup failed")
            },
          ) { service, archiveService ->
            service.refresh()
            waitForCondition {
              service.state.value.projects.firstOrNull()?.threads?.any { it.id == "claude-1" } == true
            }

            archiveService.archiveThreadsForTest(
              listOf(ArchiveThreadTarget.Thread(PROJECT_PATH, AgentSessionProvider.CLAUDE, "claude-1"))
            )

            waitForCondition(timeoutMs = 10_000) { cleanupCalls.get() == 1 }
            assertThat(archiveCalls.get()).isZero()
          }
        }
      }
    }
  }

  @Test
  fun unarchiveThreadsRestoresArchivedCodexThread() = runBlocking(Dispatchers.Default) {
    val sourceThreads = mutableListOf(
      thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
      thread(id = "codex-2", updatedAt = 100, provider = AgentSessionProvider.CODEX),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
    )
    val bridge = testCodexBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        sourceThreads.removeIf { it.id == threadId }
        true
      },
      onUnarchive = { _, threadId ->
        if (sourceThreads.none { it.id == threadId }) {
          sourceThreads.add(thread(id = threadId, updatedAt = 250, provider = AgentSessionProvider.CODEX))
        }
        true
      },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }

          val threadToArchive = service.state.value.projects.first().threads.first { it.id == "codex-1" }
          val target = ArchiveThreadTarget.Thread(path = PROJECT_PATH, provider = threadToArchive.provider, threadId = threadToArchive.id)
          archiveService.archiveThreadsForTest(listOf(target))
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.none { it.id == "codex-1" } == true
          }

          archiveService.unarchiveThreads(listOf(target))
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "codex-1" } == true
          }
        }
      }
    }
  }

  @Test
  fun unarchiveThreadsRestoresArchivedClaudeThread() = runBlocking(Dispatchers.Default) {
    val sourceThreads = mutableListOf(
      thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE),
      thread(id = "claude-2", updatedAt = 100, provider = AgentSessionProvider.CLAUDE),
    )
    val sessionSource = ScriptedSessionSource(
      provider = AgentSessionProvider.CLAUDE,
      listFromOpenProject = { path, _ ->
        if (path == PROJECT_PATH) sourceThreads.toList() else emptyList()
      },
    )
    val bridge = testClaudeBridge(
      sessionSource = sessionSource,
      onArchive = { _, threadId ->
        sourceThreads.removeIf { it.id == threadId }
        true
      },
      onUnarchive = { _, threadId ->
        if (sourceThreads.none { it.id == threadId }) {
          sourceThreads.add(thread(id = threadId, updatedAt = 250, provider = AgentSessionProvider.CLAUDE))
        }
        true
      },
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchive(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service, archiveService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "claude-1" } == true
          }

          val threadToArchive = service.state.value.projects.first().threads.first { it.id == "claude-1" }
          val target = ArchiveThreadTarget.Thread(path = PROJECT_PATH, provider = threadToArchive.provider, threadId = threadToArchive.id)
          archiveService.archiveThreadsForTest(listOf(target))
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.none { it.id == "claude-1" } == true
          }

          archiveService.unarchiveThreads(listOf(target))
          waitForCondition {
            service.state.value.projects.firstOrNull()?.threads?.any { it.id == "claude-1" } == true
          }
        }
      }
    }
  }

}

private fun AgentSessionArchiveService.archiveThreadsForTest(
  targets: List<ArchiveThreadTarget>,
  preferredSingleArchivedLabel: String? = null,
) {
  archiveThreads(targets, AgentWorkbenchEntryPoint.TREE_POPUP, preferredSingleArchivedLabel)
}

private class RecordingArchiveBackgroundTaskRunner : AgentSessionArchiveBackgroundTaskRunner {
  val titles = mutableListOf<String>()

  override suspend fun run(project: Project, title: String, block: suspend () -> Unit) {
    titles.add(title)
    block()
  }
}

private class PausedArchiveBackgroundTaskRunner : AgentSessionArchiveBackgroundTaskRunner {
  private val startedSignal = CompletableDeferred<Unit>()
  private val resumeSignal = CompletableDeferred<Unit>()

  @Volatile
  var completed: Boolean = false
    private set

  override suspend fun run(project: Project, title: String, block: suspend () -> Unit) {
    startedSignal.complete(Unit)
    resumeSignal.await()
    block()
    completed = true
  }

  suspend fun hasPendingTask(): Boolean {
    startedSignal.await()
    return !completed
  }

  fun resume() {
    resumeSignal.complete(Unit)
  }
}

private fun testCodexBridge(
  sessionSource: AgentSessionSource,
  onArchive: suspend (path: String, threadId: String) -> Boolean,
  onUnarchive: (suspend (path: String, threadId: String) -> Boolean)? = null,
): AgentSessionProviderDescriptor {
  return object : AgentSessionProviderDescriptor {
    override val provider: AgentSessionProvider
      get() = AgentSessionProvider.CODEX

    override val displayNameKey: String
      get() = "toolwindow.provider.codex"

    override val newSessionLabelKey: String
      get() = "toolwindow.action.new.session.codex"

    override val icon: Icon
      get() = AgentWorkbenchCommonIcons.Codex

    override val sessionSource: AgentSessionSource = sessionSource

    override val cliMissingMessageKey: String
      get() = "toolwindow.error.cli"

    override val supportsArchiveThread: Boolean
      get() = true

    override val supportsUnarchiveThread: Boolean
      get() = onUnarchive != null

    override val archiveRefreshDelayMs: Long
      get() = 1_000L

    override val suppressArchivedThreadsDuringRefresh: Boolean
      get() = true

    override suspend fun isCliAvailable(): Boolean = true

    override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
      return AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", sessionId))
    }

    override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
      return AgentSessionTerminalLaunchSpec(command = listOf("codex"))
    }

    override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
      return AgentInitialMessagePlan.composeDefault(request)
    }

    override suspend fun archiveThread(path: String, threadId: String): Boolean {
      return onArchive(path, threadId)
    }

    override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
      return onUnarchive?.invoke(path, threadId) ?: false
    }
  }
}

private fun testClaudeBridge(
  sessionSource: AgentSessionSource,
  onArchive: suspend (path: String, threadId: String) -> Boolean,
  onUnarchive: (suspend (path: String, threadId: String) -> Boolean)? = null,
): AgentSessionProviderDescriptor {
  return object : AgentSessionProviderDescriptor {
    override val provider: AgentSessionProvider
      get() = AgentSessionProvider.CLAUDE

    override val displayNameKey: String
      get() = "toolwindow.provider.claude"

    override val newSessionLabelKey: String
      get() = "toolwindow.action.new.session.claude"

    override val icon: Icon
      get() = AgentWorkbenchCommonIcons.Claude

    override val sessionSource: AgentSessionSource = sessionSource

    override val cliMissingMessageKey: String
      get() = "toolwindow.error.claude.cli"

    override val supportsArchiveThread: Boolean
      get() = true

    override val closeOpenChatBeforeArchiveThread: Boolean
      get() = true

    override val supportsUnarchiveThread: Boolean
      get() = onUnarchive != null

    override suspend fun isCliAvailable(): Boolean = true

    override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
      return AgentSessionTerminalLaunchSpec(command = listOf("claude", "--resume", sessionId))
    }

    override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
      return AgentSessionTerminalLaunchSpec(command = listOf("claude"))
    }

    override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
      return AgentInitialMessagePlan.composeDefault(request)
    }

    override suspend fun archiveThread(path: String, threadId: String): Boolean {
      return onArchive(path, threadId)
    }

    override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
      return onUnarchive?.invoke(path, threadId) ?: false
    }
  }
}
