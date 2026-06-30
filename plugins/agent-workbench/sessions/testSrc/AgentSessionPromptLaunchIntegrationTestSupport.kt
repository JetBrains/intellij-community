// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.platform.ai.agent.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaceId
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.service.AgentSessionThreadViewOpenExecutor
import com.intellij.agent.workbench.sessions.service.DeferredAgentSessionThreadViewOpenResult
import com.intellij.agent.workbench.thread.view.AgentThreadViewDeferredStartContent
import com.intellij.agent.workbench.thread.view.AgentThreadViewDeferredStartState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryPlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryStatus
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPendingSessionMetadata
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
  val threadViewOpenExecutor = RecordingThreadViewOpenExecutor()
  AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      withTestServiceAndLaunch(
        sessionSourcesProvider = { listOf(descriptor.sessionSource) },
        projectEntriesProvider = { listOf(openTestProjectEntry(projectPath, projectName)) },
        threadViewOpenExecutor = threadViewOpenExecutor,
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
        threadViewOpenExecutor.awaitOpenThreadViewCalls(1)

        val openRequest = checkNotNull(threadViewOpenExecutor.lastOpenThreadViewRequest.get())
        val initialMessagePlan = descriptor.buildInitialMessagePlan(request.initialMessageRequest)
        val expectedSteps = descriptor.buildPostStartDispatchSteps(initialMessagePlan)

        assertThat(threadViewOpenExecutor.openNewThreadViewCalls.get()).isZero()
        assertThat(openRequest.normalizedPath).isEqualTo(projectPath)
        assertThat(openRequest.thread.id).isEqualTo(threadId)
        assertThat(openRequest.thread.provider).isEqualTo(provider)
        assertThat(openRequest.subAgent).isNull()
        assertThat(openRequest.startupLaunchSpecOverride).isNull()
        assertThat(openRequest.postStartDispatchSteps).containsExactlyElementsOf(expectedSteps)
        assertThat(openRequest.initialMessageToken).isNotNull()
        assertThat(openRequest.initialPromptMessage).isEqualTo(initialMessagePlan.message)
      }
    }
  }
}

fun assertExistingThreadLaunchUsesNoInitialPromptDelivery(
  descriptor: AgentSessionProviderDescriptor,
  request: AgentPromptLaunchRequest,
  projectPath: String,
  threadId: String,
  projectName: String = "Project A",
) {
  val provider = descriptor.provider
  val threadViewOpenExecutor = RecordingThreadViewOpenExecutor()
  AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      withTestServiceAndLaunch(
        sessionSourcesProvider = { listOf(descriptor.sessionSource) },
        projectEntriesProvider = { listOf(openTestProjectEntry(projectPath, projectName)) },
        threadViewOpenExecutor = threadViewOpenExecutor,
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
        threadViewOpenExecutor.awaitOpenThreadViewCalls(1)

        val openRequest = checkNotNull(threadViewOpenExecutor.lastOpenThreadViewRequest.get())

        assertThat(threadViewOpenExecutor.openNewThreadViewCalls.get()).isZero()
        assertThat(openRequest.normalizedPath).isEqualTo(projectPath)
        assertThat(openRequest.thread.id).isEqualTo(threadId)
        assertThat(openRequest.thread.provider).isEqualTo(provider)
        assertThat(openRequest.subAgent).isNull()
        assertThat(openRequest.startupLaunchSpecOverride).isNull()
        assertThat(openRequest.postStartDispatchSteps).isEmpty()
        assertThat(openRequest.initialMessageToken).isNull()
        assertThat(openRequest.initialPromptMessage).isNull()
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
  expectInitialMessageToken: Boolean = true,
): ExistingThreadPromptLaunchObservation {
  val provider = descriptor.provider
  val threadViewOpenExecutor = RecordingThreadViewOpenExecutor()
  var observation: ExistingThreadPromptLaunchObservation? = null
  AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      withTestServiceAndLaunch(
        sessionSourcesProvider = { listOf(descriptor.sessionSource) },
        projectEntriesProvider = { listOf(openTestProjectEntry(projectPath, projectName)) },
        threadViewOpenExecutor = threadViewOpenExecutor,
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
        threadViewOpenExecutor.awaitOpenThreadViewCalls(1)

        val openRequest = checkNotNull(threadViewOpenExecutor.lastOpenThreadViewRequest.get())
        val initialMessagePlan = descriptor.buildInitialMessagePlan(request.initialMessageRequest)

        assertThat(threadViewOpenExecutor.openNewThreadViewCalls.get()).isZero()
        assertThat(openRequest.normalizedPath).isEqualTo(projectPath)
        assertThat(openRequest.thread.id).isEqualTo(threadId)
        assertThat(openRequest.thread.provider).isEqualTo(provider)
        assertThat(openRequest.subAgent).isNull()
        assertThat(openRequest.startupLaunchSpecOverride).isNotNull()
        assertThat(openRequest.postStartDispatchSteps).isEmpty()
        assertThat(openRequest.initialPromptMessage).isEqualTo(initialMessagePlan.message)
        if (expectInitialMessageToken) {
          assertThat(openRequest.initialMessageToken).isNotNull()
        }
        else {
          assertThat(openRequest.initialMessageToken).isNull()
        }
        observation = ExistingThreadPromptLaunchObservation(
          launchResult = result,
          normalizedPath = openRequest.normalizedPath,
          thread = openRequest.thread,
          startupLaunchSpecOverride = openRequest.startupLaunchSpecOverride,
          postStartDispatchSteps = openRequest.postStartDispatchSteps,
          initialPromptMessage = openRequest.initialPromptMessage,
          initialMessageToken = openRequest.initialMessageToken,
          launchProfileId = openRequest.launchProfileId,
          launchTargetId = openRequest.launchTargetId,
          surfaceId = openRequest.surfaceId,
        )
      }
    }
  }

  return checkNotNull(observation)
}

