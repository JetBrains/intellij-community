// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupDataKeys
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.prompt.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchError
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.service.AgentSessionChatOpenExecutor
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionPromptLauncherBridge
import com.intellij.agent.workbench.sessions.service.resolveAgentSessionPathState
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon

@TestApplication
class AgentSessionPromptLauncherBridgeTest {
  @Test
  fun launchCreatesNewSessionForPromptRequest() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val request = promptLaunchRequest(projectPath = INVALID_PROMPT_PROJECT_PATH)

          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition {
            providerBridge.createCalls.get() == 1
          }

          assertThat(providerBridge.lastCreatePath.get()).isEqualTo(INVALID_PROMPT_PROJECT_PATH)
          assertThat(providerBridge.lastCreateMode.get()).isEqualTo(AgentSessionLaunchMode.STANDARD)
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()?.command)
            .containsExactly("test", "create", INVALID_PROMPT_PROJECT_PATH, AgentSessionLaunchMode.STANDARD.name)
          assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("composed:Refactor selected code")
          waitForCondition {
            chatOpenExecutor.openNewChatCalls.get() == 1
          }
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.normalizedPath).isEqualTo(INVALID_PROMPT_PROJECT_PATH)
          assertThat(openRequest.identity).startsWith("codex:new-")
          assertThat(openRequest.launchSpec.command)
            .containsExactly("test", "create", INVALID_PROMPT_PROJECT_PATH, AgentSessionLaunchMode.STANDARD.name)
          assertThat(openRequest.launchSpec.envVariables).isEmpty()
          assertThat(openRequest.startupLaunchSpecOverride?.command)
            .containsExactly(
              "test",
              "create",
              INVALID_PROMPT_PROJECT_PATH,
              AgentSessionLaunchMode.STANDARD.name,
              "--",
              "composed:Refactor selected code",
            )
          assertThat(openRequest.startupLaunchSpecOverride?.envVariables).isEmpty()
          assertThat(openRequest.initialComposedMessage).isEqualTo("composed:Refactor selected code")
          assertThat(openRequest.initialMessageToken).isNotNull()
        }
      }
    }
  }

  @Test
  fun successfulLaunchUpdatesPreferredProvider() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val uiPreferencesState = AgentSessionUiPreferencesStateService()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          uiPreferencesState = uiPreferencesState,
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService, uiPreferencesState::getLastUsedProvider)

          assertThat(bridge.preferredProvider()).isNull()

          val result = bridge.launch(promptLaunchRequest(provider = AgentSessionProvider.CLAUDE))

          assertThat(result.launched).isTrue()
          waitForCondition {
            uiPreferencesState.getLastUsedProvider() == AgentSessionProvider.CLAUDE
          }
          assertThat(bridge.preferredProvider()).isEqualTo(AgentSessionProvider.CLAUDE)
        }
      }
    }
  }

  @Test
  fun launchCarriesStartupOverrideEnvVariablesForNewSession() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPromptCommandEnvVariables = mapOf("DISABLE_AUTOUPDATER" to "1"),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val result = bridge.launch(promptLaunchRequest(projectPath = INVALID_PROMPT_PROJECT_PATH))

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition {
            chatOpenExecutor.openNewChatCalls.get() == 1
          }
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.startupLaunchSpecOverride?.envVariables)
            .containsExactlyEntriesOf(mapOf("DISABLE_AUTOUPDATER" to "1"))
        }
      }
    }
  }

  @Test
  fun launchFallsBackWhenStartupPromptCommandIsNotSupported() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPromptCommandSupported = false,
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val result = bridge.launch(promptLaunchRequest(projectPath = INVALID_PROMPT_PROJECT_PATH))

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition {
            providerBridge.createCalls.get() == 1 &&
            providerBridge.composeCalls.get() == 1 &&
            providerBridge.startupCommandCalls.get() == 1
          }
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("composed:Refactor selected code")
          waitForCondition {
            chatOpenExecutor.openNewChatCalls.get() == 1
          }
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.startupLaunchSpecOverride).isNull()
          assertThat(openRequest.initialComposedMessage).isEqualTo("composed:Refactor selected code")
          assertThat(openRequest.initialMessageToken).isNotNull()
        }
      }
    }
  }

  @Test
  fun launchFallsBackWhenBridgePolicyDisablesStartupPromptCommand() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPromptCommandPolicyEnabled = false,
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val request = promptLaunchRequest(projectPath = INVALID_PROMPT_PROJECT_PATH)

          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition {
            providerBridge.createCalls.get() == 1
          }
          waitForCondition {
            providerBridge.composeCalls.get() == 1 &&
            providerBridge.shouldUseStartupPromptCommandCalls.get() == 1
          }
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.shouldUseStartupPromptCommandCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastShouldUseStartupPromptCommandRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isZero()

          waitForCondition {
            chatOpenExecutor.openNewChatCalls.get() == 1
          }
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.startupLaunchSpecOverride).isNull()
          assertThat(openRequest.initialComposedMessage).isEqualTo("composed:Refactor selected code")
          assertThat(openRequest.initialMessageToken).isNotNull()
        }
      }
    }
  }

  @Test
  fun launchRoutesPromptToExistingThreadWhenTargetThreadIdIsProvided() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = {
            listOf(
              ScriptedSessionSource(
                provider = AgentSessionProvider.CODEX,
                listFromOpenProject = { path, _ ->
                  if (path == PROJECT_PATH) {
                    listOf(thread(id = "thread-existing", updatedAt = 200, provider = AgentSessionProvider.CODEX))
                  }
                  else {
                    emptyList()
                  }
                },
              )
            )
          },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          service.refresh()
          waitForCondition {
            val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
            project.hasLoaded && project.threads.any { thread -> thread.id == "thread-existing" }
          }

          val bridge = promptLauncherBridge(service, launchService)
          val request = promptLaunchRequest(targetThreadId = "thread-existing")

          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          assertThat(providerBridge.createCalls.get()).isZero()
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()?.command).containsExactly("test", "resume", "thread-existing")
          assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("composed:Refactor selected code")
          waitForCondition {
            chatOpenExecutor.openChatCalls.get() == 1
          }
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.normalizedPath).isEqualTo(PROJECT_PATH)
          assertThat(openRequest.thread.id).isEqualTo("thread-existing")
          assertThat(openRequest.subAgent).isNull()
          assertThat(openRequest.startupLaunchSpecOverride?.command)
            .containsExactly("test", "resume", "thread-existing", "--", "composed:Refactor selected code")
          assertThat(openRequest.startupLaunchSpecOverride?.envVariables).isEmpty()
          assertThat(openRequest.initialComposedMessage).isEqualTo("composed:Refactor selected code")
          assertThat(openRequest.initialMessageToken).isNotNull()
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun launchRoutesPromptToExistingThreadWhenThreadOpenIsInFlight() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val firstOpenStarted = CompletableDeferred<Unit>()
    val releaseFirstOpen = CompletableDeferred<Unit>()
    val chatOpenExecutor = RecordingChatOpenExecutor { _, callIndex ->
      if (callIndex == 1) {
        firstOpenStarted.complete(Unit)
        releaseFirstOpen.await()
      }
    }
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = {
            listOf(
              ScriptedSessionSource(
                provider = AgentSessionProvider.CODEX,
                listFromOpenProject = { path, _ ->
                  if (path == PROJECT_PATH) {
                    listOf(thread(id = "thread-existing", updatedAt = 200, provider = AgentSessionProvider.CODEX))
                  }
                  else {
                    emptyList()
                  }
                },
              )
            )
          },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          service.refresh()
          waitForCondition {
            val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
            project.hasLoaded && project.threads.any { thread -> thread.id == "thread-existing" }
          }

          val existingThread = checkNotNull(
            service.state.value.projects
              .firstOrNull { project -> project.path == PROJECT_PATH }
              ?.threads
              ?.firstOrNull { thread -> thread.id == "thread-existing" }
          )

          launchService.openChatThread(path = PROJECT_PATH, thread = existingThread)
          waitForCondition {
            firstOpenStarted.isCompleted
          }
          assertThat(chatOpenExecutor.openChatCalls.get()).isEqualTo(1)

          val bridge = promptLauncherBridge(service, launchService)
          val request = promptLaunchRequest(targetThreadId = "thread-existing")

          try {
            val result = bridge.launch(request)

            assertThat(result.launched).isTrue()
            assertThat(result.error).isNull()
            assertThat(providerBridge.createCalls.get()).isZero()
            assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
            assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
            assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
            assertThat(providerBridge.lastStartupBaseLaunchSpec.get()?.command).containsExactly("test", "resume", "thread-existing")
            assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("composed:Refactor selected code")

            waitForCondition {
              chatOpenExecutor.openChatCalls.get() == 2
            }

            assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
            assertThat(chatOpenExecutor.openChatRequests).hasSize(2)

            val initialOpen = chatOpenExecutor.openChatRequests[0]
            assertThat(initialOpen.startupLaunchSpecOverride).isNull()
            assertThat(initialOpen.initialComposedMessage).isNull()
            assertThat(initialOpen.initialMessageToken).isNull()

            val promptOpen = chatOpenExecutor.openChatRequests[1]
            assertThat(promptOpen.normalizedPath).isEqualTo(PROJECT_PATH)
            assertThat(promptOpen.thread.id).isEqualTo("thread-existing")
            assertThat(promptOpen.subAgent).isNull()
            assertThat(promptOpen.startupLaunchSpecOverride?.command)
              .containsExactly("test", "resume", "thread-existing", "--", "composed:Refactor selected code")
            assertThat(promptOpen.startupLaunchSpecOverride?.envVariables).isEmpty()
            assertThat(promptOpen.initialComposedMessage).isEqualTo("composed:Refactor selected code")
            assertThat(promptOpen.initialMessageToken).isNotNull()
          }
          finally {
            releaseFirstOpen.complete(Unit)
          }
        }
      }
    }
  }

  @Test
  fun launchFallsBackForExistingThreadWhenStartupPromptCommandIsNotSupported() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPromptCommandSupported = false,
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = {
            listOf(
              ScriptedSessionSource(
                provider = AgentSessionProvider.CODEX,
                listFromOpenProject = { path, _ ->
                  if (path == PROJECT_PATH) {
                    listOf(thread(id = "thread-existing", updatedAt = 200, provider = AgentSessionProvider.CODEX))
                  }
                  else {
                    emptyList()
                  }
                },
              )
            )
          },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          service.refresh()
          waitForCondition {
            val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
            project.hasLoaded && project.threads.any { thread -> thread.id == "thread-existing" }
          }

          val bridge = promptLauncherBridge(service, launchService)
          val request = promptLaunchRequest(targetThreadId = "thread-existing")

          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          assertThat(providerBridge.createCalls.get()).isZero()
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()?.command).containsExactly("test", "resume", "thread-existing")
          assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("composed:Refactor selected code")
          waitForCondition {
            chatOpenExecutor.openChatCalls.get() == 1
          }
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.normalizedPath).isEqualTo(PROJECT_PATH)
          assertThat(openRequest.thread.id).isEqualTo("thread-existing")
          assertThat(openRequest.subAgent).isNull()
          assertThat(openRequest.startupLaunchSpecOverride).isNull()
          assertThat(openRequest.initialComposedMessage).isEqualTo("composed:Refactor selected code")
          assertThat(openRequest.initialMessageToken).isNotNull()
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun launchReturnsThreadNotFoundWhenTargetThreadIsMissing() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = {
            listOf(
              ScriptedSessionSource(
                provider = AgentSessionProvider.CODEX,
                listFromOpenProject = { path, _ ->
                  if (path == PROJECT_PATH) {
                    listOf(thread(id = "thread-existing", updatedAt = 200, provider = AgentSessionProvider.CODEX))
                  }
                  else {
                    emptyList()
                  }
                },
              )
            )
          },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
          }

          val bridge = promptLauncherBridge(service, launchService)
          val result = bridge.launch(promptLaunchRequest(targetThreadId = "thread-missing"))

          assertThat(result.launched).isFalse()
          assertThat(result.error).isEqualTo(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND)
          assertThat(providerBridge.createCalls.get()).isZero()
          assertThat(providerBridge.composeCalls.get()).isZero()
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun launchReturnsProviderUnavailableWhenBridgeIsMissing() {
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(emptyList())
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { emptyList() },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val result = bridge.launch(promptLaunchRequest(provider = AgentSessionProvider.CODEX))

          assertThat(result.launched).isFalse()
          assertThat(result.error).isEqualTo(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun launchReturnsUnsupportedLaunchModeWhenProviderDoesNotSupportMode() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val result = bridge.launch(
            promptLaunchRequest(
              provider = AgentSessionProvider.CODEX,
              launchMode = AgentSessionLaunchMode.YOLO,
            )
          )

          assertThat(result.launched).isFalse()
          assertThat(result.error).isEqualTo(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE)
          assertThat(providerBridge.createCalls.get()).isZero()
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun observeExistingThreadsFiltersProviderThreadsFromSharedState() = runBlocking(Dispatchers.Default) {
    withServiceAndLaunch(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX))
              else emptyList()
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                listOf(
                  thread(id = "claude-1", updatedAt = 100, provider = AgentSessionProvider.CLAUDE),
                  thread(id = "claude-2", updatedAt = 300, provider = AgentSessionProvider.CLAUDE),
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
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service, launchService ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val bridge = promptLauncherBridge(service, launchService)
      val snapshot = bridge.observeExistingThreads(
        projectPath = PROJECT_PATH,
        provider = AgentSessionProvider.CLAUDE,
      ).first { it.hasLoaded }

      assertThat(snapshot.threads.map { thread -> thread.id })
        .containsExactly("claude-2", "claude-1")
      assertThat(snapshot.hasError).isFalse()
    }
  }

  @Test
  fun refreshExistingThreadsBootstrapsWhenPathIsMissing() = runBlocking(Dispatchers.Default) {
    val openLoads = AtomicInteger(0)
    withServiceAndLaunch(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                openLoads.incrementAndGet()
                listOf(thread(id = "claude-1", updatedAt = 100, provider = AgentSessionProvider.CLAUDE))
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
    ) { service, launchService ->
      assertThat(service.state.value.projects).isEmpty()

      val bridge = promptLauncherBridge(service, launchService)
      bridge.refreshExistingThreads(projectPath = PROJECT_PATH, provider = AgentSessionProvider.CLAUDE)

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.hasLoaded && project.threads.map { thread -> thread.id } == listOf("claude-1")
      }

      assertThat(openLoads.get()).isEqualTo(1)
    }
  }

  @Test
  fun refreshExistingThreadsUsesProviderScopedRefreshForLoadedPath() = runBlocking(Dispatchers.Default) {
    val codexClosedLoads = AtomicInteger(0)
    val claudeClosedLoads = AtomicInteger(0)
    var claudeClosedThreadId = "claude-closed-1"

    withServiceAndLaunch(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-open", updatedAt = 300, provider = AgentSessionProvider.CODEX))
              else emptyList()
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                codexClosedLoads.incrementAndGet()
                listOf(thread(id = "codex-closed", updatedAt = 250, provider = AgentSessionProvider.CODEX))
              }
              else {
                emptyList()
              }
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-open", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
              else emptyList()
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                claudeClosedLoads.incrementAndGet()
                listOf(thread(id = claudeClosedThreadId, updatedAt = 400, provider = AgentSessionProvider.CLAUDE))
              }
              else {
                emptyList()
              }
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service, launchService ->
      service.refresh()
      waitForCondition {
        val ids = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { thread -> thread.id } ?: return@waitForCondition false
        ids.contains("codex-open") && ids.contains("claude-open")
      }

      claudeClosedThreadId = "claude-closed-2"
      val bridge = promptLauncherBridge(service, launchService)
      bridge.refreshExistingThreads(projectPath = PROJECT_PATH, provider = AgentSessionProvider.CLAUDE)

      waitForCondition {
        val ids = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { thread -> thread.id } ?: return@waitForCondition false
        ids.contains("codex-open") && ids.contains("claude-closed-2")
      }

      assertThat(claudeClosedLoads.get()).isEqualTo(1)
      assertThat(codexClosedLoads.get()).isEqualTo(0)
    }
  }

  @Test
  fun observeExistingThreadsMarksProviderWarningAsError() = runBlocking(Dispatchers.Default) {
    withServiceAndLaunch(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX))
              else emptyList()
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                throw IllegalStateException("Claude backend failed")
              }
              emptyList()
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service, launchService ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val bridge = promptLauncherBridge(service, launchService)
      val claudeSnapshot = bridge.observeExistingThreads(
        projectPath = PROJECT_PATH,
        provider = AgentSessionProvider.CLAUDE,
      ).first { it.hasLoaded && !it.isLoading }
      val codexSnapshot = bridge.observeExistingThreads(
        projectPath = PROJECT_PATH,
        provider = AgentSessionProvider.CODEX,
      ).first { it.hasLoaded && !it.isLoading }

      assertThat(claudeSnapshot.threads).isEmpty()
      assertThat(claudeSnapshot.hasError).isTrue()
      assertThat(codexSnapshot.threads.map { thread -> thread.id }).containsExactly("codex-1")
      assertThat(codexSnapshot.hasError).isFalse()
    }
  }

  @Test
  fun dedicatedInvocationPrefersSelectedTreePathForWorkingProjectResolution() {
    val dedicatedProject = projectProxy(
      name = "Agent Dedicated Frame",
      basePath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath(),
    )
    val selectedTreePath = "/work/project-from-tree"
    val invocation = invocationData(
      project = dedicatedProject,
      treeContext = AgentSessionsTreePopupActionContext(
        project = dedicatedProject,
        nodeId = SessionTreeId.Thread(
          projectPath = selectedTreePath,
          provider = AgentSessionProvider.CODEX,
          threadId = "thread-1",
        ),
        node = SessionTreeNode.Thread(
          project = AgentProjectSessions(path = selectedTreePath, name = "Project From Tree", isOpen = true),
          thread = thread(id = "thread-1", updatedAt = 100, provider = AgentSessionProvider.CODEX),
        ),
        archiveTargets = emptyList(),
      ),
    )
    val bridge = AgentSessionPromptLauncherBridge {
      throw UnsupportedOperationException("Not used in path resolution test")
    }

    val candidates = bridge.listWorkingProjectPathCandidates(invocation)

    assertThat(candidates).isNotEmpty()
    assertThat(candidates.first().path).isEqualTo(selectedTreePath)
    assertThat(bridge.resolveWorkingProjectPath(invocation)).isEqualTo(selectedTreePath)
  }

  @Test
  fun nonDedicatedInvocationUsesCurrentProjectPathForWorkingProjectResolution() {
    val currentProjectPath = "/work/current-project"
    val currentProject = projectProxy(name = "Current Project", basePath = currentProjectPath)
    val invocation = invocationData(
      project = currentProject,
      treeContext = AgentSessionsTreePopupActionContext(
        project = currentProject,
        nodeId = SessionTreeId.Project(path = "/work/project-from-tree"),
        node = SessionTreeNode.Project(
          AgentProjectSessions(path = "/work/project-from-tree", name = "Project From Tree", isOpen = true),
        ),
        archiveTargets = emptyList(),
      ),
    )
    val bridge = AgentSessionPromptLauncherBridge {
      throw UnsupportedOperationException("Not used in path resolution test")
    }

    val candidates = bridge.listWorkingProjectPathCandidates(invocation)

    assertThat(candidates).containsExactly(
      AgentPromptProjectPathCandidate(
        path = currentProjectPath,
        displayName = "Current Project",
      )
    )
    assertThat(bridge.resolveWorkingProjectPath(invocation)).isEqualTo(currentProjectPath)
  }

  @Test
  fun dedicatedWorkingProjectCandidatesNeverContainDedicatedFramePath() {
    val dedicatedPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
    val dedicatedProject = projectProxy(name = "Agent Dedicated Frame", basePath = dedicatedPath)
    val invocation = invocationData(
      project = dedicatedProject,
      treeContext = AgentSessionsTreePopupActionContext(
        project = dedicatedProject,
        nodeId = SessionTreeId.Project(path = dedicatedPath),
        node = SessionTreeNode.Project(
          AgentProjectSessions(path = dedicatedPath, name = "Dedicated", isOpen = true),
        ),
        archiveTargets = emptyList(),
      ),
    )
    val bridge = AgentSessionPromptLauncherBridge {
      throw UnsupportedOperationException("Not used in path resolution test")
    }

    val candidates = bridge.listWorkingProjectPathCandidates(invocation)

    assertThat(candidates.none { candidate -> candidate.path == dedicatedPath }).isTrue()
  }
}

