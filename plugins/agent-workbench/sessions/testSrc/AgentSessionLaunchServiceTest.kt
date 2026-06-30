// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindOutcome
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.platform.ai.agent.sessions.core.launch.AGENT_SESSION_SURFACE_ACP
import com.intellij.platform.ai.agent.sessions.core.launch.AGENT_SESSION_SURFACE_TERMINAL
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.state.AgentSessionLaunchProfileStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
        activeThreads.add(thread(id = threadId, updatedAt = 250, provider = AgentSessionProvider.from("codex")))
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
            thread = thread(id = "codex-archived", updatedAt = 200, provider = AgentSessionProvider.from("codex")).copy(archived = true),
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
            thread = thread(id = "codex-active", updatedAt = 200, provider = AgentSessionProvider.from("codex")),
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
  fun openChatThreadPassesOpenedChatHandlerToExecutor() {
    val descriptor = testDescriptor(
      supportsUnarchiveThread = false,
      unarchiveThreadHandler = { _, _ -> false },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val openedChatHandler: suspend (Project, VirtualFile) -> Unit = { _, _ -> }

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(emptyList())) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = thread(id = "codex-active", updatedAt = 200, provider = AgentSessionProvider.from("codex")),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
            openedChatHandler = openedChatHandler,
          )

          waitForCondition { chatOpenExecutor.openChatCalls.get() == 1 }

          assertThat(chatOpenExecutor.lastOpenChatHandler.get()).isSameAs(openedChatHandler)
        }
      }
    }
  }

  @Test
  fun openThreadWithBranchMismatchAllowsDialogToAcquireWriteIntentReadLock() {
    val descriptor = testDescriptor(
      supportsUnarchiveThread = false,
      unarchiveThreadHandler = { _, _ -> false },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val confirmations = AtomicInteger(0)
    val currentProject = ProjectManager.getInstance().defaultProject

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(emptyList())) },
          projectEntriesProvider = { listOf(featureWorktreeProjectEntry()) },
          chatOpenExecutor = chatOpenExecutor,
          branchMismatchConfirmation = { project, originBranch, currentBranch ->
            confirmations.incrementAndGet()
            assertThat(project).isSameAs(currentProject)
            assertThat(originBranch).isEqualTo("main")
            assertThat(currentBranch).isEqualTo("feature")

            val application = ApplicationManager.getApplication()
            assertThat(application.isWriteIntentLockAcquired).isFalse()
            application.runWriteIntentReadAction<Unit, Exception> {}
            true
          },
        ) { service, launchService ->
          service.refreshCatalogAndLoadNewlyOpened()
          waitForCondition { hasFeatureWorktree(service.state.value) }

          launchService.openChatThread(
            path = WORKTREE_PATH,
            thread = branchMismatchThread(),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
            currentProject = currentProject,
          )

          waitForCondition { chatOpenExecutor.openChatCalls.get() == 1 }
          assertThat(confirmations.get()).isEqualTo(1)
        }
      }
    }
  }

  @Test
  fun openThreadWithBranchMismatchCancelsWhenDialogRejected() {
    val descriptor = testDescriptor(
      supportsUnarchiveThread = false,
      unarchiveThreadHandler = { _, _ -> false },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val confirmations = AtomicInteger(0)
    val promptResults = CopyOnWriteArrayList<AgentPromptLaunchResult>()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(emptyList())) },
          projectEntriesProvider = { listOf(featureWorktreeProjectEntry()) },
          chatOpenExecutor = chatOpenExecutor,
          branchMismatchConfirmation = { _, originBranch, currentBranch ->
            confirmations.incrementAndGet()
            assertThat(originBranch).isEqualTo("main")
            assertThat(currentBranch).isEqualTo("feature")
            false
          },
        ) { service, launchService ->
          service.refreshCatalogAndLoadNewlyOpened()
          waitForCondition { hasFeatureWorktree(service.state.value) }

          launchService.openChatThread(
            path = WORKTREE_PATH,
            thread = branchMismatchThread(),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
            promptLaunchResolved = promptResults::add,
          )

          waitForCondition { promptResults.singleOrNull()?.error == AgentPromptLaunchError.CANCELLED }
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()
          assertThat(confirmations.get()).isEqualTo(1)
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

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(emptyList())) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = thread(id = "codex-active", updatedAt = 200, provider = AgentSessionProvider.from("codex")),
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
    val activeThread = thread(id = "codex-active", updatedAt = 200, provider = AgentSessionProvider.from("codex"))

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
              provider = AgentSessionProvider.from("codex"),
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
  fun promptExistingThreadResolvesLaunchProfilePayload() {
    val descriptor = testDescriptor(
      supportsUnarchiveThread = true,
      unarchiveThreadHandler = { _, _ -> false },
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
    )
    val profileId = "profile:codex-yolo"
    val profileSettings = AgentPromptGenerationSettings(
      modelId = "gpt-5",
    )
    val uiPreferencesState = uiPreferencesStateWithProfiles(
      AgentPromptLaunchProfile(
        id = profileId,
        name = "Codex Yolo",
        providerId = AgentSessionProvider.from("codex").value,
        launchMode = AgentSessionLaunchMode.YOLO,
        launchTargetId = "codex.test.target",
        surfaceId = AGENT_SESSION_SURFACE_TERMINAL,
        generationSettings = profileSettings,
      )
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val activeThread = thread(id = "codex-active", updatedAt = 200, provider = AgentSessionProvider.from("codex"))

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(listOf(activeThread))) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          uiPreferencesState = uiPreferencesState,
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          service.refresh()
          waitForCondition { activeThreadIds(service.state.value).contains(activeThread.id) }

          val result = launchService.launchPromptRequest(
            AgentPromptLaunchRequest(
              launchProfileId = profileId,
              provider = AgentSessionProvider.from("codex"),
              projectPath = PROJECT_PATH,
              launchMode = AgentSessionLaunchMode.STANDARD,
              surfaceId = AGENT_SESSION_SURFACE_ACP,
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
          assertThat(openRequest.launchProfileId).isEqualTo(profileId)
          assertThat(openRequest.launchTargetId).isEqualTo("codex.test.target")
          assertThat(openRequest.surfaceId).isEqualTo(AGENT_SESSION_SURFACE_TERMINAL)
          assertThat(openRequest.generationSettings).isEqualTo(profileSettings)
        }
      }
    }
  }

  @Test
  fun createNewSessionResolvesLaunchProfilePayload() {
    val descriptor = testDescriptor(
      supportsUnarchiveThread = true,
      unarchiveThreadHandler = { _, _ -> false },
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO),
    )
    val profileId = "profile:codex-new-yolo"
    val profileSettings = AgentPromptGenerationSettings(
      modelId = "gpt-5",
    )
    val uiPreferencesState = uiPreferencesStateWithProfiles(
      AgentPromptLaunchProfile(
        id = profileId,
        name = "Codex New Yolo",
        providerId = AgentSessionProvider.from("codex").value,
        launchMode = AgentSessionLaunchMode.YOLO,
        launchTargetId = "codex.active.target",
        surfaceId = AGENT_SESSION_SURFACE_TERMINAL,
        generationSettings = profileSettings,
      )
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(descriptor.sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          uiPreferencesState = uiPreferencesState,
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.createNewSession(
            path = PROJECT_PATH,
            launchProfileId = profileId,
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition { chatOpenExecutor.openNewChatCalls.get() == 1 }
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.identity).startsWith("codex:new-")
          assertThat(openRequest.launchMode).isEqualTo(AgentSessionLaunchMode.YOLO)
          assertThat(openRequest.launchProfileId).isEqualTo(profileId)
          assertThat(openRequest.launchTargetId).isEqualTo("codex.active.target")
          assertThat(openRequest.surfaceId).isEqualTo(AGENT_SESSION_SURFACE_TERMINAL)
          assertThat(openRequest.generationSettings).isEqualTo(profileSettings)
        }
      }
    }
  }

  @Test
  fun createNewSessionOpensPreparingChatBeforeLaunchSpecIsPrepared() {
    val launchSpecRequested = CompletableDeferred<Unit>()
    val releaseLaunchSpec = CompletableDeferred<Unit>()
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      newSessionLaunchSpecProvider = { mode ->
        launchSpecRequested.complete(Unit)
        releaseLaunchSpec.await()
        AgentSessionTerminalLaunchSpec(command = listOf("test", "new", mode.name))
      },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(descriptor.sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.createNewSession(
            path = PROJECT_PATH,
            provider = AgentSessionProvider.from("codex"),
            mode = AgentSessionLaunchMode.STANDARD,
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          chatOpenExecutor.awaitOpenPreparingNewChatCalls(1)
          val preparingRequest = checkNotNull(chatOpenExecutor.lastOpenPreparingNewChatRequest.get())
          assertThat(preparingRequest.hasDeferredStartContentProvider).isFalse()
          assertThat(preparingRequest.surfaceId).isEqualTo(AGENT_SESSION_SURFACE_TERMINAL)
          withTimeout(5_000.milliseconds) { launchSpecRequested.await() }
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()

          releaseLaunchSpec.complete(Unit)
          chatOpenExecutor.awaitOpenNewChatCalls(1)
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.launchSpec.command).containsExactly("test", "new", AgentSessionLaunchMode.STANDARD.name)
          assertThat(openRequest.surfaceId).isEqualTo(AGENT_SESSION_SURFACE_TERMINAL)
        }
      }
    }
  }

  @Test
  fun createNewSessionDefaultsAcpProviderToAcpSurface() {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("acp"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(descriptor.sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.createNewSession(
            path = PROJECT_PATH,
            provider = AgentSessionProvider.from("acp"),
            mode = AgentSessionLaunchMode.STANDARD,
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          chatOpenExecutor.awaitOpenNewChatCalls(1)
          val preparingRequest = checkNotNull(chatOpenExecutor.lastOpenPreparingNewChatRequest.get())
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(preparingRequest.surfaceId).isEqualTo(AGENT_SESSION_SURFACE_ACP)
          assertThat(openRequest.surfaceId).isEqualTo(AGENT_SESSION_SURFACE_ACP)
        }
      }
    }
  }

  @Test
  fun createNewSessionUsesGenericDeferredWaitingCopy() {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(descriptor.sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.createNewSession(
            path = PROJECT_PATH,
            provider = AgentSessionProvider.from("codex"),
            mode = AgentSessionLaunchMode.STANDARD,
            entryPoint = AgentWorkbenchEntryPoint.PROMPT,
          )

          chatOpenExecutor.awaitOpenPreparingNewChatCalls(1)
          val state = checkNotNull(chatOpenExecutor.lastOpenPreparingNewChatRequest.get()).waitingState
          assertThat(state.title).isEqualTo("Starting new thread…")
          assertThat(state.message).isNull()
        }
      }
    }
  }

  @Test
  fun createDeferredNewSessionPassesDeferredStartContentProviderToPreparingChat() {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(descriptor.sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          val result = launchService.createDeferredNewSession(
            path = PROJECT_PATH,
            provider = AgentSessionProvider.from("codex"),
            mode = AgentSessionLaunchMode.STANDARD,
            entryPoint = AgentWorkbenchEntryPoint.PROMPT,
            waitingTitle = "Preparing",
            deferredStartContentProvider = { error("test executor records the provider without rendering it") },
          )

          assertThat(result.handle).isNotNull()
          chatOpenExecutor.awaitOpenPreparingNewChatCalls(1)
          assertThat(checkNotNull(chatOpenExecutor.lastOpenPreparingNewChatRequest.get()).hasDeferredStartContentProvider).isTrue()
        }
      }
    }
  }

  @Test
  fun createNewSessionReportsPreparationFailureInOpenedChat() {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val launchResult = CompletableDeferred<AgentPromptLaunchResult>()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(descriptor.sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.createNewSession(
            path = PROJECT_PATH,
            provider = AgentSessionProvider.from("codex"),
            mode = AgentSessionLaunchMode.STANDARD,
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
            promptLaunchResolved = { result -> launchResult.complete(result) },
          )

          chatOpenExecutor.awaitOpenPreparingNewChatCalls(1)
          waitForCondition { chatOpenExecutor.failPreparingNewChatCalls.get() == 1 }
          val result = withTimeout(5_000.milliseconds) { launchResult.await() }
          assertThat(result.launched).isFalse()
          assertThat(result.error).isEqualTo(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
          assertThat(chatOpenExecutor.lastFailPreparingNewChatMessage.get()).isNotBlank()
        }
      }
    }
  }

  @Test
  fun deferredNewSessionPromptLaunchCanRetryAfterRejectedPreparation() {
    val launchSpecAttempts = AtomicInteger(0)
    val descriptor = object : TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      newSessionLaunchSpecProvider = { _ ->
        val attempt = launchSpecAttempts.incrementAndGet()
        AgentSessionTerminalLaunchSpec(command = listOf("test", "retry", attempt.toString()))
      },
    ) {
      override val supportedReasoningEfforts: Set<AgentPromptReasoningEffort>
        get() = setOf(AgentPromptReasoningEffort.HIGH)
    }
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(descriptor.sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          val handle = checkNotNull(
            launchService.createDeferredNewSession(
              path = PROJECT_PATH,
              provider = AgentSessionProvider.from("codex"),
              mode = AgentSessionLaunchMode.STANDARD,
              entryPoint = AgentWorkbenchEntryPoint.PROMPT,
              waitingTitle = "Preparing",
            ).handle
          )
          chatOpenExecutor.awaitOpenPreparingNewChatCalls(1)

          val rejectedRequest = AgentPromptLaunchRequest(
            provider = AgentSessionProvider.from("codex"),
            projectPath = PROJECT_PATH,
            launchMode = AgentSessionLaunchMode.YOLO,
            initialMessageRequest = AgentPromptInitialMessageRequest(prompt = "Rejected before retry"),
          )
          val request = AgentPromptLaunchRequest(
            provider = AgentSessionProvider.from("codex"),
            projectPath = PROJECT_PATH,
            launchMode = AgentSessionLaunchMode.STANDARD,
            generationSettings = AgentPromptGenerationSettings(
              modelId = "gpt-5.1-codex",
              reasoningEffort = AgentPromptReasoningEffort.HIGH,
            ),
            initialMessageRequest = AgentPromptInitialMessageRequest(prompt = "Start after retry"),
          )
          val failedResult = handle.launch(rejectedRequest)
          assertThat(failedResult.launched).isFalse()
          assertThat(failedResult.error).isEqualTo(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE)
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
          assertThat(chatOpenExecutor.failPreparingNewChatCalls.get()).isZero()
          assertThat(launchSpecAttempts.get()).isZero()

          val successfulResult = handle.launch(request)
          assertThat(successfulResult.launched).isTrue()
          assertThat(successfulResult.error).isNull()
          chatOpenExecutor.awaitOpenNewChatCalls(1)

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.launchSpec.command).containsExactly("test", "retry", "1")
          assertThat(openRequest.initialComposedMessage).isEqualTo("Start after retry")
          assertThat(openRequest.generationSettings).isEqualTo(request.generationSettings)

          val duplicateResult = handle.launch(request)
          assertThat(duplicateResult.launched).isFalse()
          assertThat(duplicateResult.error).isEqualTo(AgentPromptLaunchError.DROPPED_DUPLICATE)
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isEqualTo(1)
          assertThat(launchSpecAttempts.get()).isEqualTo(1)
        }
      }
    }
  }

  @Test
  fun promptLaunchRejectsPendingNewThreadTarget() {
    val descriptor = testDescriptor(
      supportsUnarchiveThread = true,
      unarchiveThreadHandler = { _, _ -> false },
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val pendingThread = thread(id = "new-global-prompt", updatedAt = 200, provider = AgentSessionProvider.from("codex"))

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(listOf(pendingThread))) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          service.refresh()
          waitForCondition { activeThreadIds(service.state.value).contains(pendingThread.id) }

          val result = launchService.launchPromptRequest(
            AgentPromptLaunchRequest(
              provider = AgentSessionProvider.from("codex"),
              projectPath = PROJECT_PATH,
              launchMode = AgentSessionLaunchMode.STANDARD,
              initialMessageRequest = AgentPromptInitialMessageRequest(prompt = "Start another thread"),
              targetThreadId = pendingThread.id,
            )
          )

          assertThat(result.launched).isFalse()
          assertThat(result.error).isEqualTo(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND)
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun promptLaunchRejectsProvidersWithoutPromptLaunchSupport() {
    val descriptor = testDescriptor(
      supportsUnarchiveThread = false,
      unarchiveThreadHandler = { _, _ -> false },
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      supportsPromptLaunch = false,
    )
    val activeThread = thread(id = "codex-active", updatedAt = 200, provider = AgentSessionProvider.from("codex"))
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(sourceForActiveThreads(listOf(activeThread))) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          service.refresh()
          waitForCondition { activeThreadIds(service.state.value).contains(activeThread.id) }

          val existingThreadResult = launchService.launchPromptRequest(
            AgentPromptLaunchRequest(
              provider = AgentSessionProvider.from("codex"),
              projectPath = PROJECT_PATH,
              launchMode = AgentSessionLaunchMode.STANDARD,
              initialMessageRequest = AgentPromptInitialMessageRequest(prompt = "Continue this thread"),
              targetThreadId = activeThread.id,
            )
          )
          val newThreadResult = launchService.launchPromptRequest(
            AgentPromptLaunchRequest(
              provider = AgentSessionProvider.from("codex"),
              projectPath = PROJECT_PATH,
              launchMode = AgentSessionLaunchMode.STANDARD,
              initialMessageRequest = AgentPromptInitialMessageRequest(prompt = "Start a new thread"),
            )
          )

          assertThat(existingThreadResult.launched).isFalse()
          assertThat(existingThreadResult.error).isEqualTo(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
          assertThat(newThreadResult.launched).isFalse()
          assertThat(newThreadResult.error).isEqualTo(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun createNewSessionDoesNotUpdateProviderOptionsForProviderWithoutPromptLaunchSupport() {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("terminal"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      supportsPromptLaunch = false,
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val uiPreferencesState = AgentSessionUiPreferencesStateService()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(descriptor.sessionSource) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          uiPreferencesState = uiPreferencesState,
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.createNewSession(
            path = PROJECT_PATH,
            provider = AgentSessionProvider.from("terminal"),
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition { chatOpenExecutor.openNewChatCalls.get() == 1 }

          assertThat(uiPreferencesState.getProviderPreferences().providerOptionsByProviderId).isEmpty()
        }
      }
    }
  }

  @Test
  fun createNewSessionUsesPreallocatedLaunchSpecSessionIdAsConcreteIdentity() {
    val provider = AgentSessionProvider.from("claude")
    val preallocatedSessionId = "a174b4df-e942-49fe-bb30-8b5f8e7f4857"
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = provider,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      newSessionLaunchSpecProvider = { mode ->
        assertThat(mode).isEqualTo(AgentSessionLaunchMode.STANDARD)
        AgentSessionTerminalLaunchSpec(
          command = listOf("test", "new"),
          preallocatedSessionId = preallocatedSessionId,
        )
      },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(ScriptedSessionSource(provider = provider)) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.createNewSession(
            path = PROJECT_PATH,
            provider = provider,
            entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
          )

          waitForCondition { chatOpenExecutor.openNewChatCalls.get() == 1 }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.identity).isEqualTo(buildAgentSessionIdentity(provider, preallocatedSessionId))
          assertThat(openRequest.launchSpec.preallocatedSessionId).isEqualTo(preallocatedSessionId)
        }
      }
    }
  }

  @Test
  fun createNewSessionBuilderAndPreparedHandlerUsePreallocatedLaunchSpecSessionId() {
    val provider = AgentSessionProvider.from("pi")
    val preallocatedSessionId = "f174b4df-e942-49fe-bb30-8b5f8e7f4857"
    val builderThreadIds = CopyOnWriteArrayList<String>()
    val preparedThreadIds = CopyOnWriteArrayList<String>()
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = provider,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      newSessionLaunchSpecProvider = {
        AgentSessionTerminalLaunchSpec(
          command = listOf("test", "new"),
          preallocatedSessionId = preallocatedSessionId,
        )
      },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(ScriptedSessionSource(provider = provider)) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.createNewSession(
            path = PROJECT_PATH,
            provider = provider,
            entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
            initialMessageRequestBuilder = { context ->
              builderThreadIds += context.threadId
              AgentPromptInitialMessageRequest(prompt = "Start task folder ${context.threadId}")
            },
            preparedLaunchHandler = { context ->
              preparedThreadIds += context.threadId
            },
          )

          waitForCondition { chatOpenExecutor.openNewChatCalls.get() == 1 }

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.identity).isEqualTo(buildAgentSessionIdentity(provider, preallocatedSessionId))
          assertThat(openRequest.initialComposedMessage).isEqualTo("Start task folder $preallocatedSessionId")
          assertThat(builderThreadIds).containsExactly(preallocatedSessionId)
          assertThat(preparedThreadIds).containsExactly(preallocatedSessionId)
        }
      }
    }
  }

  @Test
  fun openCodexThreadRebindsMatchingPendingTabBeforeOpening() {
    assertOpenThreadRebindsMatchingPendingTabBeforeOpening(AgentSessionProvider.from("codex"))
  }

  @Test
  fun openClaudeThreadRebindsMatchingPendingTabBeforeOpening() {
    assertOpenThreadRebindsMatchingPendingTabBeforeOpening(AgentSessionProvider.from("claude"))
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
            thread = thread(id = "codex-archived", updatedAt = 200, provider = AgentSessionProvider.from("codex")).copy(archived = true),
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
            thread = thread(id = "codex-archived", updatedAt = 200, provider = AgentSessionProvider.from("codex")).copy(archived = true),
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
    val activeThread = thread(id = "codex-archived", updatedAt = 200, provider = AgentSessionProvider.from("codex"))
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
            provider = AgentSessionProvider.from("codex"),
            threadId = activeThread.id,
          )
          archiveService.archiveThreads(listOf(archiveTarget), AgentWorkbenchEntryPoint.TREE_POPUP)
          waitForCondition {
            archiveCalls.get() == 1 &&
            !activeThreadIds(service.state.value).contains(activeThread.id)
          }

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
      provider = AgentSessionProvider.from("codex"),
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

  private fun assertOpenThreadRebindsMatchingPendingTabBeforeOpening(provider: AgentSessionProvider) {
    val descriptor = TestAgentSessionProviderDescriptor(
      provider = provider,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      supportsPendingEditorTabRebind = true,
    )
    val pendingCreatedAtMs = 1_000L
    val pendingThreadIdentity = buildAgentSessionIdentity(provider, "new-pending-open")
    val resolvedThread = thread(
      id = "resolved-open",
      updatedAt = pendingCreatedAtMs + 200L,
      title = "Resolved open",
      provider = provider,
    )
    val pendingTab = AgentChatPendingTabSnapshot(
      projectPath = PROJECT_PATH,
      pendingTabKey = "pending-$pendingThreadIdentity",
      pendingThreadIdentity = pendingThreadIdentity,
      pendingCreatedAtMs = pendingCreatedAtMs,
      pendingFirstInputAtMs = null,
      pendingLaunchMode = "standard",
    )
    val rebindInvocations = CopyOnWriteArrayList<AgentChatPendingTabRebindRequest>()
    val chatOpenExecutor = RecordingChatOpenExecutor(
      onOpenChat = { _, _ ->
        assertThat(rebindInvocations).hasSize(1)
      }
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        withTestServiceAndLaunch(
          sessionSourcesProvider = { listOf(ScriptedSessionSource(provider = provider)) },
          projectEntriesProvider = { listOf(openTestProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
          openPendingAgentChatTabsProvider = { requestedProvider ->
            if (requestedProvider == provider) mapOf(PROJECT_PATH to listOf(pendingTab)) else emptyMap()
          },
          openAgentChatPendingTabsBinderWithProvider = { requestedProvider, requestsByPath ->
            assertThat(requestedProvider.value).isEqualTo(provider.value)
            requestsByPath.values.flatten().forEach(rebindInvocations::add)
            successfulPendingRebindReport(requestsByPath)
          },
        ) { _, launchService ->
          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = resolvedThread,
            entryPoint = AgentWorkbenchEntryPoint.TREE_ROW,
          )

          waitForCondition { chatOpenExecutor.openChatCalls.get() == 1 }

          val rebindRequest = rebindInvocations.single()
          assertThat(rebindRequest.pendingTabKey).isEqualTo("pending-$pendingThreadIdentity")
          assertThat(rebindRequest.pendingThreadIdentity).isEqualTo(pendingThreadIdentity)
          assertThat(rebindRequest.target.provider).isEqualTo(provider)
          assertThat(rebindRequest.target.threadIdentity).isEqualTo(buildAgentSessionIdentity(provider, resolvedThread.id))
          assertThat(rebindRequest.target.threadId).isEqualTo(resolvedThread.id)
          assertThat(checkNotNull(chatOpenExecutor.lastOpenChatRequest.get()).thread.id).isEqualTo(resolvedThread.id)
        }
      }
    }
  }

  private fun testDescriptor(
    supportsUnarchiveThread: Boolean,
    unarchiveThreadHandler: suspend (String, String) -> Boolean,
    supportedModes: Set<AgentSessionLaunchMode> = emptySet(),
    supportsPromptLaunch: Boolean = true,
    supportsArchiveThread: Boolean = false,
    suppressArchivedThreadsDuringRefresh: Boolean = false,
    archiveThreadHandler: suspend (String, String) -> Boolean = { _, _ -> false },
  ): TestAgentSessionProviderDescriptor {
    return TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = supportedModes,
      cliAvailable = true,
      supportsPromptLaunch = supportsPromptLaunch,
      supportsArchiveThread = supportsArchiveThread,
      supportsUnarchiveThread = supportsUnarchiveThread,
      suppressArchivedThreadsDuringRefresh = suppressArchivedThreadsDuringRefresh,
      archiveThreadHandler = archiveThreadHandler,
      unarchiveThreadHandler = unarchiveThreadHandler,
    )
  }

  private fun sourceForActiveThreads(threads: List<AgentSessionThread>): ScriptedSessionSource {
    return ScriptedSessionSource(
      provider = AgentSessionProvider.from("codex"),
      listFromOpenProject = { path, _ -> if (path == PROJECT_PATH) threads.toList() else emptyList() },
    )
  }

  private fun featureWorktreeProjectEntry(): TestProjectCatalogEntry {
    return openTestProjectEntry(
      path = PROJECT_PATH,
      name = "Project A",
      worktrees = listOf(
        TestWorktreeCatalogEntry(
          path = WORKTREE_PATH,
          name = "Feature",
          branch = "feature",
          isOpen = true,
        ),
      ),
    )
  }

  private fun branchMismatchThread(): AgentSessionThread {
    return thread(id = "codex-feature", updatedAt = 200, provider = AgentSessionProvider.from("codex")).copy(originBranch = "main")
  }

  private fun hasFeatureWorktree(state: AgentSessionsState): Boolean {
    return state.projects.any { project ->
      project.worktrees.any { worktree ->
        worktree.path == WORKTREE_PATH && worktree.branch == "feature"
      }
    }
  }
}

private fun uiPreferencesStateWithProfiles(vararg profiles: AgentPromptLaunchProfile): AgentSessionUiPreferencesStateService {
  val launchProfileStateService = AgentSessionLaunchProfileStateService()
  launchProfileStateService.setLaunchProfiles(
    profiles = profiles.toList(),
    defaultProfileId = null,
  )
  return AgentSessionUiPreferencesStateService(launchProfileStateService)
}

private fun successfulPendingRebindReport(
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

private fun activeThreadIds(state: AgentSessionsState): List<String> {
  return state.projects.flatMap { project ->
    project.threads.map { thread -> thread.id } +
    project.worktrees.flatMap { worktree -> worktree.threads.map { thread -> thread.id } }
  }
}