fun assertNewThreadPromptLaunchOpensNewThreadView(
  descriptor: AgentSessionProviderDescriptor,
  request: AgentPromptLaunchRequest,
  projectName: String = "Project A",
): NewThreadPromptLaunchObservation {
  require(request.targetThreadId == null) {
    "New-thread prompt launch assertions require request.targetThreadId to be null"
  }

  val threadViewOpenExecutor = RecordingThreadViewOpenExecutor()
  var observation: NewThreadPromptLaunchObservation? = null

  AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      withTestServiceAndLaunch(
        sessionSourcesProvider = { listOf(descriptor.sessionSource) },
        projectEntriesProvider = { listOf(openTestProjectEntry(request.projectPath, projectName)) },
        threadViewOpenExecutor = threadViewOpenExecutor,
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
        threadViewOpenExecutor.awaitOpenNewThreadViewCalls(1)

        assertThat(threadViewOpenExecutor.openThreadViewCalls.get()).isZero()
        val openRequest = checkNotNull(threadViewOpenExecutor.lastOpenNewThreadViewRequest.get())
        observation = NewThreadPromptLaunchObservation(
          launchResult = result,
          normalizedPath = openRequest.normalizedPath,
          identity = openRequest.identity,
          launchSpec = openRequest.launchSpec,
          startupLaunchSpecOverride = openRequest.startupLaunchSpecOverride,
          postStartDispatchSteps = openRequest.postStartDispatchSteps,
          initialPromptMessage = openRequest.initialPromptMessage,
          initialMessageToken = openRequest.initialMessageToken,
          initialPromptDeliveryStatus = openRequest.initialPromptDeliveryStatus,
          initialPromptDeliveryChannel = openRequest.initialPromptDeliveryChannel,
          launchProfileId = openRequest.launchProfileId,
          launchTargetId = openRequest.launchTargetId,
          surfaceId = openRequest.surfaceId,
          preferredDedicatedFrame = openRequest.preferredDedicatedFrame,
        )
      }
    }
  }

  return checkNotNull(observation)
}

