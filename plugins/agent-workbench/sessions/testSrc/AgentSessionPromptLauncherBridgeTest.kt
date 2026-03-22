// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.prompt.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.sessions.core.prompt.AGENT_PROMPT_PROJECT_PATH_CONTEXT_DATA_KEY
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchError
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptProjectPathContext
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchPromptLaunchResultKind
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTargetKind
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetryEvent
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetryProvider
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.frame.OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionPromptLauncherBridge
import com.intellij.agent.workbench.sessions.service.resolveAgentSessionPathState
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
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
    AgentSessionProviders.withRegistryForTest(
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
          val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
          val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

          try {
            val result = bridge.launch(request)

            assertThat(result.launched).isTrue()
            assertThat(result.error).isNull()
            waitForCondition {
              providerBridge.createCalls.get() == 1 &&
              providerBridge.composeCalls.get() == 1 &&
              providerBridge.startupCommandCalls.get() == 1 &&
              chatOpenExecutor.openNewChatCalls.get() == 1 &&
              telemetryEvents.any { it.id == AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID }
            }

            assertThat(providerBridge.lastCreatePath.get()).isEqualTo(INVALID_PROMPT_PROJECT_PATH)
            assertThat(providerBridge.lastCreateMode.get()).isEqualTo(AgentSessionLaunchMode.STANDARD)
            assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
            assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
            assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
            assertThat(providerBridge.lastStartupBaseLaunchSpec.get()?.command)
              .containsExactly("test", "create", INVALID_PROMPT_PROJECT_PATH, AgentSessionLaunchMode.STANDARD.name)
            assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("composed:Refactor selected code")
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
            assertThat(telemetryEvents).contains(
              AgentWorkbenchTelemetryEvent(
                id = AgentWorkbenchTelemetry.THREAD_CREATE_REQUESTED_EVENT_ID,
                entryPoint = AgentWorkbenchEntryPoint.PROMPT,
                provider = telemetryEvents.first { it.id == AgentWorkbenchTelemetry.THREAD_CREATE_REQUESTED_EVENT_ID }.provider,
                launchMode = AgentSessionLaunchMode.STANDARD,
                targetKind = AgentWorkbenchTargetKind.NEW_THREAD,
              )
            )
            assertThat(telemetryEvents).contains(
              AgentWorkbenchTelemetryEvent(
                id = AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID,
                provider = telemetryEvents.first { it.id == AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID }.provider,
                launchMode = AgentSessionLaunchMode.STANDARD,
                targetKind = AgentWorkbenchTargetKind.NEW_THREAD,
                launchResult = AgentWorkbenchPromptLaunchResultKind.SUCCESS,
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
  fun launchReportsPromptFailureTelemetryWhenTargetThreadIsMissing() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    AgentSessionProviders.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
          val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

          try {
            val result = bridge.launch(promptLaunchRequest(targetThreadId = "missing-thread"))

            assertThat(result.launched).isFalse()
            assertThat(result.error).isEqualTo(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND)
            assertThat(telemetryEvents).contains(
              AgentWorkbenchTelemetryEvent(
                id = AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID,
                provider = telemetryEvents.single().provider,
                launchMode = AgentSessionLaunchMode.STANDARD,
                targetKind = AgentWorkbenchTargetKind.THREAD,
                launchResult = AgentWorkbenchPromptLaunchResultKind.TARGET_THREAD_NOT_FOUND,
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
  fun launchReportsDroppedDuplicateTelemetryWhenNewSessionRequestIsAlreadyInFlight() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val releaseFirstOpen = CompletableDeferred<Unit>()
    val chatOpenExecutor = RecordingChatOpenExecutor(
      onOpenNewChat = { _, callIndex ->
        if (callIndex == 1) {
          releaseFirstOpen.await()
        }
      }
    )
    withOpenInNonDedicatedFrameSettingForTest {
      AgentSessionProviders.withRegistryForTest(
        InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
      ) {
        runBlocking(Dispatchers.Default) {
          withServiceAndLaunch(
            sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
            projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
            chatOpenExecutor = chatOpenExecutor,
          ) { service, launchService ->
            val bridge = promptLauncherBridge(service, launchService)
            val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
            val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

            try {
              val firstResult = bridge.launch(promptLaunchRequest(projectPath = INVALID_PROMPT_PROJECT_PATH))
              waitForCondition {
                chatOpenExecutor.openNewChatCalls.get() == 1
              }

              val secondResult = bridge.launch(promptLaunchRequest(projectPath = INVALID_PROMPT_PROJECT_PATH))

              assertThat(firstResult.launched).isTrue()
              assertThat(secondResult.launched).isTrue()
              waitForCondition {
                telemetryEvents.any {
                  it.id == AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID &&
                  it.launchResult == AgentWorkbenchPromptLaunchResultKind.DROPPED_DUPLICATE
                }
              }

              releaseFirstOpen.complete(Unit)
              waitForCondition {
                telemetryEvents.count { it.id == AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID } >= 2
              }
              assertThat(telemetryEvents).contains(
                AgentWorkbenchTelemetryEvent(
                  id = AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID,
                  provider = AgentWorkbenchTelemetryProvider.CODEX,
                  launchMode = AgentSessionLaunchMode.STANDARD,
                  targetKind = AgentWorkbenchTargetKind.NEW_THREAD,
                  launchResult = AgentWorkbenchPromptLaunchResultKind.DROPPED_DUPLICATE,
                )
              )
            }
            finally {
              releaseFirstOpen.complete(Unit)
              token.finish()
            }
          }
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
    AgentSessionProviders.withRegistryForTest(
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
    AgentSessionProviders.withRegistryForTest(
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
  fun launchAugmentsNewSessionAndStartupOverrideFromAugmenter() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPromptCommandEnvVariables = mapOf("DISABLE_AUTOUPDATER" to "1"),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviders.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withTestLaunchSpecAugmenter {
          withServiceAndLaunch(
            sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
            projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
            chatOpenExecutor = chatOpenExecutor,
          ) { service, launchService ->
            val bridge = promptLauncherBridge(service, launchService)
            val result = bridge.launch(promptLaunchRequest(projectPath = PROJECT_PATH))

            assertThat(result.launched).isTrue()
            assertThat(result.error).isNull()
            waitForCondition {
              chatOpenExecutor.openNewChatCalls.get() == 1
            }

            val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
            assertThat(openRequest.launchSpec.envVariables)
              .containsEntry(AGENT_WORKBENCH_TEST_ENV_NAME, AGENT_WORKBENCH_TEST_ENV_VALUE)
              .containsEntry("PATH", AGENT_WORKBENCH_TEST_PATH_PREPEND)
            assertThat(openRequest.startupLaunchSpecOverride?.envVariables)
              .containsEntry("DISABLE_AUTOUPDATER", "1")
              .containsEntry(AGENT_WORKBENCH_TEST_ENV_NAME, AGENT_WORKBENCH_TEST_ENV_VALUE)
              .containsEntry("PATH", AGENT_WORKBENCH_TEST_PATH_PREPEND)
          }
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
    AgentSessionProviders.withRegistryForTest(
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
    AgentSessionProviders.withRegistryForTest(
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
  fun launchKeepsManualCodexPlanPromptOnPostStartDispatchWhenTryStartupCommandIsEnabled() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPolicyOverride = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
      timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      composedMessageBuilder = { request -> request.prompt.trim() },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviders.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val baseRequest = promptLaunchRequest(projectPath = INVALID_PROMPT_PROJECT_PATH)
          val request = baseRequest.copy(
            initialMessageRequest = baseRequest.initialMessageRequest.copy(prompt = "/plan Refactor selected code"),
          )
          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition {
            providerBridge.composeCalls.get() == 1 &&
            chatOpenExecutor.openNewChatCalls.get() == 1
          }

          assertThat(providerBridge.createCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastCreatePath.get()).isEqualTo(INVALID_PROMPT_PROJECT_PATH)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isZero()
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()).isNull()
          assertThat(providerBridge.lastStartupPrompt.get()).isNull()
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.launchSpec.command)
            .containsExactly("test", "create", INVALID_PROMPT_PROJECT_PATH, AgentSessionLaunchMode.STANDARD.name)
          assertThat(openRequest.startupLaunchSpecOverride).isNull()
          assertThat(openRequest.initialComposedMessage).isNull()
          assertThat(openRequest.postStartDispatchSteps).containsExactly(
            AgentInitialMessageDispatchStep(
              text = "/plan",
              timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
              completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
            ),
            AgentInitialMessageDispatchStep(
              text = "Refactor selected code",
              timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
            ),
          )
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
    AgentSessionProviders.withRegistryForTest(
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
          waitForCondition {
            providerBridge.composeCalls.get() == 1 &&
            chatOpenExecutor.openChatCalls.get() == 1
          }
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isZero()
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()).isNull()
          assertThat(providerBridge.lastStartupPrompt.get()).isNull()
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.normalizedPath).isEqualTo(PROJECT_PATH)
          assertThat(openRequest.thread.id).isEqualTo("thread-existing")
          assertThat(openRequest.subAgent).isNull()
          assertThat(openRequest.startupLaunchSpecOverride).isNull()
          assertThat(openRequest.initialComposedMessage).isEqualTo("composed:Refactor selected code")
          assertThat(openRequest.postStartDispatchSteps).containsExactly(
            AgentInitialMessageDispatchStep(text = "composed:Refactor selected code"),
          )
          assertThat(openRequest.initialMessageToken).isNotNull()
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun launchRoutesManualCodexPlanPromptToExistingThreadAsPostStartDispatchWhenTryStartupCommandIsEnabled() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPolicyOverride = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
      timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      composedMessageBuilder = { request -> request.prompt.trim() },
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviders.withRegistryForTest(
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
          val baseRequest = promptLaunchRequest(targetThreadId = "thread-existing")
          val request = baseRequest.copy(
            initialMessageRequest = baseRequest.initialMessageRequest.copy(prompt = "/plan Refactor selected code"),
          )

          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          assertThat(providerBridge.createCalls.get()).isZero()
          waitForCondition {
            providerBridge.composeCalls.get() == 1 &&
            chatOpenExecutor.openChatCalls.get() == 1
          }

          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isZero()
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()).isNull()
          assertThat(providerBridge.lastStartupPrompt.get()).isNull()
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.normalizedPath).isEqualTo(PROJECT_PATH)
          assertThat(openRequest.thread.id).isEqualTo("thread-existing")
          assertThat(openRequest.subAgent).isNull()
          assertThat(openRequest.startupLaunchSpecOverride).isNull()
          assertThat(openRequest.initialComposedMessage).isNull()
          assertThat(openRequest.postStartDispatchSteps).containsExactly(
            AgentInitialMessageDispatchStep(
              text = "/plan",
              timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
              completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
            ),
            AgentInitialMessageDispatchStep(
              text = "Refactor selected code",
              timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
            ),
          )
          assertThat(openRequest.initialMessageToken).isNotNull()
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
    val chatOpenExecutor = RecordingChatOpenExecutor(
      onOpenChat = { _, callIndex ->
        if (callIndex == 1) {
          firstOpenStarted.complete(Unit)
          releaseFirstOpen.await()
        }
      }
    )
    withOpenInNonDedicatedFrameSettingForTest {
      AgentSessionProviders.withRegistryForTest(
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

            launchService.openChatThread(path = PROJECT_PATH, thread = existingThread, entryPoint = AgentWorkbenchEntryPoint.TREE_ROW)
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

              releaseFirstOpen.complete(Unit)
              waitForCondition(timeoutMs = 5_000) {
                chatOpenExecutor.openChatCalls.get() == 2
              }

              assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
              assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
              assertThat(providerBridge.startupCommandCalls.get()).isZero()
              assertThat(providerBridge.lastStartupBaseLaunchSpec.get()).isNull()
              assertThat(providerBridge.lastStartupPrompt.get()).isNull()

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
              assertThat(promptOpen.startupLaunchSpecOverride).isNull()
              assertThat(promptOpen.initialComposedMessage).isEqualTo("composed:Refactor selected code")
              assertThat(promptOpen.postStartDispatchSteps).containsExactly(
                AgentInitialMessageDispatchStep(text = "composed:Refactor selected code"),
              )
              assertThat(promptOpen.initialMessageToken).isNotNull()
            }
            finally {
              releaseFirstOpen.complete(Unit)
            }
          }
        }
      }
    }
  }

  @Test
  fun launchUsesPostStartDispatchForExistingThreadWithoutEvaluatingStartupPromptCommandSupport() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPromptCommandSupported = false,
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviders.withRegistryForTest(
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
          waitForCondition {
            providerBridge.composeCalls.get() == 1 &&
            chatOpenExecutor.openChatCalls.get() == 1
          }
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isZero()
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()).isNull()
          assertThat(providerBridge.lastStartupPrompt.get()).isNull()
          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.normalizedPath).isEqualTo(PROJECT_PATH)
          assertThat(openRequest.thread.id).isEqualTo("thread-existing")
          assertThat(openRequest.subAgent).isNull()
          assertThat(openRequest.startupLaunchSpecOverride).isNull()
          assertThat(openRequest.initialComposedMessage).isEqualTo("composed:Refactor selected code")
          assertThat(openRequest.postStartDispatchSteps).containsExactly(
            AgentInitialMessageDispatchStep(text = "composed:Refactor selected code"),
          )
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
    AgentSessionProviders.withRegistryForTest(
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
    AgentSessionProviders.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(emptyList())
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { emptyList() },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
          val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

          try {
            val result = bridge.launch(promptLaunchRequest(provider = AgentSessionProvider.CODEX))

            assertThat(result.launched).isFalse()
            assertThat(result.error).isEqualTo(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
            assertThat(chatOpenExecutor.openChatCalls.get()).isZero()
            assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
            assertThat(telemetryEvents).contains(
              AgentWorkbenchTelemetryEvent(
                id = AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID,
                provider = AgentWorkbenchTelemetryProvider.CODEX,
                launchMode = AgentSessionLaunchMode.STANDARD,
                targetKind = AgentWorkbenchTargetKind.NEW_THREAD,
                launchResult = AgentWorkbenchPromptLaunchResultKind.PROVIDER_UNAVAILABLE,
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
  fun launchReturnsUnsupportedLaunchModeWhenProviderDoesNotSupportMode() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    AgentSessionProviders.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          val bridge = promptLauncherBridge(service, launchService)
          val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
          val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

          try {
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
            assertThat(telemetryEvents).contains(
              AgentWorkbenchTelemetryEvent(
                id = AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID,
                provider = AgentWorkbenchTelemetryProvider.CODEX,
                launchMode = AgentSessionLaunchMode.YOLO,
                targetKind = AgentWorkbenchTargetKind.NEW_THREAD,
                launchResult = AgentWorkbenchPromptLaunchResultKind.UNSUPPORTED_LAUNCH_MODE,
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
        val ids = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { thread -> thread.id }
                  ?: return@waitForCondition false
        ids.contains("codex-open") && ids.contains("claude-open")
      }

      claudeClosedThreadId = "claude-closed-2"
      val bridge = promptLauncherBridge(service, launchService)
      bridge.refreshExistingThreads(projectPath = PROJECT_PATH, provider = AgentSessionProvider.CLAUDE)

      waitForCondition {
        val ids = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { thread -> thread.id }
                  ?: return@waitForCondition false
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
      projectPathContext = AgentPromptProjectPathContext(
        path = selectedTreePath,
        displayName = "Project From Tree",
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
      projectPathContext = AgentPromptProjectPathContext(
        path = "/work/project-from-tree",
        displayName = "Project From Tree",
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
  fun dedicatedInvocationResolvesSourceProjectFromSelectedTreePath() {
    val dedicatedProject = projectProxy(
      name = "Agent Dedicated Frame",
      basePath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath(),
    )
    val selectedTreePath = "/work/project-from-tree"
    val sourceProject = projectProxy(name = "Project From Tree", basePath = selectedTreePath)
    val invocation = invocationData(
      project = dedicatedProject,
      projectPathContext = AgentPromptProjectPathContext(
        path = selectedTreePath,
        displayName = "Project From Tree",
      ),
    )
    val resolvedPaths = CopyOnWriteArrayList<String>()
    val bridge = AgentSessionPromptLauncherBridge(
      launchPromptRequest = {
        throw UnsupportedOperationException("Not used in source-project resolution test")
      },
      stateFlowProvider = {
        error("stateFlowProvider is unavailable in this test setup")
      },
      pathStateResolver = ::resolveAgentSessionPathState,
      refreshCatalogAndLoadNewlyOpened = {},
      refreshProviderForPath = { _, _ -> },
      preferredProviderProvider = { null },
      sourceProjectResolver = { path ->
        resolvedPaths.add(path)
        sourceProject.takeIf { path == selectedTreePath }
      },
    )

    assertThat(bridge.resolveSourceProject(invocation)).isSameAs(sourceProject)
    assertThat(resolvedPaths).containsExactly(selectedTreePath)
  }

  @Test
  fun nonDedicatedInvocationUsesCurrentProjectAsSourceProject() {
    val currentProject = projectProxy(name = "Current Project", basePath = "/work/current-project")
    val invocation = invocationData(
      project = currentProject,
      projectPathContext = null,
    )
    val bridge = AgentSessionPromptLauncherBridge {
      throw UnsupportedOperationException("Not used in source-project resolution test")
    }

    assertThat(bridge.resolveSourceProject(invocation)).isSameAs(currentProject)
  }

  @Test
  fun dedicatedWorkingProjectCandidatesNeverContainDedicatedFramePath() {
    val dedicatedPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
    val dedicatedProject = projectProxy(name = "Agent Dedicated Frame", basePath = dedicatedPath)
    val invocation = invocationData(
      project = dedicatedProject,
      projectPathContext = AgentPromptProjectPathContext(
        path = dedicatedPath,
        displayName = "Dedicated",
      ),
    )
    val bridge = AgentSessionPromptLauncherBridge {
      throw UnsupportedOperationException("Not used in path resolution test")
    }

    val candidates = bridge.listWorkingProjectPathCandidates(invocation)

    assertThat(candidates.none { candidate -> candidate.path == dedicatedPath }).isTrue()
  }

  @Test
  fun loadAndSaveProviderPreferencesDelegatesToLambdas() {
    var stored = AgentPromptLauncherBridge.ProviderPreferences()
    val bridge = AgentSessionPromptLauncherBridge(
      launchPromptRequest = { error("not used") },
      stateFlowProvider = { error("not used") },
      pathStateResolver = ::resolveAgentSessionPathState,
      refreshCatalogAndLoadNewlyOpened = {},
      refreshProviderForPath = { _, _ -> },
      preferredProviderProvider = { null },
      providerPreferencesLoader = { stored },
      providerPreferencesSaver = { prefs -> stored = prefs },
    )

    assertThat(bridge.loadProviderPreferences()).isEqualTo(AgentPromptLauncherBridge.ProviderPreferences())

    val prefs = AgentPromptLauncherBridge.ProviderPreferences(
      providerId = AgentSessionProvider.CODEX.value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      providerOptionsByProviderId = mapOf("codex" to setOf("plan_mode")),
    )
    bridge.saveProviderPreferences(prefs)

    assertThat(stored).isEqualTo(prefs)
    assertThat(bridge.loadProviderPreferences()).isEqualTo(prefs)
  }
}

private fun <T> withOpenInNonDedicatedFrameSettingForTest(action: () -> T): T {
  val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
  val disposable = Disposer.newDisposable()
  registerDedicatedFrameSettingForTest(disposable)
  advancedSettings.setSetting(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID, false, disposable)
  try {
    AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)
    return action()
  }
  finally {
    Disposer.dispose(disposable)
  }
}

private fun promptLauncherBridge(
  service: AgentSessionStateSyncTestFacade,
  launchService: AgentSessionLaunchService,
  preferredProviderProvider: () -> AgentSessionProvider? = { null },
  providerPreferencesLoader: () -> AgentPromptLauncherBridge.ProviderPreferences = { AgentPromptLauncherBridge.ProviderPreferences() },
  providerPreferencesSaver: (AgentPromptLauncherBridge.ProviderPreferences) -> Unit = {},
): AgentSessionPromptLauncherBridge {
  return AgentSessionPromptLauncherBridge(
    launchPromptRequest = { request -> launchService.launchPromptRequest(request) },
    stateFlowProvider = { service.state },
    pathStateResolver = ::resolveAgentSessionPathState,
    refreshCatalogAndLoadNewlyOpened = { service.refreshCatalogAndLoadNewlyOpened() },
    refreshProviderForPath = { path, provider -> service.refreshProviderForPath(path = path, provider = provider) },
    preferredProviderProvider = preferredProviderProvider,
    providerPreferencesLoader = providerPreferencesLoader,
    providerPreferencesSaver = providerPreferencesSaver,
  )
}

private const val INVALID_PROMPT_PROJECT_PATH: String = "invalid\u0000project"

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
  projectPathContext: AgentPromptProjectPathContext?,
): AgentPromptInvocationData {
  val dataContextBuilder = SimpleDataContext.builder()
  if (projectPathContext != null) {
    dataContextBuilder.add(AGENT_PROMPT_PROJECT_PATH_CONTEXT_DATA_KEY, projectPathContext)
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
  private val startupPolicyOverride: AgentInitialMessageStartupPolicy? = null,
  private val timeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
  private val startupPromptCommandEnvVariables: Map<String, String> = emptyMap(),
  private val composedMessageBuilder: (AgentPromptInitialMessageRequest) -> String = { request -> "composed:${request.prompt.trim()}" },
) : AgentSessionProviderDescriptor {
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
      message = composedMessageBuilder(request),
      startupPolicy = startupPolicyOverride ?: if (startupPromptCommandPolicyEnabled) {
        AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND
      }
      else {
        AgentInitialMessageStartupPolicy.POST_START_ONLY
      },
      timeoutPolicy = timeoutPolicy,
    )
  }
}