private fun promptLauncherBridge(
  service: AgentSessionStateSyncTestFacade,
  launchService: AgentSessionLaunchService,
  preferredProviderProvider: () -> AgentSessionProvider? = { null },
): AgentSessionPromptLauncherBridge {
  return AgentSessionPromptLauncherBridge(
    launchPromptRequest = { request -> launchService.launchPromptRequest(request) },
    stateFlowProvider = { service.state },
    pathStateResolver = ::resolveAgentSessionPathState,
    refreshCatalogAndLoadNewlyOpened = { service.refreshCatalogAndLoadNewlyOpened() },
    refreshProviderForPath = { path, provider -> service.refreshProviderForPath(path = path, provider = provider) },
    preferredProviderProvider = preferredProviderProvider,
  )
}

private const val INVALID_PROMPT_PROJECT_PATH: String = "invalid\u0000project"

private class RecordingChatOpenExecutor(
  private val onOpenChat: (suspend (OpenChatRequest, Int) -> Unit)? = null,
) : AgentSessionChatOpenExecutor {
  val openChatCalls: AtomicInteger = AtomicInteger(0)
  val openNewChatCalls: AtomicInteger = AtomicInteger(0)
  val openChatRequests: CopyOnWriteArrayList<OpenChatRequest> = CopyOnWriteArrayList()
  val lastOpenChatRequest: AtomicReference<OpenChatRequest?> = AtomicReference(null)
  val lastOpenNewChatRequest: AtomicReference<OpenNewChatRequest?> = AtomicReference(null)

  override suspend fun openChat(
    normalizedPath: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    launchSpecOverride: AgentSessionTerminalLaunchSpec?,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
  ) {
    val request = OpenChatRequest(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride,
      initialComposedMessage = initialMessageDispatchPlan.initialComposedMessage,
      initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
      initialMessageTimeoutPolicy = initialMessageDispatchPlan.initialMessageTimeoutPolicy,
    )
    val callIndex = openChatCalls.incrementAndGet()
    openChatRequests.add(request)
    lastOpenChatRequest.set(request)
    onOpenChat?.invoke(request, callIndex)
  }

  override suspend fun openNewChat(
    normalizedPath: String,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
    preferredDedicatedFrame: Boolean?,
  ) {
    openNewChatCalls.incrementAndGet()
    lastOpenNewChatRequest.set(
      OpenNewChatRequest(
        normalizedPath = normalizedPath,
        identity = identity,
        launchSpec = launchSpec,
        startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride,
        initialComposedMessage = initialMessageDispatchPlan.initialComposedMessage,
        initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
        initialMessageTimeoutPolicy = initialMessageDispatchPlan.initialMessageTimeoutPolicy,
        preferredDedicatedFrame = preferredDedicatedFrame,
      )
    )
  }
}

