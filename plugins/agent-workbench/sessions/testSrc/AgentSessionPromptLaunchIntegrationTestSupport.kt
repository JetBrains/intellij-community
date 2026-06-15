// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.service.AgentSessionChatOpenExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

fun existingThreadPromptLaunchRequest(
  provider: AgentSessionProvider,
  projectPath: String,
  threadId: String,
  prompt: String = "Refactor selected code",
  contextTitle: String = "Project",
  contextBody: String = "project-a",
  planMode: Boolean = false,
): AgentPromptLaunchRequest {
  return AgentPromptLaunchRequest(
    provider = provider,
    projectPath = projectPath,
    launchMode = AgentSessionLaunchMode.STANDARD,
    initialMessageRequest = AgentPromptInitialMessageRequest(
      prompt = prompt,
      contextItems = listOf(
        AgentPromptContextItem(
          rendererId = "project",
          title = contextTitle,
          body = contextBody,
          source = "test",
        )
      ),
      providerOptionIds = if (planMode) setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE) else emptySet(),
    ),
    targetThreadId = threadId,
    preferredDedicatedFrame = null,
  )
}

fun newThreadPromptLaunchRequest(
  provider: AgentSessionProvider,
  projectPath: String,
  prompt: String = "Refactor selected code",
  contextTitle: String = "Project",
  contextBody: String = "project-a",
  planMode: Boolean = false,
): AgentPromptLaunchRequest {
  return AgentPromptLaunchRequest(
    provider = provider,
    projectPath = projectPath,
    launchMode = AgentSessionLaunchMode.STANDARD,
    initialMessageRequest = AgentPromptInitialMessageRequest(
      prompt = prompt,
      contextItems = listOf(
        AgentPromptContextItem(
          rendererId = "project",
          title = contextTitle,
          body = contextBody,
          source = "test",
        )
      ),
      providerOptionIds = if (planMode) setOf(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE) else emptySet(),
    ),
    targetThreadId = null,
    preferredDedicatedFrame = null,
  )
}

fun assertExistingThreadLaunchUsesPostStartDispatch(
  descriptor: AgentSessionProviderDescriptor,
  request: AgentPromptLaunchRequest,
  projectPath: String,
  threadId: String,
  projectName: String = "Project A",
) {
  val provider = descriptor.provider
  val chatOpenExecutor = RecordingChatOpenExecutor()
  AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      withTestServiceAndLaunch(
        sessionSourcesProvider = { listOf(descriptor.sessionSource) },
        projectEntriesProvider = { listOf(openTestProjectEntry(projectPath, projectName)) },
        chatOpenExecutor = chatOpenExecutor,
      ) { service, launchService ->
        service.refresh()
        waitForCondition(timeoutMs = PROMPT_LAUNCH_WAIT_TIMEOUT_MS) {
          val project = service.state.value.projects.firstOrNull { it.path == projectPath } ?: return@waitForCondition false
          project.providerLoadStates[provider] == AgentSessionProviderLoadState.LOADED &&
          project.threads.any { thread -> thread.id == threadId }
        }

        val result = launchService.launchPromptRequest(request)

        assertThat(result.launched).isTrue()
        assertThat(result.error).isNull()
        chatOpenExecutor.awaitOpenChatCalls(1)

        val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
        val initialMessagePlan = descriptor.buildInitialMessagePlan(request.initialMessageRequest)
        val expectedSteps = descriptor.buildPostStartDispatchSteps(initialMessagePlan)

        assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        assertThat(openRequest.normalizedPath).isEqualTo(projectPath)
        assertThat(openRequest.thread.id).isEqualTo(threadId)
        assertThat(openRequest.thread.provider).isEqualTo(provider)
        assertThat(openRequest.subAgent).isNull()
        assertThat(openRequest.startupLaunchSpecOverride).isNull()
        assertThat(openRequest.postStartDispatchSteps).containsExactlyElementsOf(expectedSteps)
        assertThat(openRequest.initialMessageToken).isNotNull()
        if (expectedSteps.size == 1) {
          assertThat(openRequest.initialComposedMessage).isEqualTo(expectedSteps.single().text)
        }
        else {
          assertThat(openRequest.initialComposedMessage).isNull()
        }
      }
    }
  }
}

