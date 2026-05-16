// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class AgentSessionLaunchServiceTest {
  @Test
  fun openArchivedThreadUnarchivesBeforeOpeningAndRefreshesSessionLists() {
    val activeThreads = CopyOnWriteArrayList<AgentSessionThread>()
    val unarchiveCalls = AtomicInteger(0)
    val archivedRefreshCalls = AtomicInteger(0)
    val descriptor = testDescriptor(
      supportsUnarchiveThread = true,
      unarchiveThreadHandler = { path, threadId ->
        unarchiveCalls.incrementAndGet()
        assertThat(path).isEqualTo(PROJECT_PATH)
        assertThat(threadId).isEqualTo("codex-archived")
        activeThreads.add(thread(id = threadId, updatedAt = 250, provider = AgentSessionProvider.CODEX))
        true
      },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val sessionSource = sourceForActiveThreads(activeThreads)

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
          archivedSessionsRefreshIfLoaded = { archivedRefreshCalls.incrementAndGet() },
        ) { service, launchService ->
          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = thread(id = "codex-archived", updatedAt = 200, provider = AgentSessionProvider.CODEX).copy(archived = true),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition {
            chatOpenExecutor.openChatCalls.get() == 1 &&
            archivedRefreshCalls.get() == 1 &&
            activeThreadIds(service.state.value).contains("codex-archived")
          }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.thread.id).isEqualTo("codex-archived")
          assertThat(openRequest.thread.archived).isFalse()
          assertThat(openRequest.subAgent).isNull()
          assertThat(unarchiveCalls.get()).isEqualTo(1)
        }
      }
    }
  }

  @Test
  fun openActiveThreadDoesNotUnarchiveOrRefreshArchivedList() {
    val unarchiveCalls = AtomicInteger(0)
    val archivedRefreshCalls = AtomicInteger(0)
    val descriptor = testDescriptor(
      supportsUnarchiveThread = true,
      unarchiveThreadHandler = { _, _ ->
        unarchiveCalls.incrementAndGet()
        true
      },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(emptyList())) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
          archivedSessionsRefreshIfLoaded = { archivedRefreshCalls.incrementAndGet() },
        ) { _, launchService ->
          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = thread(id = "codex-active", updatedAt = 200, provider = AgentSessionProvider.CODEX),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition { chatOpenExecutor.openChatCalls.get() == 1 }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.thread.id).isEqualTo("codex-active")
          assertThat(openRequest.thread.archived).isFalse()
          assertThat(unarchiveCalls.get()).isZero()
          assertThat(archivedRefreshCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun openActiveThreadIgnoresLastYoloLaunchModeForSameProvider() {
    val descriptor = testDescriptor(
      supportsUnarchiveThread = true,
      unarchiveThreadHandler = { _, _ -> false },
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val uiPreferencesState = AgentSessionUiPreferencesStateService().also { preferences ->
      preferences.updateProviderPreferencesOnLaunch(
        provider = AgentSessionProvider.CODEX,
        launchMode = AgentSessionLaunchMode.YOLO,
        initialMessageRequest = null,
      )
    }

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(emptyList())) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          uiPreferencesState = uiPreferencesState,
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = thread(id = "codex-active", updatedAt = 200, provider = AgentSessionProvider.CODEX),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition { chatOpenExecutor.openChatCalls.get() == 1 }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.launchMode).isNull()
        }
      }
    }
  }

  @Test
  fun promptExistingThreadUsesRequestedYoloLaunchMode() {
    val descriptor = testDescriptor(
      supportsUnarchiveThread = true,
      unarchiveThreadHandler = { _, _ -> false },
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val activeThread = thread(id = "codex-active", updatedAt = 200, provider = AgentSessionProvider.CODEX)

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(listOf(activeThread))) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          service.refresh()
          waitForCondition { activeThreadIds(service.state.value).contains(activeThread.id) }

          val result = launchService.launchPromptRequest(
            AgentPromptLaunchRequest(
              provider = AgentSessionProvider.CODEX,
              projectPath = PROJECT_PATH,
              launchMode = AgentSessionLaunchMode.YOLO,
              initialMessageRequest = AgentPromptInitialMessageRequest(prompt = "Continue this thread"),
              targetThreadId = activeThread.id,
            )
          )

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition { chatOpenExecutor.openChatCalls.get() == 1 }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.thread.id).isEqualTo(activeThread.id)
          assertThat(openRequest.launchMode).isEqualTo(AgentSessionLaunchMode.YOLO)
        }
      }
    }
  }

  @Test
  fun openArchivedThreadWithFailedUnarchiveStillOpensWithoutRefreshingActiveList() {
    val unarchiveCalls = AtomicInteger(0)
    val archivedRefreshCalls = AtomicInteger(0)
    val descriptor = testDescriptor(
      supportsUnarchiveThread = true,
      unarchiveThreadHandler = { _, _ ->
        unarchiveCalls.incrementAndGet()
        false
      },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(emptyList())) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
          archivedSessionsRefreshIfLoaded = { archivedRefreshCalls.incrementAndGet() },
        ) { _, launchService ->
          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = thread(id = "codex-archived", updatedAt = 200, provider = AgentSessionProvider.CODEX).copy(archived = true),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition { chatOpenExecutor.openChatCalls.get() == 1 }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.thread.id).isEqualTo("codex-archived")
          assertThat(openRequest.thread.archived).isTrue()
          assertThat(unarchiveCalls.get()).isEqualTo(1)
          assertThat(archivedRefreshCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun openArchivedThreadWithoutUnarchiveSupportStillOpensWithoutRefreshingActiveList() {
    val unarchiveCalls = AtomicInteger(0)
    val archivedRefreshCalls = AtomicInteger(0)
    val descriptor = testDescriptor(
      supportsUnarchiveThread = false,
      unarchiveThreadHandler = { _, _ ->
        unarchiveCalls.incrementAndGet()
        true
      },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(emptyList())) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
          archivedSessionsRefreshIfLoaded = { archivedRefreshCalls.incrementAndGet() },
        ) { _, launchService ->
          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = thread(id = "codex-archived", updatedAt = 200, provider = AgentSessionProvider.CODEX).copy(archived = true),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition { chatOpenExecutor.openChatCalls.get() == 1 }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.thread.id).isEqualTo("codex-archived")
          assertThat(openRequest.thread.archived).isTrue()
          assertThat(unarchiveCalls.get()).isZero()
          assertThat(archivedRefreshCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun openPreviouslyArchivedThreadUnsuppressesBeforeRefreshingActiveList() {
    val activeThread = thread(id = "codex-archived", updatedAt = 200, provider = AgentSessionProvider.CODEX)
    val activeThreads = CopyOnWriteArrayList(listOf(activeThread))
    val archiveCalls = AtomicInteger(0)
    val unarchiveCalls = AtomicInteger(0)
    val archivedRefreshCalls = AtomicInteger(0)
    val descriptor = testDescriptor(
      supportsArchiveThread = true,
      supportsUnarchiveThread = true,
      suppressArchivedThreadsDuringRefresh = true,
      archiveThreadHandler = { path, threadId ->
        archiveCalls.incrementAndGet()
        assertThat(path).isEqualTo(PROJECT_PATH)
        assertThat(threadId).isEqualTo(activeThread.id)
        true
      },
      unarchiveThreadHandler = { path, threadId ->
        unarchiveCalls.incrementAndGet()
        assertThat(path).isEqualTo(PROJECT_PATH)
        assertThat(threadId).isEqualTo(activeThread.id)
        true
      },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val sessionSource = sourceForActiveThreads(activeThreads)

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withServiceAndArchiveAndLaunch(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
          archivedSessionsRefreshIfLoaded = { archivedRefreshCalls.incrementAndGet() },
        ) { service, archiveService, launchService ->
          service.refresh()
          waitForCondition { activeThreadIds(service.state.value).contains(activeThread.id) }

          val archiveTarget = ArchiveThreadTarget.Thread(
            path = PROJECT_PATH,
            provider = AgentSessionProvider.CODEX,
            threadId = activeThread.id,
          )
          archiveService.archiveThreads(listOf(archiveTarget), AgentWorkbenchEntryPoint.TREE_POPUP)
          waitForCondition { !activeThreadIds(service.state.value).contains(activeThread.id) }

          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = activeThread.copy(archived = true),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition {
            chatOpenExecutor.openChatCalls.get() == 1 &&
            archivedRefreshCalls.get() == 1 &&
            activeThreadIds(service.state.value).contains(activeThread.id)
          }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.thread.id).isEqualTo(activeThread.id)
          assertThat(openRequest.thread.archived).isFalse()
          assertThat(archiveCalls.get()).isEqualTo(1)
          assertThat(unarchiveCalls.get()).isEqualTo(1)
        }
      }
    }
  }

  @Test
  fun openArchivedSubAgentUnarchivesParentThreadBeforeOpening() {
    val activeThreads = CopyOnWriteArrayList<AgentSessionThread>()
    val subAgent = AgentSubAgent(id = "sub-1", name = "Sub Agent")
    val archivedThread = thread(
      id = "codex-archived",
      updatedAt = 200,
      provider = AgentSessionProvider.CODEX,
      subAgents = listOf(subAgent),
    ).copy(archived = true)
    val unarchiveCalls = AtomicInteger(0)
    val archivedRefreshCalls = AtomicInteger(0)
    val descriptor = testDescriptor(
      supportsUnarchiveThread = true,
      unarchiveThreadHandler = { _, threadId ->
        unarchiveCalls.incrementAndGet()
        activeThreads.add(archivedThread.copy(archived = false))
        threadId == archivedThread.id
      },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val sessionSource = sourceForActiveThreads(activeThreads)

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
          archivedSessionsRefreshIfLoaded = { archivedRefreshCalls.incrementAndGet() },
        ) { service, launchService ->
          launchService.openChatSubAgent(
            path = PROJECT_PATH,
            thread = archivedThread,
            subAgent = subAgent,
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition {
            chatOpenExecutor.openChatCalls.get() == 1 &&
            archivedRefreshCalls.get() == 1 &&
            activeThreadIds(service.state.value).contains("codex-archived")
          }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.thread.id).isEqualTo("codex-archived")
          assertThat(openRequest.thread.archived).isFalse()
          assertThat(openRequest.subAgent).isEqualTo(subAgent)
          assertThat(unarchiveCalls.get()).isEqualTo(1)
        }
      }
    }
  }

  private fun testDescriptor(
    supportsUnarchiveThread: Boolean,
    unarchiveThreadHandler: suspend (String, String) -> Boolean,
    supportedModes: Set<AgentSessionLaunchMode> = emptySet(),
    supportsArchiveThread: Boolean = false,
    suppressArchivedThreadsDuringRefresh: Boolean = false,
    archiveThreadHandler: suspend (String, String) -> Boolean = { _, _ -> false },
  ): TestAgentSessionProviderDescriptor {
    return TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = supportedModes,
      cliAvailable = true,
      supportsArchiveThread = supportsArchiveThread,
      supportsUnarchiveThread = supportsUnarchiveThread,
      suppressArchivedThreadsDuringRefresh = suppressArchivedThreadsDuringRefresh,
      archiveThreadHandler = archiveThreadHandler,
      unarchiveThreadHandler = unarchiveThreadHandler,
    )
  }

  private fun sourceForActiveThreads(threads: List<AgentSessionThread>): ScriptedSessionSource {
    return ScriptedSessionSource(
      provider = AgentSessionProvider.CODEX,
      listFromOpenProject = { path, _ -> if (path == PROJECT_PATH) threads.toList() else emptyList() },
    )
  }
}

private fun activeThreadIds(state: AgentSessionsState): List<String> {
  return state.projects.flatMap { project ->
    project.threads.map { thread -> thread.id } +
    project.worktrees.flatMap { worktree -> worktree.threads.map { thread -> thread.id } }
  }
}
