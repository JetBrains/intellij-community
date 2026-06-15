// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_PROJECT_PATH_CONTEXT_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextTargetCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextToTargetRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextToTargetResult
import com.intellij.agent.workbench.prompt.core.AgentPromptContainerLauncher
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathContext
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageTimeoutPolicy
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.core.providers.isPlanModeRequested
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchPromptLaunchResultKind
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTargetKind
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetryEvent
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetryProvider
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionPromptLauncherBridge
import com.intellij.agent.workbench.sessions.service.OpenThreadLaunchOrigin
import com.intellij.agent.workbench.sessions.service.resolveAgentSessionPathState
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionPromptLauncherBridgeTest {
  @TestDisposable
  lateinit var testRootDisposable: Disposable

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

            assertThat(providerBridge.lastCreateMode.get()).isEqualTo(AgentSessionLaunchMode.STANDARD)
            assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
            assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
            assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
            assertThat(providerBridge.lastStartupBaseLaunchSpec.get()?.command)
              .containsExactly("test", "new", AgentSessionLaunchMode.STANDARD.name)
            assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("composed:Refactor selected code")
            val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
            assertThat(openRequest.normalizedPath).isEqualTo(INVALID_PROMPT_PROJECT_PATH)
            assertThat(openRequest.identity).startsWith("codex:new-")
            assertThat(openRequest.launchSpec.command)
              .containsExactly("test", "new", AgentSessionLaunchMode.STANDARD.name)
            assertThat(openRequest.launchSpec.envVariables).isEmpty()
            assertThat(openRequest.startupLaunchSpecOverride?.command)
              .containsExactly(
                "test",
                "new",
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
  fun launchRoutesSupportedContainerRequestToContainerLauncher() {
    val project = ProjectManager.getInstance().defaultProject
    val containerLauncher = RecordingContainerLauncher(
      supportedProviders = setOf(AgentSessionProvider.CLAUDE),
      available = true,
    )
    val standardLaunchCalls = AtomicInteger()
    val bridge = containerPromptLauncherBridge(
      launchPromptRequest = {
        standardLaunchCalls.incrementAndGet()
        AgentPromptLaunchResult.SUCCESS
      },
      sourceProjectResolver = { path -> project.takeIf { path == PROJECT_PATH } },
    )
    val request = promptLaunchRequest(provider = AgentSessionProvider.CLAUDE).copy(containerMode = true)

    withContainerLauncherForTest(containerLauncher, testRootDisposable) {
      val result = bridge.launch(request)

      assertThat(result).isEqualTo(AgentPromptLaunchResult.SUCCESS)
      assertThat(standardLaunchCalls.get()).isZero()
      assertThat(containerLauncher.launchCalls.get()).isEqualTo(1)
      assertThat(containerLauncher.lastProject.get()).isSameAs(project)
      assertThat(containerLauncher.lastRequest.get()).isEqualTo(request)
    }
  }

  @Test
  fun launchRejectsContainerRequestForUnsupportedProviderWithoutStandardFallback() {
    val containerLauncher = RecordingContainerLauncher(
      supportedProviders = setOf(AgentSessionProvider.CLAUDE),
      available = true,
    )
    val standardLaunchCalls = AtomicInteger()
    val bridge = containerPromptLauncherBridge(
      launchPromptRequest = {
        standardLaunchCalls.incrementAndGet()
        AgentPromptLaunchResult.SUCCESS
      },
    )
    val request = promptLaunchRequest(provider = AgentSessionProvider.CODEX).copy(containerMode = true)
    val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
    val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

    try {
      withContainerLauncherForTest(containerLauncher, testRootDisposable) {
        val result = bridge.launch(request)

        assertThat(result.launched).isFalse()
        assertThat(result.error).isEqualTo(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE)
        assertThat(standardLaunchCalls.get()).isZero()
        assertThat(containerLauncher.launchCalls.get()).isZero()
      }
      assertPromptLaunchResolvedTelemetry(
        telemetryEvents = telemetryEvents,
        provider = AgentWorkbenchTelemetryProvider.CODEX,
        launchResult = AgentWorkbenchPromptLaunchResultKind.UNSUPPORTED_LAUNCH_MODE,
      )
    }
    finally {
      token.finish()
    }
  }

  @Test
  fun launchRejectsContainerRequestWhenLauncherIsUnavailableWithoutStandardFallback() {
    val containerLauncher = RecordingContainerLauncher(
      supportedProviders = setOf(AgentSessionProvider.CLAUDE),
      available = false,
    )
    val standardLaunchCalls = AtomicInteger()
    val bridge = containerPromptLauncherBridge(
      launchPromptRequest = {
        standardLaunchCalls.incrementAndGet()
        AgentPromptLaunchResult.SUCCESS
      },
    )
    val request = promptLaunchRequest(provider = AgentSessionProvider.CLAUDE).copy(containerMode = true)
    val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
    val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

    try {
      withContainerLauncherForTest(containerLauncher, testRootDisposable) {
        val result = bridge.launch(request)

        assertThat(result.launched).isFalse()
        assertThat(result.error).isEqualTo(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
        assertThat(standardLaunchCalls.get()).isZero()
        assertThat(containerLauncher.launchCalls.get()).isZero()
      }
      assertPromptLaunchResolvedTelemetry(
        telemetryEvents = telemetryEvents,
        provider = AgentWorkbenchTelemetryProvider.CLAUDE,
        launchResult = AgentWorkbenchPromptLaunchResultKind.PROVIDER_UNAVAILABLE,
      )
    }
    finally {
      token.finish()
    }
  }

  @Test
  fun launchRejectsContainerRequestWhenContainerLauncherIsMissingWithoutStandardFallback() {
    val standardLaunchCalls = AtomicInteger()
    val bridge = containerPromptLauncherBridge(
      launchPromptRequest = {
        standardLaunchCalls.incrementAndGet()
        AgentPromptLaunchResult.SUCCESS
      },
    )
    val request = promptLaunchRequest(provider = AgentSessionProvider.CLAUDE).copy(containerMode = true)
    val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
    val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

    try {
      withContainerLaunchersForTest(emptyList(), testRootDisposable) {
        val result = bridge.launch(request)

        assertThat(result.launched).isFalse()
        assertThat(result.error).isEqualTo(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE)
        assertThat(standardLaunchCalls.get()).isZero()
      }
      assertPromptLaunchResolvedTelemetry(
        telemetryEvents = telemetryEvents,
        provider = AgentWorkbenchTelemetryProvider.CLAUDE,
        launchResult = AgentWorkbenchPromptLaunchResultKind.UNSUPPORTED_LAUNCH_MODE,
      )
    }
    finally {
      token.finish()
    }
  }

  @Test
  fun launchRejectsContainerRequestWhenSourceProjectCannotBeResolvedWithoutStandardFallback() {
    val containerLauncher = RecordingContainerLauncher(
      supportedProviders = setOf(AgentSessionProvider.CLAUDE),
      available = true,
    )
    val standardLaunchCalls = AtomicInteger()
    val bridge = containerPromptLauncherBridge(
      launchPromptRequest = {
        standardLaunchCalls.incrementAndGet()
        AgentPromptLaunchResult.SUCCESS
      },
    )
    val request = promptLaunchRequest(provider = AgentSessionProvider.CLAUDE).copy(containerMode = true)
    val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
    val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

    try {
      withContainerLauncherForTest(containerLauncher, testRootDisposable) {
        val result = bridge.launch(request)

        assertThat(result.launched).isFalse()
        assertThat(result.error).isEqualTo(AgentPromptLaunchError.INTERNAL_ERROR)
        assertThat(standardLaunchCalls.get()).isZero()
        assertThat(containerLauncher.launchCalls.get()).isZero()
      }
      assertPromptLaunchResolvedTelemetry(
        telemetryEvents = telemetryEvents,
        provider = AgentWorkbenchTelemetryProvider.CLAUDE,
        launchResult = AgentWorkbenchPromptLaunchResultKind.INTERNAL_ERROR,
      )
    }
    finally {
      token.finish()
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
  fun launchReportsPromptFailureTelemetryWhenTargetThreadIsBusyForPlanMode() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      composedMessageBuilder = { request -> request.prompt.trim() },
    )
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
                    listOf(
                      thread(
                        id = "thread-existing",
                        updatedAt = 200,
                        provider = AgentSessionProvider.CODEX,
                        activity = AgentThreadActivity.PROCESSING,
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
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service, launchService ->
          service.refresh()
          waitForCondition {
            val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
            project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
            project.threads.any { thread -> thread.id == "thread-existing" }
          }

          val bridge = promptLauncherBridge(service, launchService)
          val telemetryEvents = CopyOnWriteArrayList<AgentWorkbenchTelemetryEvent>()
          val token = AgentWorkbenchTelemetry.pushTestHandler(telemetryEvents::add)

          try {
            val baseRequest = promptLaunchRequest(targetThreadId = "thread-existing")
            val result = bridge.launch(
              baseRequest.copy(
                initialMessageRequest = baseRequest.initialMessageRequest.copy(
                  providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
                ),
              )
            )

            assertThat(result.launched).isFalse()
            assertThat(result.error).isEqualTo(AgentPromptLaunchError.TARGET_THREAD_BUSY_FOR_PLAN_MODE)
            assertThat(telemetryEvents).contains(
              AgentWorkbenchTelemetryEvent(
                id = AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID,
                provider = telemetryEvents.single().provider,
                launchMode = AgentSessionLaunchMode.STANDARD,
                targetKind = AgentWorkbenchTargetKind.THREAD,
                launchResult = AgentWorkbenchPromptLaunchResultKind.TARGET_THREAD_BUSY_FOR_PLAN_MODE,
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
    withOpenInNonDedicatedFrameSettingForTest(testRootDisposable) {
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
  fun createNewSessionAllowsConcurrentRequestsWithDifferentSingleFlightDiscriminators() {
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
    withOpenInNonDedicatedFrameSettingForTest(testRootDisposable) {
      AgentSessionProviders.withRegistryForTest(
        InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
      ) {
        runBlocking(Dispatchers.Default) {
          withServiceAndLaunch(
            sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
            projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
            chatOpenExecutor = chatOpenExecutor,
          ) { _, launchService ->
            try {
              launchService.createNewSession(
                path = PROJECT_PATH,
                provider = AgentSessionProvider.CODEX,
                mode = AgentSessionLaunchMode.STANDARD,
                entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
                singleFlightDiscriminator = "merge-session-1",
              )
              waitForCondition {
                chatOpenExecutor.openNewChatCalls.get() == 1
              }

              launchService.createNewSession(
                path = PROJECT_PATH,
                provider = AgentSessionProvider.CODEX,
                mode = AgentSessionLaunchMode.STANDARD,
                entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
                singleFlightDiscriminator = "merge-session-2",
              )

              waitForCondition {
                providerBridge.createCalls.get() == 2 &&
                chatOpenExecutor.openNewChatCalls.get() == 2
              }
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
  fun createNewSessionWaitsForOpenedChatHandlerBeforeReleasingSingleFlight() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val releaseOpenedChatHandler = CompletableDeferred<Unit>()
    val firstLaunchResult = CompletableDeferred<AgentPromptLaunchResult>()
    val secondLaunchResult = CompletableDeferred<AgentPromptLaunchResult>()
    val chatOpenExecutor = RecordingChatOpenExecutor()
    withOpenInNonDedicatedFrameSettingForTest(testRootDisposable) {
      AgentSessionProviders.withRegistryForTest(
        InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
      ) {
        runBlocking(Dispatchers.Default) {
          withServiceAndLaunch(
            sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
            projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
            chatOpenExecutor = chatOpenExecutor,
          ) { _, launchService ->
            launchService.createNewSession(
              path = PROJECT_PATH,
              provider = AgentSessionProvider.CODEX,
              mode = AgentSessionLaunchMode.STANDARD,
              entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
              openedChatHandler = { _, _ -> releaseOpenedChatHandler.await() },
              promptLaunchResolved = { result -> firstLaunchResult.complete(result) },
            )
            waitForCondition {
              chatOpenExecutor.openNewChatCalls.get() == 1
            }

            launchService.createNewSession(
              path = PROJECT_PATH,
              provider = AgentSessionProvider.CODEX,
              mode = AgentSessionLaunchMode.STANDARD,
              entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
              promptLaunchResolved = { result -> secondLaunchResult.complete(result) },
            )

            waitForCondition {
              secondLaunchResult.isCompleted
            }
            val droppedDuplicateResult = secondLaunchResult.await()
            assertThat(firstLaunchResult.isCompleted).isFalse()
            assertThat(droppedDuplicateResult.launched).isFalse()
            assertThat(droppedDuplicateResult.error).isEqualTo(AgentPromptLaunchError.DROPPED_DUPLICATE)
            assertThat(chatOpenExecutor.openNewChatCalls.get()).isEqualTo(1)

            releaseOpenedChatHandler.complete(Unit)

            assertThat(firstLaunchResult.await()).isEqualTo(AgentPromptLaunchResult.SUCCESS)
          }
        }
      }
    }
  }

  @Test
  fun createNewSessionRunsOpenAndOpenedChatHandlerInProvidedLaunchModality() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val openModality = AtomicReference<ModalityState?>(null)
    val handlerModality = AtomicReference<ModalityState?>(null)
    val launchModalityState = createTestModalityState()
    val chatOpenExecutor = RecordingChatOpenExecutor(
      onOpenNewChat = { _, _ ->
        openModality.set(currentCoroutineContext().contextModality())
      }
    )
    withOpenInNonDedicatedFrameSettingForTest(testRootDisposable) {
      AgentSessionProviders.withRegistryForTest(
        InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
      ) {
        runBlocking(Dispatchers.Default) {
          withServiceAndLaunch(
            sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
            projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
            chatOpenExecutor = chatOpenExecutor,
          ) { _, launchService ->
            assertThat(launchModalityState).isNotEqualTo(ModalityState.nonModal())

            launchService.createNewSession(
              path = PROJECT_PATH,
              provider = AgentSessionProvider.CODEX,
              mode = AgentSessionLaunchMode.STANDARD,
              entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
              launchModalityState = launchModalityState,
              openedChatHandler = { _, _ ->
                handlerModality.set(currentCoroutineContext().contextModality())
              },
            )

            waitForCondition {
              openModality.get() === launchModalityState &&
              handlerModality.get() === launchModalityState
            }

            assertThat(chatOpenExecutor.openNewChatCalls.get()).isEqualTo(1)
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
  fun promptLaunchAppliesGenerationSettingsToNewSessionLaunchSpec() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      generationSettingsApplier = { launchSpec, settings ->
        launchSpec.copy(command = launchSpec.command + listOf("--effort", settings.reasoningEffort.name.lowercase()))
      },
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

          val result = bridge.launch(
            promptLaunchRequest(
              provider = AgentSessionProvider.CODEX,
              generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
            )
          )

          assertThat(result.launched).isTrue()
          waitForCondition { chatOpenExecutor.openNewChatCalls.get() == 1 }
          assertThat(chatOpenExecutor.lastOpenNewChatRequest.get()?.launchSpec?.command)
            .containsExactly("test", "new", AgentSessionLaunchMode.STANDARD.name, "--effort", "high")
          assertThat(chatOpenExecutor.lastOpenNewChatRequest.get()?.generationSettings)
            .isEqualTo(AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH))
        }
      }
    }
  }

  @Test
  fun promptLaunchAppliesGenerationSettingsToExistingThreadResumeLaunchSpec() {
    val generationSettings = AgentPromptGenerationSettings(modelId = "pi:custom-model")
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPolicyOverride = AgentInitialMessageStartupPolicy.POST_START_ONLY,
      generationSettingsApplier = { launchSpec, settings ->
        launchSpec.copy(command = launchSpec.command + listOf("--model", settings.modelId.orEmpty()))
      },
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
            project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
            project.threads.any { thread -> thread.id == "thread-existing" }
          }

          val bridge = promptLauncherBridge(service, launchService)

          val result = bridge.launch(
            promptLaunchRequest(
              provider = AgentSessionProvider.CODEX,
              targetThreadId = "thread-existing",
              generationSettings = generationSettings,
            )
          )

          assertThat(result.launched).isTrue()
          waitForCondition { chatOpenExecutor.openChatCalls.get() == 1 }
          val openRequest = chatOpenExecutor.lastOpenChatRequest.get()
          assertThat(openRequest?.launchSpecOverride?.command)
            .containsExactly("test", "resume", "thread-existing", "--model", "pi:custom-model")
          assertThat(openRequest?.generationSettings).isEqualTo(generationSettings)
          assertThat(providerBridge.lastGenerationSettings.get()).isEqualTo(generationSettings)
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun createNewSessionCanSkipUpdatingGeneralProviderPreferences() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    val chatOpenExecutor = RecordingChatOpenExecutor()
    val uiPreferencesState = AgentSessionUiPreferencesStateService().apply {
      setProviderPreferences(
        AgentPromptLauncherBridge.ProviderPreferences(
          providerId = AgentSessionProvider.CLAUDE.value,
          launchMode = AgentSessionLaunchMode.YOLO,
        )
      )
    }
    AgentSessionProviders.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withServiceAndLaunch(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          uiPreferencesState = uiPreferencesState,
          chatOpenExecutor = chatOpenExecutor,
        ) { _, launchService ->
          launchService.createNewSession(
            path = PROJECT_PATH,
            provider = AgentSessionProvider.CODEX,
            mode = AgentSessionLaunchMode.STANDARD,
            entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
            updateGeneralProviderPreferences = false,
          )

          waitForCondition {
            chatOpenExecutor.openNewChatCalls.get() == 1
          }
          assertThat(uiPreferencesState.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CLAUDE)
          assertThat(uiPreferencesState.getLastUsedLaunchMode()).isEqualTo(AgentSessionLaunchMode.YOLO)
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
  fun launchUsesPostStartDispatchForCodexPlanPromptOnNewThread() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
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
            initialMessageRequest = baseRequest.initialMessageRequest.copy(
              providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            ),
          )

          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition {
            providerBridge.composeCalls.get() == 1 &&
            providerBridge.createCalls.get() == 1 &&
            chatOpenExecutor.openNewChatCalls.get() == 1
          }

          assertThat(providerBridge.createCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastCreateMode.get()).isEqualTo(AgentSessionLaunchMode.STANDARD)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.generationSettingsApplyCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastGenerationSettingsInitialMessagePlan.get()?.mode).isEqualTo(AgentInitialMessageMode.PLAN)
          assertThat(providerBridge.startupCommandCalls.get()).isZero()
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()).isNull()
          assertThat(providerBridge.lastStartupPrompt.get()).isNull()
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.identity).startsWith("codex:new-")
          assertThat(openRequest.launchSpec.command)
            .containsExactly("test", "new", AgentSessionLaunchMode.STANDARD.name)
          assertThat(openRequest.startupLaunchSpecOverride).isNull()
          assertThat(openRequest.postStartDispatchSteps).containsExactly(
            AgentInitialMessageDispatchStep(
              action = AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
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
  fun launchUsesStartupOverrideForPlanModeWhenPlanPolicyAllowsStartupCommand() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPolicyOverride = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
      timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      composedMessageBuilder = { request -> request.prompt.trim() },
      supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
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
            initialMessageRequest = baseRequest.initialMessageRequest.copy(
              providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            ),
          )
          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition {
            providerBridge.composeCalls.get() == 1 &&
            chatOpenExecutor.openNewChatCalls.get() == 1
          }

          assertThat(providerBridge.createCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()?.command)
            .containsExactly("test", "new", AgentSessionLaunchMode.STANDARD.name)
          assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("Refactor selected code")
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
          assertThat(openRequest.launchSpec.command)
            .containsExactly("test", "new", AgentSessionLaunchMode.STANDARD.name)
          assertThat(openRequest.startupLaunchSpecOverride?.command)
            .containsExactly("test", "new", AgentSessionLaunchMode.STANDARD.name, "--", "Refactor selected code")
          assertThat(openRequest.startupLaunchSpecOverride?.envVariables).isEmpty()
          assertThat(openRequest.initialComposedMessage).isNull()
          assertThat(openRequest.postStartDispatchSteps).containsExactly(
            AgentInitialMessageDispatchStep(
              action = AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
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
            project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
            project.threads.any { thread -> thread.id == "thread-existing" }
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
  fun launchRoutesProviderOptionPlanPromptToExistingThreadAsStartupOverrideWhenTryStartupCommandIsEnabled() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPolicyOverride = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
      timeoutPolicy = AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS,
      composedMessageBuilder = { request -> request.prompt.trim() },
      supportedReasoningEffortsOverride = setOf(AgentPromptReasoningEffort.HIGH),
      generationSettingsApplier = { launchSpec, settings ->
        launchSpec.copy(command = launchSpec.command + listOf("--effort", settings.reasoningEffort.name.lowercase()))
      },
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
            project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
            project.threads.any { thread -> thread.id == "thread-existing" }
          }

          val bridge = promptLauncherBridge(service, launchService)
          val baseRequest = promptLaunchRequest(
            targetThreadId = "thread-existing",
            generationSettings = AgentPromptGenerationSettings(reasoningEffort = AgentPromptReasoningEffort.HIGH),
          )
          val request = baseRequest.copy(
            initialMessageRequest = baseRequest.initialMessageRequest.copy(
              providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            ),
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
          assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastStartupBaseLaunchSpec.get()?.command)
            .containsExactly("test", "resume", "thread-existing", "--effort", "high")
          assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("Refactor selected code")
          assertThat(providerBridge.generationSettingsApplyCalls.get()).isEqualTo(1)
          assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()

          val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
          assertThat(openRequest.normalizedPath).isEqualTo(PROJECT_PATH)
          assertThat(openRequest.thread.id).isEqualTo("thread-existing")
          assertThat(openRequest.subAgent).isNull()
          assertThat(openRequest.startupLaunchSpecOverride?.command)
            .containsExactly("test", "resume", "thread-existing", "--effort", "high", "--", "Refactor selected code")
          assertThat(openRequest.startupLaunchSpecOverride?.envVariables).isEmpty()
          assertThat(openRequest.initialComposedMessage).isNull()
          assertThat(openRequest.postStartDispatchSteps).containsExactly(
            AgentInitialMessageDispatchStep(
              action = AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
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
  fun openChatThreadRechecksPromptPlanModeAgainstLatestThreadActivity() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
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
                    listOf(
                      thread(
                        id = "thread-existing",
                        updatedAt = 200,
                        provider = AgentSessionProvider.CODEX,
                        activity = AgentThreadActivity.PROCESSING,
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
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
          chatOpenExecutor = chatOpenExecutor,
        ) { service, launchService ->
          service.refresh()
          waitForCondition {
            val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
            project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
            project.threads.any { thread -> thread.id == "thread-existing" }
          }

          val promptLaunchResults = CopyOnWriteArrayList<AgentPromptLaunchResult>()
          launchService.openChatThread(
            path = PROJECT_PATH,
            thread = thread(
              id = "thread-existing",
              updatedAt = 150,
              provider = AgentSessionProvider.CODEX,
              activity = AgentThreadActivity.READY,
            ),
            entryPoint = AgentWorkbenchEntryPoint.PROMPT,
            initialMessageRequest = AgentPromptInitialMessageRequest(
              prompt = "Refactor selected code",
              providerOptionIds = setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE),
            ),
            launchOrigin = OpenThreadLaunchOrigin.PROMPT_LAUNCH,
            promptLaunchResolved = promptLaunchResults::add,
          )

          waitForCondition { promptLaunchResults.isNotEmpty() }

          assertThat(promptLaunchResults).containsExactly(
            AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_BUSY_FOR_PLAN_MODE)
          )
          assertThat(chatOpenExecutor.openChatCalls.get()).isZero()
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
    withOpenInNonDedicatedFrameSettingForTest(testRootDisposable) {
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
              val project =
                service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
              project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
              project.threads.any { thread -> thread.id == "thread-existing" }
            }

            val existingThread = checkNotNull(
              service.state.value.projects
                .firstOrNull { project -> project.path == PROJECT_PATH }
                ?.threads
                ?.firstOrNull { thread -> thread.id == "thread-existing" }
            )

            launchService.openChatThread(
              path = PROJECT_PATH,
              thread = existingThread,
              entryPoint = AgentWorkbenchEntryPoint.TREE_ROW
            )
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
            project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
            project.threads.any { thread -> thread.id == "thread-existing" }
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
            val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
            project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED
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
  fun observeExistingThreadsFiltersProviderThreadsFromSharedState() = withCliAvailableTestRegistry {
    runBlocking(Dispatchers.Default) {
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
          val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
          project.providerLoadStates[AgentSessionProvider.CLAUDE] == AgentSessionProviderLoadState.LOADED
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
  }

  @Test
  fun observeExistingThreadsFiltersPendingNewThreads() = withCliAvailableTestRegistry {
    runBlocking(Dispatchers.Default) {
      withServiceAndLaunch(
        sessionSourcesProvider = {
          listOf(
            ScriptedSessionSource(
              provider = AgentSessionProvider.CODEX,
              listFromOpenProject = { path, _ ->
                if (path == PROJECT_PATH) {
                  listOf(
                    thread(id = "new-global-prompt", updatedAt = 300, provider = AgentSessionProvider.CODEX),
                    thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX),
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
          val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
          project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED
        }

        val bridge = promptLauncherBridge(service, launchService)
        val snapshot = bridge.observeExistingThreads(
          projectPath = PROJECT_PATH,
          provider = AgentSessionProvider.CODEX,
        ).first { it.hasLoaded }

        assertThat(snapshot.threads.map { thread -> thread.id })
          .containsExactly("codex-1")
      }
    }
  }

  @Test
  fun refreshExistingThreadsBootstrapsWhenPathIsMissing() = withCliAvailableTestRegistry {
    runBlocking(Dispatchers.Default) {
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
          project.providerLoadStates[AgentSessionProvider.CLAUDE] == AgentSessionProviderLoadState.LOADED &&
          project.threads.map { thread -> thread.id } == listOf("claude-1")
        }

        assertThat(openLoads.get()).isEqualTo(1)
      }
    }
  }

  @Test
  fun refreshExistingThreadsUsesProviderScopedRefreshForLoadedPath() = withCliAvailableTestRegistry {
    runBlocking(Dispatchers.Default) {
      val codexClosedLoads = AtomicInteger(0)
      val claudeClosedLoads = AtomicInteger(0)
      var claudeClosedThreadId = "claude-closed-1"

      withServiceAndLaunch(
        sessionSourcesProvider = {
          listOf(
            ScriptedSessionSource(
              provider = AgentSessionProvider.CODEX,
              listFromOpenProject = { path, _ ->
                if (path == PROJECT_PATH) listOf(
                  thread(
                    id = "codex-open",
                    updatedAt = 300,
                    provider = AgentSessionProvider.CODEX
                  )
                )
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
                if (path == PROJECT_PATH) listOf(
                  thread(
                    id = "claude-open",
                    updatedAt = 200,
                    provider = AgentSessionProvider.CLAUDE
                  )
                )
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
  }

  @Test
  fun observeExistingThreadsMarksProviderWarningAsError() = withCliAvailableTestRegistry {
    runBlocking(Dispatchers.Default) {
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
          val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
          project.providerLoadStates[AgentSessionProvider.CODEX] == AgentSessionProviderLoadState.LOADED &&
          project.providerLoadStates[AgentSessionProvider.CLAUDE] == AgentSessionProviderLoadState.FAILED
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

  @Test
  fun addContextToOpenChatTargetDelegatesToInjectedHandler() {
    val capturedRequest = AtomicReference<AgentPromptAddContextToTargetRequest>()
    val bridge = AgentSessionPromptLauncherBridge(
      launchPromptRequest = { error("not used") },
      stateFlowProvider = { error("not used") },
      pathStateResolver = ::resolveAgentSessionPathState,
      refreshCatalogAndLoadNewlyOpened = {},
      refreshProviderForPath = { _, _ -> },
      preferredProviderProvider = { null },
      addContextToOpenChatTarget = { request ->
        capturedRequest.set(request)
        AgentPromptAddContextToTargetResult.ADDED_TO_CHAT
      },
    )
    val request = addContextToTargetRequest()

    val result = runBlocking(Dispatchers.Default) {
      bridge.addContextToOpenChatTarget(request)
    }

    assertThat(result).isEqualTo(AgentPromptAddContextToTargetResult.ADDED_TO_CHAT)
    assertThat(capturedRequest.get()).isEqualTo(request)
  }

  @Test
  fun addContextToOpenChatTargetPropagatesAlreadyAddedResult() {
    val bridge = AgentSessionPromptLauncherBridge(
      launchPromptRequest = { error("not used") },
      stateFlowProvider = { error("not used") },
      pathStateResolver = ::resolveAgentSessionPathState,
      refreshCatalogAndLoadNewlyOpened = {},
      refreshProviderForPath = { _, _ -> },
      preferredProviderProvider = { null },
      addContextToOpenChatTarget = {
        AgentPromptAddContextToTargetResult.ALREADY_ADDED_TO_CHAT
      },
    )

    val result = runBlocking(Dispatchers.Default) {
      bridge.addContextToOpenChatTarget(addContextToTargetRequest())
    }

    assertThat(result).isEqualTo(AgentPromptAddContextToTargetResult.ALREADY_ADDED_TO_CHAT)
  }
}

private fun addContextToTargetRequest(): AgentPromptAddContextToTargetRequest {
  return AgentPromptAddContextToTargetRequest(
    target = AgentPromptAddContextTargetCandidate(
      projectPath = PROJECT_PATH,
      provider = AgentSessionProvider.CODEX,
      threadId = "thread-existing",
      displayText = "Existing thread",
    ),
    contextItems = listOf(
      AgentPromptContextItem(
        rendererId = "test",
        title = "Selected code",
        body = "fun selected() {}",
        source = "test",
      )
    ),
  )
}

private fun <T> withOpenInNonDedicatedFrameSettingForTest(parentDisposable: Disposable, action: () -> T): T {
  registerDedicatedFrameSettingForTest(parentDisposable)
  val previousValue = AgentChatOpenModeSettings.openInDedicatedFrame()
  AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)
  try {
    return action()
  }
  finally {
    AgentChatOpenModeSettings.setOpenInDedicatedFrame(previousValue)
  }
}

private fun assertPromptLaunchResolvedTelemetry(
  telemetryEvents: Iterable<AgentWorkbenchTelemetryEvent>,
  provider: AgentWorkbenchTelemetryProvider,
  launchResult: AgentWorkbenchPromptLaunchResultKind,
  launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  targetKind: AgentWorkbenchTargetKind = AgentWorkbenchTargetKind.NEW_THREAD,
) {
  assertThat(telemetryEvents).contains(
    AgentWorkbenchTelemetryEvent(
      id = AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID,
      provider = provider,
      launchMode = launchMode,
      targetKind = targetKind,
      launchResult = launchResult,
    )
  )
}

private fun containerPromptLauncherBridge(
  launchPromptRequest: (AgentPromptLaunchRequest) -> AgentPromptLaunchResult,
  sourceProjectResolver: (String) -> Project? = { null },
): AgentSessionPromptLauncherBridge {
  return AgentSessionPromptLauncherBridge(
    launchPromptRequest = launchPromptRequest,
    stateFlowProvider = {
      error("stateFlowProvider is unavailable in this test setup")
    },
    pathStateResolver = ::resolveAgentSessionPathState,
    refreshCatalogAndLoadNewlyOpened = {},
    refreshProviderForPath = { _, _ -> },
    preferredProviderProvider = { null },
    sourceProjectResolver = sourceProjectResolver,
  )
}

private fun <T> withContainerLauncherForTest(
  launcher: AgentPromptContainerLauncher,
  parentDisposable: Disposable,
  action: () -> T,
): T {
  return withContainerLaunchersForTest(listOf(launcher), parentDisposable, action)
}

private fun <T> withContainerLaunchersForTest(
  launchers: List<AgentPromptContainerLauncher>,
  parentDisposable: Disposable,
  action: () -> T,
): T {
  val point = AgentPromptContainerLauncher.EP_NAME.point as ExtensionPointImpl<AgentPromptContainerLauncher>
  point.maskAll(newList = launchers, parentDisposable = parentDisposable, fireEvents = false)
  return action()
}

private class RecordingContainerLauncher(
  private val supportedProviders: Set<AgentSessionProvider>,
  private val available: Boolean,
) : AgentPromptContainerLauncher {
  val launchCalls = AtomicInteger()
  val lastProject = AtomicReference<Project?>()
  val lastRequest = AtomicReference<AgentPromptLaunchRequest?>()

  override fun isAvailable(): Boolean = available

  override fun supportsProvider(provider: AgentSessionProvider): Boolean = provider in supportedProviders

  override fun launch(project: Project, request: AgentPromptLaunchRequest) {
    launchCalls.incrementAndGet()
    lastProject.set(project)
    lastRequest.set(request)
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

private fun createTestModalityState(): ModalityState {
  val modalityClass = Class.forName("com.intellij.openapi.application.impl.ModalityStateEx")
  return modalityClass.getDeclaredConstructor().newInstance() as ModalityState
}

private fun promptLaunchRequest(
  provider: AgentSessionProvider = AgentSessionProvider.CODEX,
  launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  projectPath: String = PROJECT_PATH,
  targetThreadId: String? = null,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
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
    generationSettings = generationSettings,
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
  override val promptOptions: List<AgentPromptProviderOption> = listOf(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION),
  private val supportedReasoningEffortsOverride: Set<AgentPromptReasoningEffort> = emptySet(),
  private val generationSettingsApplier: (
    AgentSessionTerminalLaunchSpec,
    AgentPromptGenerationSettings,
  ) -> AgentSessionTerminalLaunchSpec = { launchSpec, _ -> launchSpec },
) : AgentSessionProviderDescriptor {
  val createCalls: AtomicInteger = AtomicInteger(0)
  val composeCalls: AtomicInteger = AtomicInteger(0)
  val startupCommandCalls: AtomicInteger = AtomicInteger(0)
  val shouldUseStartupPromptCommandCalls: AtomicInteger = AtomicInteger(0)
  val lastCreateMode: AtomicReference<AgentSessionLaunchMode?> = AtomicReference(null)
  val lastComposeRequest: AtomicReference<AgentPromptInitialMessageRequest?> = AtomicReference(null)
  val lastStartupBaseLaunchSpec: AtomicReference<AgentSessionTerminalLaunchSpec?> = AtomicReference(null)
  val lastStartupPrompt: AtomicReference<String?> = AtomicReference(null)
  val lastShouldUseStartupPromptCommandRequest: AtomicReference<AgentPromptInitialMessageRequest?> = AtomicReference(null)
  val lastGenerationSettings: AtomicReference<AgentPromptGenerationSettings?> = AtomicReference(null)
  val lastGenerationSettingsInitialMessagePlan: AtomicReference<AgentInitialMessagePlan?> = AtomicReference(null)
  val generationSettingsApplyCalls: AtomicInteger = AtomicInteger(0)

  override val displayNameKey: String
    get() = "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex"

  override val icon: Icon
    get() = if (provider == AgentSessionProvider.CLAUDE) AgentWorkbenchCommonIcons.Claude else AgentWorkbenchCommonIcons.Codex

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = supportedModes

  override val supportedReasoningEfforts: Set<AgentPromptReasoningEffort>
    get() = supportedReasoningEffortsOverride

  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@RecordingPromptLaunchProviderBridge.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override suspend fun isCliAvailable(): Boolean = true

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "resume", sessionId))
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    createCalls.incrementAndGet()
    lastCreateMode.set(mode)
    return AgentSessionTerminalLaunchSpec(command = listOf("test", "new", mode.name))
  }

  override fun applyGenerationSettings(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    generationSettings: AgentPromptGenerationSettings,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec {
    generationSettingsApplyCalls.incrementAndGet()
    val sanitizedGenerationSettings = sanitizeGenerationSettings(generationSettings)
    lastGenerationSettings.set(sanitizedGenerationSettings)
    lastGenerationSettingsInitialMessagePlan.set(initialMessagePlan)
    return generationSettingsApplier(baseLaunchSpec, sanitizedGenerationSettings)
  }

  override fun buildLaunchSpecWithInitialMessage(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec? {
    startupCommandCalls.incrementAndGet()
    lastStartupBaseLaunchSpec.set(baseLaunchSpec)
    lastStartupPrompt.set(initialMessagePlan.message)
    if (!startupPromptCommandSupported) {
      return null
    }
    val message = initialMessagePlan.message ?: return baseLaunchSpec
    return baseLaunchSpec.copy(
      command = baseLaunchSpec.command + listOf("--", message),
      envVariables = baseLaunchSpec.envVariables + startupPromptCommandEnvVariables,
    )
  }

  override fun buildPostStartDispatchSteps(initialMessagePlan: AgentInitialMessagePlan): List<AgentInitialMessageDispatchStep> {
    if (provider != AgentSessionProvider.CODEX || initialMessagePlan.mode != AgentInitialMessageMode.PLAN) {
      return super.buildPostStartDispatchSteps(initialMessagePlan)
    }

    val message = initialMessagePlan.message.orEmpty()
    return listOfNotNull(
      AgentInitialMessageDispatchStep(
        action = AgentInitialMessageDispatchAction.ENSURE_TERMINAL_PLAN_MODE,
        timeoutPolicy = initialMessagePlan.timeoutPolicy,
        completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
      ),
      message.takeIf(String::isNotEmpty)?.let { prompt ->
        AgentInitialMessageDispatchStep(
          text = prompt,
          timeoutPolicy = initialMessagePlan.timeoutPolicy,
        )
      },
    )
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    composeCalls.incrementAndGet()
    lastComposeRequest.set(request)
    shouldUseStartupPromptCommandCalls.incrementAndGet()
    lastShouldUseStartupPromptCommandRequest.set(request)
    val composedMessage = composedMessageBuilder(request)
    val planMode = request.isPlanModeRequested()
    val startupPolicy = startupPolicyOverride ?: when {
      planMode && provider == AgentSessionProvider.CODEX -> AgentInitialMessageStartupPolicy.POST_START_ONLY
      startupPromptCommandPolicyEnabled -> AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND
      else -> AgentInitialMessageStartupPolicy.POST_START_ONLY
    }
    return AgentInitialMessagePlan(
      message = composedMessage,
      mode = if (planMode) AgentInitialMessageMode.PLAN else AgentInitialMessageMode.STANDARD,
      startupPolicy = startupPolicy,
      timeoutPolicy = timeoutPolicy,
    )
  }
}

private fun <T> withCliAvailableTestRegistry(action: () -> T): T {
  return AgentSessionProviders.withRegistryForTest(
    InMemoryAgentSessionProviderRegistry(
      listOf(
        TestAgentSessionProviderDescriptor(
          provider = AgentSessionProvider.CLAUDE,
          supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
          cliAvailable = true,
        ),
        TestAgentSessionProviderDescriptor(
          provider = AgentSessionProvider.CODEX,
          supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
          cliAvailable = true,
        ),
      )
    ),
    action,
  )
}