fun assertExistingThreadLaunchUsesStartupOverride(
  descriptor: AgentSessionProviderDescriptor,
  request: AgentPromptLaunchRequest,
  projectPath: String,
  threadId: String,
  projectName: String = "Project A",
): ExistingThreadPromptLaunchObservation {
  val provider = descriptor.provider
  val chatOpenExecutor = RecordingChatOpenExecutor()
  var observation: ExistingThreadPromptLaunchObservation? = null
  AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      withTestServiceAndLaunch(
        sessionSourcesProvider = { listOf(descriptor.sessionSource) },
        projectEntriesProvider = { listOf(openTestProjectEntry(projectPath, projectName)) },
        chatOpenExecutor = chatOpenExecutor,
      ) { service, launchService ->
        service.refresh()
        waitForCondition(timeoutMs = PROMPT_LAUNCH_WAIT_TIMEOUT_MS) {
          val project = service.state.value.projects.firstOrNull { it.path == projectPath } ?: return@waitForCondition false
          project.providerLoadStates[provider] == AgentSessionProviderLoadState.LOADED &&
          project.threads.any { thread -> thread.id == threadId }
        }

        val result = launchService.launchPromptRequest(request)

        assertThat(result.launched).isTrue()
        assertThat(result.error).isNull()
        chatOpenExecutor.awaitOpenChatCalls(1)

        val openRequest = checkNotNull(chatOpenExecutor.lastOpenChatRequest.get())
        val initialMessagePlan = descriptor.buildInitialMessagePlan(request.initialMessageRequest)
        val expectedSteps = descriptor.buildPostStartDispatchSteps(initialMessagePlan)

        assertThat(chatOpenExecutor.openNewChatCalls.get()).isZero()
        assertThat(openRequest.normalizedPath).isEqualTo(projectPath)
        assertThat(openRequest.thread.id).isEqualTo(threadId)
        assertThat(openRequest.thread.provider).isEqualTo(provider)
        assertThat(openRequest.subAgent).isNull()
        assertThat(openRequest.startupLaunchSpecOverride).isNotNull()
        assertThat(openRequest.postStartDispatchSteps).containsExactlyElementsOf(expectedSteps)
        assertThat(openRequest.initialMessageToken).isNotNull()
        observation = ExistingThreadPromptLaunchObservation(
          launchResult = result,
          normalizedPath = openRequest.normalizedPath,
          thread = openRequest.thread,
          startupLaunchSpecOverride = openRequest.startupLaunchSpecOverride,
          postStartDispatchSteps = openRequest.postStartDispatchSteps,
          initialMessageToken = openRequest.initialMessageToken,
        )
      }
    }
  }

  return checkNotNull(observation)
}