fun launchNewThreadPromptRequestWithDefaultThreadViewOpenExecutor(
  descriptor: AgentSessionProviderDescriptor,
  request: AgentPromptLaunchRequest,
  projectName: String = "Project A",
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
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
          launchProfileId = request.launchProfileId,
          launchTargetId = request.launchTargetId,
          surfaceId = AgentSessionSurfaceId.fromOrNull(request.surfaceId),
          entryPoint = AgentWorkbenchEntryPoint.PROMPT,
          initialMessageRequest = request.initialMessageRequest,
          preferredDedicatedFrame = request.preferredDedicatedFrame,
          openedThreadViewHandler = openedThreadViewHandler,
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

internal class RecordingThreadViewOpenExecutor(
  private val onOpenThreadView: (suspend (OpenThreadViewRequest, Int) -> Unit)? = null,
  private val onOpenNewThreadView: (suspend (OpenNewThreadViewRequest, Int) -> Unit)? = null,
  private val onOpenPreparingNewThreadView: (suspend (OpenPreparingNewThreadViewRequest, Int) -> Unit)? = null,
) : AgentSessionThreadViewOpenExecutor {
  val openThreadViewCalls: AtomicInteger = AtomicInteger(0)
  val openNewThreadViewCalls: AtomicInteger = AtomicInteger(0)
  val openPreparingNewThreadViewCalls: AtomicInteger = AtomicInteger(0)
  val failPreparingNewThreadViewCalls: AtomicInteger = AtomicInteger(0)
  val openThreadViewRequests: CopyOnWriteArrayList<OpenThreadViewRequest> = CopyOnWriteArrayList()
  val lastOpenThreadViewRequest: AtomicReference<OpenThreadViewRequest?> = AtomicReference(null)
  val lastOpenThreadViewHandler: AtomicReference<(suspend (Project, VirtualFile) -> Unit)?> = AtomicReference(null)
  val lastOpenNewThreadViewRequest: AtomicReference<OpenNewThreadViewRequest?> = AtomicReference(null)
  val lastOpenPreparingNewThreadViewRequest: AtomicReference<OpenPreparingNewThreadViewRequest?> = AtomicReference(null)
  val lastFailPreparingNewThreadViewMessage: AtomicReference<String?> = AtomicReference(null)
  private val openThreadViewCallsFlow: MutableStateFlow<Int> = MutableStateFlow(0)
  private val openNewThreadViewCallsFlow: MutableStateFlow<Int> = MutableStateFlow(0)
  private val openPreparingNewThreadViewCallsFlow: MutableStateFlow<Int> = MutableStateFlow(0)

  suspend fun awaitOpenThreadViewCalls(expected: Int, timeoutMs: Long = PROMPT_LAUNCH_WAIT_TIMEOUT_MS) {
    withTimeout(timeoutMs.milliseconds) {
      openThreadViewCallsFlow.first { calls -> calls >= expected }
    }
  }

  suspend fun awaitOpenNewThreadViewCalls(expected: Int, timeoutMs: Long = PROMPT_LAUNCH_WAIT_TIMEOUT_MS) {
    withTimeout(timeoutMs.milliseconds) {
      openNewThreadViewCallsFlow.first { calls -> calls >= expected }
    }
  }

  suspend fun awaitOpenPreparingNewThreadViewCalls(expected: Int, timeoutMs: Long = PROMPT_LAUNCH_WAIT_TIMEOUT_MS) {
    withTimeout(timeoutMs.milliseconds) {
      openPreparingNewThreadViewCallsFlow.first { calls -> calls >= expected }
    }
  }

  override suspend fun openThreadView(
    normalizedPath: String,
    projectDirectory: String?,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    launchSpecOverride: AgentSessionTerminalLaunchSpec?,
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
    launchMode: AgentSessionLaunchMode?,
    launchProfileId: String?,
    launchTargetId: String?,
    surfaceId: AgentSessionSurfaceId?,
    generationSettings: AgentPromptGenerationSettings,
    openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)?,
  ) {
    val request = OpenThreadViewRequest(
      normalizedPath = normalizedPath,
      projectDirectory = projectDirectory,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride,
      postStartDispatchSteps = initialMessageDispatchPlan.postStartDispatchSteps,
      initialPromptMessage = initialMessageDispatchPlan.promptRecord?.message,
      initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
      initialPromptDeliveryStatus = initialMessageDispatchPlan.promptRecord?.deliveryStatus,
      initialPromptDeliveryChannel = initialMessageDispatchPlan.promptRecord?.deliveryChannel,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
    )
    val callIndex = openThreadViewCalls.incrementAndGet()
    openThreadViewRequests.add(request)
    lastOpenThreadViewRequest.set(request)
    lastOpenThreadViewHandler.set(openedThreadViewHandler)
    openThreadViewCallsFlow.value = callIndex
    onOpenThreadView?.invoke(request, callIndex)
  }

  override suspend fun openNewThreadView(
    normalizedPath: String,
    projectDirectory: String?,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
    launchMode: AgentSessionLaunchMode?,
    launchProfileId: String?,
    launchTargetId: String?,
    surfaceId: AgentSessionSurfaceId?,
    generationSettings: AgentPromptGenerationSettings,
    preferredDedicatedFrame: Boolean?,
    openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)?,
    threadTitle: String?,
  ) {
    val request = OpenNewThreadViewRequest(
      normalizedPath = normalizedPath,
      projectDirectory = projectDirectory,
      identity = identity,
      launchSpec = launchSpec,
      startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride,
      postStartDispatchSteps = initialMessageDispatchPlan.postStartDispatchSteps,
      initialPromptMessage = initialMessageDispatchPlan.promptRecord?.message,
      initialMessageToken = initialMessageDispatchPlan.initialMessageToken,
      initialPromptDeliveryStatus = initialMessageDispatchPlan.promptRecord?.deliveryStatus,
      initialPromptDeliveryChannel = initialMessageDispatchPlan.promptRecord?.deliveryChannel,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
    )
    val callIndex = openNewThreadViewCalls.incrementAndGet()
    lastOpenNewThreadViewRequest.set(request)
    openNewThreadViewCallsFlow.value = callIndex
    onOpenNewThreadView?.invoke(request, callIndex)
    openedThreadViewHandler?.invoke(ProjectManager.getInstance().defaultProject, LightVirtualFile("opened-threadView-$callIndex"))
  }

  override suspend fun openPreparingNewThreadView(
    normalizedPath: String,
    projectDirectory: String?,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    launchMode: AgentSessionLaunchMode?,
    launchProfileId: String?,
    launchTargetId: String?,
    surfaceId: AgentSessionSurfaceId?,
    generationSettings: AgentPromptGenerationSettings,
    preferredDedicatedFrame: Boolean?,
    openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)?,
    threadTitle: String?,
    waitingState: AgentThreadViewDeferredStartState,
    deferredStartContentProvider: ((Project) -> AgentThreadViewDeferredStartContent)?,
  ): DeferredAgentSessionThreadViewOpenResult {
    val request = OpenPreparingNewThreadViewRequest(
      normalizedPath = normalizedPath,
      projectDirectory = projectDirectory,
      identity = identity,
      launchSpec = launchSpec,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
      waitingState = waitingState,
      hasDeferredStartContentProvider = deferredStartContentProvider != null,
    )
    val callIndex = openPreparingNewThreadViewCalls.incrementAndGet()
    lastOpenPreparingNewThreadViewRequest.set(request)
    openPreparingNewThreadViewCallsFlow.value = callIndex
    onOpenPreparingNewThreadView?.invoke(request, callIndex)
    val project = ProjectManager.getInstance().defaultProject
    val file = LightVirtualFile("preparing-threadView-$callIndex")
    openedThreadViewHandler?.invoke(project, file)
    return DeferredAgentSessionThreadViewOpenResult(project = project, file = file)
  }

  override suspend fun completePreparingNewThreadView(
    openedThreadView: DeferredAgentSessionThreadViewOpenResult,
    projectPath: String,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    launchMode: AgentSessionLaunchMode?,
    launchProfileId: String?,
    launchTargetId: String?,
    surfaceId: AgentSessionSurfaceId?,
    generationSettings: AgentPromptGenerationSettings,
    preferredDedicatedFrame: Boolean?,
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
    threadTitle: String,
    pendingMetadata: AgentPendingSessionMetadata?,
  ) {
    openNewThreadView(
      normalizedPath = projectPath,
      projectDirectory = null,
      identity = identity,
      launchSpec = launchSpec,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
      openedThreadViewHandler = null,
      threadTitle = threadTitle,
    )
  }

  override suspend fun failPreparingNewThreadView(
    openedThreadView: DeferredAgentSessionThreadViewOpenResult,
    title: String,
    message: String?,
  ) {
    failPreparingNewThreadViewCalls.incrementAndGet()
    lastFailPreparingNewThreadViewMessage.set(message)
  }
}