private data class OpenChatRequest(
  @JvmField val normalizedPath: String,
  @JvmField val thread: AgentSessionThread,
  @JvmField val subAgent: AgentSubAgent?,
  @JvmField val launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val initialComposedMessage: String?,
  @JvmField val initialMessageToken: String?,
  @JvmField val initialMessageTimeoutPolicy: AgentInitialMessageTimeoutPolicy,
)

private data class OpenNewChatRequest(
  @JvmField val normalizedPath: String,
  @JvmField val identity: String,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val initialComposedMessage: String?,
  @JvmField val initialMessageToken: String?,
  @JvmField val initialMessageTimeoutPolicy: AgentInitialMessageTimeoutPolicy,
  @JvmField val preferredDedicatedFrame: Boolean?,
)

private fun promptLaunchRequest(
  provider: AgentSessionProvider = AgentSessionProvider.CODEX,
  launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  projectPath: String = PROJECT_PATH,
  targetThreadId: String? = null,
): AgentPromptLaunchRequest {
  return AgentPromptLaunchRequest(
    provider = provider,
    projectPath = projectPath,
    launchMode = launchMode,
    initialMessageRequest = AgentPromptInitialMessageRequest(
      prompt = "Refactor selected code",
      contextItems = listOf(
        AgentPromptContextItem(
          rendererId = "project",
          title = "Project",
          body = "project-a",
          source = "test",
        )
      ),
    ),
    targetThreadId = targetThreadId,
    preferredDedicatedFrame = null,
  )
}