fun assertNewThreadPromptLaunchOpensNewChat(
  descriptor: AgentSessionProviderDescriptor,
  request: AgentPromptLaunchRequest,
  projectName: String = "Project A",
): NewThreadPromptLaunchObservation {
  require(request.targetThreadId == null) {
    "New-thread prompt launch assertions require request.targetThreadId to be null"
  }

  val chatOpenExecutor = RecordingChatOpenExecutor()
  var observation: NewThreadPromptLaunchObservation? = null

  AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      withTestServiceAndLaunch(
        sessionSourcesProvider = { listOf(descriptor.sessionSource) },
        projectEntriesProvider = { listOf(openTestProjectEntry(request.projectPath, projectName)) },
        chatOpenExecutor = chatOpenExecutor,
      ) { service, launchService ->
        service.refresh()
        waitForCondition(timeoutMs = PROMPT_LAUNCH_WAIT_TIMEOUT_MS) {
          val project = service.state.value.projects.firstOrNull { project -> project.path == request.projectPath }
                        ?: return@waitForCondition false
          project.providerLoadStates[descriptor.provider] == AgentSessionProviderLoadState.LOADED
        }

        val result = launchService.launchPromptRequest(request)

        assertThat(result.launched).isTrue()
        assertThat(result.error).isNull()
        chatOpenExecutor.awaitOpenNewChatCalls(1)

        assertThat(chatOpenExecutor.openChatCalls.get()).isZero()
        val openRequest = checkNotNull(chatOpenExecutor.lastOpenNewChatRequest.get())
        observation = NewThreadPromptLaunchObservation(
          launchResult = result,
          normalizedPath = openRequest.normalizedPath,
          identity = openRequest.identity,
          launchSpec = openRequest.launchSpec,
          startupLaunchSpecOverride = openRequest.startupLaunchSpecOverride,
          postStartDispatchSteps = openRequest.postStartDispatchSteps,
          initialMessageToken = openRequest.initialMessageToken,
          preferredDedicatedFrame = openRequest.preferredDedicatedFrame,
        )
      }
    }
  }

  return checkNotNull(observation)
}

fun launchNewThreadPromptRequestWithDefaultChatOpenExecutor(
  descriptor: AgentSessionProviderDescriptor,
  request: AgentPromptLaunchRequest,
  projectName: String = "Project A",
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  verifyLaunchSideEffects: suspend () -> Unit = {},
) {
  require(request.targetThreadId == null) {
    "New-thread prompt launch requires request.targetThreadId to be null"
  }

  AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      withTestServiceAndLaunch(
        sessionSourcesProvider = { listOf(descriptor.sessionSource) },
        projectEntriesProvider = { listOf(openTestProjectEntry(request.projectPath, projectName)) },
      ) { service, launchService ->
        service.refresh()
        waitForCondition(timeoutMs = PROMPT_LAUNCH_WAIT_TIMEOUT_MS) {
          val project = service.state.value.projects.firstOrNull { project -> project.path == request.projectPath }
                        ?: return@waitForCondition false
          project.providerLoadStates[descriptor.provider] == AgentSessionProviderLoadState.LOADED
        }

        val result = CompletableDeferred<AgentPromptLaunchResult>()
        launchService.createNewSession(
          path = request.projectPath,
          provider = request.provider,
          mode = request.launchMode,
          entryPoint = AgentWorkbenchEntryPoint.PROMPT,
          initialMessageRequest = request.initialMessageRequest,
          preferredDedicatedFrame = request.preferredDedicatedFrame,
          openedChatHandler = openedChatHandler,
          promptLaunchResolved = { launchResult -> result.complete(launchResult) },
          generationSettings = request.generationSettings,
          extraEnvVariables = request.containerSessionEnvVariables,
          extraCommandArgs = request.containerSessionExtraArgs,
        )
        val launchResult = withTimeout(PROMPT_LAUNCH_WAIT_TIMEOUT_MS.milliseconds) { result.await() }
        assertThat(launchResult.launched).isTrue()
        assertThat(launchResult.error).isNull()

        verifyLaunchSideEffects()
      }
    }
  }
}