internal data class OpenThreadViewRequest(
  @JvmField val normalizedPath: String,
  @JvmField val projectDirectory: String?,
  @JvmField val thread: AgentSessionThread,
  @JvmField val subAgent: AgentSubAgent?,
  @JvmField val launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialPromptMessage: String?,
  @JvmField val initialMessageToken: String?,
  @JvmField val initialPromptDeliveryStatus: AgentInitialPromptDeliveryStatus?,
  @JvmField val initialPromptDeliveryChannel: AgentInitialPromptDeliveryChannel?,
  @JvmField val launchMode: AgentSessionLaunchMode?,
  @JvmField val launchProfileId: String?,
  @JvmField val launchTargetId: String?,
  val surfaceId: AgentSessionSurfaceId?,
  @JvmField val generationSettings: AgentPromptGenerationSettings,
) {
  val initialComposedMessage: String?
    get() = initialPromptMessage ?: postStartDispatchSteps.singleOrNull()?.text
}

internal data class OpenNewThreadViewRequest(
  @JvmField val normalizedPath: String,
  @JvmField val projectDirectory: String?,
  @JvmField val identity: String,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialPromptMessage: String?,
  @JvmField val initialMessageToken: String?,
  @JvmField val initialPromptDeliveryStatus: AgentInitialPromptDeliveryStatus?,
  @JvmField val initialPromptDeliveryChannel: AgentInitialPromptDeliveryChannel?,
  @JvmField val launchMode: AgentSessionLaunchMode?,
  @JvmField val launchProfileId: String?,
  @JvmField val launchTargetId: String?,
  val surfaceId: AgentSessionSurfaceId?,
  @JvmField val generationSettings: AgentPromptGenerationSettings,
  @JvmField val preferredDedicatedFrame: Boolean?,
) {
  val initialComposedMessage: String?
    get() = initialPromptMessage ?: postStartDispatchSteps.singleOrNull()?.text
}