private fun invocationData(
  project: Project,
  treeContext: AgentSessionsTreePopupActionContext?,
): AgentPromptInvocationData {
  val dataContextBuilder = SimpleDataContext.builder()
  if (treeContext != null) {
    dataContextBuilder.add(AgentSessionsTreePopupDataKeys.CONTEXT, treeContext)
  }
  val dataContext = dataContextBuilder.build()
  return AgentPromptInvocationData(
    project = project,
    actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
    actionText = "Ask Agent with Context",
    actionPlace = "test",
    invokedAtMs = 0,
    attributes = mapOf(AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to dataContext),
  )
}

private fun projectProxy(
  name: String,
  basePath: String?,
): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getName" -> name
      "getBasePath" -> basePath
      "isOpen" -> true
      "isDisposed" -> false
      "toString" -> "MockProject($name)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> null
    }
  }
  return Proxy.newProxyInstance(
    ProjectManager::class.java.classLoader,
    arrayOf(Project::class.java),
    handler,
  ) as Project
}

private class RecordingPromptLaunchProviderBridge(
  override val provider: AgentSessionProvider,
  private val supportedModes: Set<AgentSessionLaunchMode>,
  private val startupPromptCommandSupported: Boolean = true,
  private val startupPromptCommandPolicyEnabled: Boolean = true,
  private val timeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
  private val startupPromptCommandEnvVariables: Map<String, String> = emptyMap(),
) : AgentSessionProviderBridge {
  val createCalls: AtomicInteger = AtomicInteger(0)
  val composeCalls: AtomicInteger = AtomicInteger(0)
  val startupCommandCalls: AtomicInteger = AtomicInteger(0)
  val shouldUseStartupPromptCommandCalls: AtomicInteger = AtomicInteger(0)
  val lastCreatePath: AtomicReference<String?> = AtomicReference(null)
  val lastCreateMode: AtomicReference<AgentSessionLaunchMode?> = AtomicReference(null)
  val lastComposeRequest: AtomicReference<AgentPromptInitialMessageRequest?> = AtomicReference(null)
  val lastStartupBaseLaunchSpec: AtomicReference<AgentSessionTerminalLaunchSpec?> = AtomicReference(null)
  val lastStartupPrompt: AtomicReference<String?> = AtomicReference(null)
  val lastShouldUseStartupPromptCommandRequest: AtomicReference<AgentPromptInitialMessageRequest?> = AtomicReference(null)

  override val displayNameKey: String
    get() = "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex"

  override val icon: Icon
    get() = if (provider == AgentSessionProvider.CLAUDE) AgentWorkbenchCommonIcons.Claude_14x14 else AgentWorkbenchCommonIcons.Codex_14x14

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = supportedModes

  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@RecordingPromptLaunchProviderBridge.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override fun isCliAvailable(): Boolean = true

  override fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "resume", sessionId))
  }

  override fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "new", mode.name))
  }

  override fun buildNewEntryLaunchSpec(): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test"))
  }

  override fun buildLaunchSpecWithInitialPrompt(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    prompt: String,
  ): AgentSessionTerminalLaunchSpec? {
    startupCommandCalls.incrementAndGet()
    lastStartupBaseLaunchSpec.set(baseLaunchSpec)
    lastStartupPrompt.set(prompt)
    if (!startupPromptCommandSupported) {
      return null
    }
    return baseLaunchSpec.copy(
      command = baseLaunchSpec.command + listOf("--", prompt),
      envVariables = baseLaunchSpec.envVariables + startupPromptCommandEnvVariables,
    )
  }

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    createCalls.incrementAndGet()
    lastCreatePath.set(path)
    lastCreateMode.set(mode)
    return AgentSessionLaunchSpec(
      sessionId = null,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("test", "create", path, mode.name)),
    )
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    composeCalls.incrementAndGet()
    lastComposeRequest.set(request)
    shouldUseStartupPromptCommandCalls.incrementAndGet()
    lastShouldUseStartupPromptCommandRequest.set(request)
    return AgentInitialMessagePlan(
      message = "composed:${request.prompt.trim()}",
      startupPolicy = if (startupPromptCommandPolicyEnabled) {
        AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND
      }
      else {
        AgentInitialMessageStartupPolicy.POST_START_ONLY
      },
      timeoutPolicy = timeoutPolicy,
    )
  }
}