internal class RecordingChatOpenExecutor(
  private val onOpenChat: (suspend (OpenChatRequest, Int) -> Unit)? = null,
  private val onOpenNewChat: (suspend (OpenNewChatRequest, Int) -> Unit)? = null,
) : AgentSessionChatOpenExecutor {
  val openChatCalls: AtomicInteger = AtomicInteger(0)
  val openNewChatCalls: AtomicInteger = AtomicInteger(0)
  val openChatRequests: CopyOnWriteArrayList<OpenChatRequest> = CopyOnWriteArrayList()
  val lastOpenChatRequest: AtomicReference<OpenChatRequest?> = AtomicReference(null)
  val lastOpenNewChatRequest: AtomicReference<OpenNewChatRequest?> = AtomicReference(null)
  private val openChatCallsFlow: MutableStateFlow<Int> = MutableStateFlow(0)
  private val openNewChatCallsFlow: MutableStateFlow<Int> = MutableStateFlow(0)

  suspend fun awaitOpenChatCalls(expected: Int, timeoutMs: Long = PROMPT_LAUNCH_WAIT_TIMEOUT_MS) {
    withTimeout(timeoutMs.milliseconds) {
      openChatCallsFlow.first { calls -> calls >= expected }
    }
  }

  suspend fun awaitOpenNewChatCalls(expected: Int, timeoutMs: Long = PROMPT_LAUNCH_WAIT_TIMEOUT_MS) {
    withTimeout(timeoutMs.milliseconds) {
      openNewChatCallsFlow.first { calls -> calls >= expected }
    }
  }

  override suspend fun openChat(
    normalizedPath: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    launchSpecOverride: AgentSessionTerminalLaunchSpec?,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
    launchMode: AgentSessionLaunchMode?,
    generationSettings: AgentPromptGenerationSettings,
  ) {
    val request = OpenChatRequest(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride,
      postStartDispatchSteps = initialMessageDispatchPlan.postStartDispatchSteps,
      initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
      launchMode = launchMode,
      generationSettings = generationSettings,
    )
    val callIndex = openChatCalls.incrementAndGet()
    openChatRequests.add(request)
    lastOpenChatRequest.set(request)
    openChatCallsFlow.value = callIndex
    onOpenChat?.invoke(request, callIndex)
  }

  override suspend fun openNewChat(
    normalizedPath: String,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
    launchMode: AgentSessionLaunchMode?,
    generationSettings: AgentPromptGenerationSettings,
    preferredDedicatedFrame: Boolean?,
    openedChatHandler: (suspend (Project, VirtualFile) -> Unit)?,
    threadTitle: String?,
  ) {
    val request = OpenNewChatRequest(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride,
      postStartDispatchSteps = initialMessageDispatchPlan.postStartDispatchSteps,
      initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
      launchMode = launchMode,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
    )
    val callIndex = openNewChatCalls.incrementAndGet()
    lastOpenNewChatRequest.set(request)
    openNewChatCallsFlow.value = callIndex
    onOpenNewChat?.invoke(request, callIndex)
    openedChatHandler?.invoke(ProjectManager.getInstance().defaultProject, LightVirtualFile("opened-chat-$callIndex"))
  }
}

internal data class OpenChatRequest(
  @JvmField val normalizedPath: String,
  @JvmField val thread: AgentSessionThread,
  @JvmField val subAgent: AgentSubAgent?,
  @JvmField val launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialMessageToken: String?,
  @JvmField val launchMode: AgentSessionLaunchMode?,
  @JvmField val generationSettings: AgentPromptGenerationSettings,
) {
  val initialComposedMessage: String?
    get() = postStartDispatchSteps.singleOrNull()?.text
}

internal data class OpenNewChatRequest(
  @JvmField val normalizedPath: String,
  @JvmField val identity: String,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialMessageToken: String?,
  @JvmField val launchMode: AgentSessionLaunchMode?,
  @JvmField val generationSettings: AgentPromptGenerationSettings,
  @JvmField val preferredDedicatedFrame: Boolean?,
) {
  val initialComposedMessage: String?
    get() = postStartDispatchSteps.singleOrNull()?.text
}

data class NewThreadPromptLaunchObservation(
  @JvmField val launchResult: AgentPromptLaunchResult,
  @JvmField val normalizedPath: String,
  @JvmField val identity: String,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialMessageToken: String?,
  @JvmField val preferredDedicatedFrame: Boolean?,
)

data class ExistingThreadPromptLaunchObservation(
  @JvmField val launchResult: AgentPromptLaunchResult,
  @JvmField val normalizedPath: String,
  @JvmField val thread: AgentSessionThread,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialMessageToken: String?,
)

private const val PROMPT_LAUNCH_WAIT_TIMEOUT_MS: Long = 20_000