internal data class OpenPreparingNewThreadViewRequest(
  @JvmField val normalizedPath: String,
  @JvmField val projectDirectory: String?,
  @JvmField val identity: String,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val launchMode: AgentSessionLaunchMode?,
  @JvmField val launchProfileId: String?,
  @JvmField val launchTargetId: String?,
  val surfaceId: AgentSessionSurfaceId?,
  @JvmField val generationSettings: AgentPromptGenerationSettings,
  @JvmField val preferredDedicatedFrame: Boolean?,
  @JvmField val waitingState: AgentThreadViewDeferredStartState,
  @JvmField val hasDeferredStartContentProvider: Boolean,
)

data class NewThreadPromptLaunchObservation(
  @JvmField val launchResult: AgentPromptLaunchResult,
  @JvmField val normalizedPath: String,
  @JvmField val identity: String,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialPromptMessage: String?,
  @JvmField val initialMessageToken: String?,
  @JvmField val initialPromptDeliveryStatus: AgentInitialPromptDeliveryStatus?,
  @JvmField val initialPromptDeliveryChannel: AgentInitialPromptDeliveryChannel?,
  @JvmField val launchProfileId: String?,
  @JvmField val launchTargetId: String?,
  val surfaceId: AgentSessionSurfaceId?,
  @JvmField val preferredDedicatedFrame: Boolean?,
)

data class ExistingThreadPromptLaunchObservation(
  @JvmField val launchResult: AgentPromptLaunchResult,
  @JvmField val normalizedPath: String,
  @JvmField val thread: AgentSessionThread,
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec?,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep>,
  @JvmField val initialPromptMessage: String?,
  @JvmField val initialMessageToken: String?,
  @JvmField val launchProfileId: String?,
  @JvmField val launchTargetId: String?,
  val surfaceId: AgentSessionSurfaceId?,
)

private const val PROMPT_LAUNCH_WAIT_TIMEOUT_MS: Long = 20_000
