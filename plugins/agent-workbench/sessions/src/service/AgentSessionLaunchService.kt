// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-dedicated-frame.spec.md
// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.chat.openChat
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchSpecs
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchError
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchResult
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.isPlanModeCommand
import com.intellij.agent.workbench.sessions.core.providers.stripPlanModePrefix
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTargetKind
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.frame.AGENT_SESSIONS_TOOL_WINDOW_ID
import com.intellij.agent.workbench.sessions.frame.AGENT_WORKBENCH_DEDICATED_FRAME_TYPE_ID
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.AgentSessionsTreeUiStateService
import com.intellij.agent.workbench.sessions.state.SessionsTreeUiState
import com.intellij.agent.workbench.sessions.util.SingleFlightActionGate
import com.intellij.agent.workbench.sessions.util.SingleFlightPolicy
import com.intellij.agent.workbench.sessions.util.SingleFlightProgressRequest
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionNewIdentity
import com.intellij.agent.workbench.sessions.util.parseAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.resolveAgentSessionId
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.project.ProjectStoreOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<AgentSessionLaunchService>()

private const val SUPPRESS_BRANCH_MISMATCH_DIALOG_KEY = "agent.workbench.suppress.branch.mismatch.dialog"
private const val OPEN_PROJECT_ACTION_KEY_PREFIX = "project-open"
private const val OPEN_DEDICATED_FRAME_ACTION_KEY_PREFIX = "dedicated-frame-open"
private const val CREATE_SESSION_ACTION_KEY_PREFIX = "session-create"
private const val OPEN_THREAD_ACTION_KEY_PREFIX = "thread-open"
private const val OPEN_SUB_AGENT_ACTION_KEY_PREFIX = "subagent-open"
private const val MAX_STARTUP_COMMAND_BYTES = 24 * 1024

enum class OpenThreadLaunchOrigin(val keySuffix: String) {
  USER_OPEN(""),
  PROMPT_LAUNCH(":prompt-launch"),
}

internal interface AgentSessionChatOpenExecutor {
  suspend fun openChat(
    normalizedPath: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    launchSpecOverride: AgentSessionTerminalLaunchSpec?,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
  )

  suspend fun openNewChat(
    normalizedPath: String,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
    preferredDedicatedFrame: Boolean?,
  )
}

private object DefaultAgentSessionChatOpenExecutor : AgentSessionChatOpenExecutor {
  override suspend fun openChat(
    normalizedPath: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    launchSpecOverride: AgentSessionTerminalLaunchSpec?,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
  ) {
    openAgentSessionChat(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
    )
  }

  override suspend fun openNewChat(
    normalizedPath: String,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
    preferredDedicatedFrame: Boolean?,
  ) {
    openAgentSessionNewChat(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      preferredDedicatedFrame = preferredDedicatedFrame,
    )
  }
}

@Service(Service.Level.APP)
internal class AgentSessionLaunchService(
  private val serviceScope: CoroutineScope,
  private val stateStore: AgentSessionsStateStore,
  private val syncService: AgentSessionRefreshService,
  private val treeUiState: SessionsTreeUiState,
  private val chatOpenExecutor: AgentSessionChatOpenExecutor = DefaultAgentSessionChatOpenExecutor,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    stateStore = service<AgentSessionsStateStore>(),
    syncService = service<AgentSessionRefreshService>(),
    treeUiState = service<AgentSessionsTreeUiStateService>(),
    chatOpenExecutor = DefaultAgentSessionChatOpenExecutor,
  )

  private val actionGate = SingleFlightActionGate()

  fun openOrFocusProject(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    launchDropAction(
      key = buildOpenProjectActionKey(normalizedPath),
      droppedActionMessage = "Dropped duplicate open project action for $normalizedPath",
    ) {
      AgentWorkbenchTelemetry.logProjectFocusRequested(entryPoint)
      openOrFocusProjectInternal(normalizedPath)
    }
  }

  fun openOrFocusDedicatedFrame(entryPoint: AgentWorkbenchEntryPoint, currentProject: Project? = null) {
    launchDropAction(
      key = OPEN_DEDICATED_FRAME_ACTION_KEY_PREFIX,
      droppedActionMessage = "Dropped duplicate open dedicated frame action",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
    ) {
      AgentWorkbenchTelemetry.logDedicatedFrameFocusRequested(entryPoint)
      openOrFocusDedicatedFrameInternal()
    }
  }

  fun openChatThread(
    path: String,
    thread: AgentSessionThread,
    entryPoint: AgentWorkbenchEntryPoint,
    currentProject: Project? = null,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
    initialMessageRequest: AgentPromptInitialMessageRequest? = null,
    singleFlightPolicy: SingleFlightPolicy = SingleFlightPolicy.DROP,
    launchOrigin: OpenThreadLaunchOrigin = OpenThreadLaunchOrigin.USER_OPEN,
    promptLaunchResolved: ((AgentPromptLaunchResult) -> Unit)? = null,
  ) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    AgentSessionProviders.find(thread.provider)?.onConversationOpened()
    syncService.prepareThreadForOpen(path = normalizedPath, provider = thread.provider, threadId = thread.id, updatedAt = thread.updatedAt)
    launchDropAction(
      key = buildOpenThreadActionKey(path = normalizedPath, thread = thread, launchOrigin = launchOrigin),
      droppedActionMessage = "Dropped duplicate open thread action for $normalizedPath:${thread.provider}:${thread.id}",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
      policy = singleFlightPolicy,
    ) {
      try {
        val worktreeBranch = stateStore.findWorktreeBranch(normalizedPath)
        val originBranch = thread.originBranch
        if (worktreeBranch != null && originBranch != null && originBranch != worktreeBranch && !isBranchMismatchDialogSuppressed()) {
          val proceed = withContext(Dispatchers.EDT) {
            showBranchMismatchDialog(originBranch, worktreeBranch)
          }
          if (!proceed) {
            promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.CANCELLED))
            return@launchDropAction
          }
        }
        val effectiveInitialMessageDispatchPlan = if (initialMessageDispatchPlan != AgentInitialMessageDispatchPlan.EMPTY) {
          initialMessageDispatchPlan
        }
        else {
          resolvePromptInitialMessageDispatchPlan(
            normalizedPath = normalizedPath,
            thread = thread,
            initialMessageRequest = initialMessageRequest,
          )
        }
        AgentWorkbenchTelemetry.logThreadOpenRequested(entryPoint, thread.provider, AgentWorkbenchTargetKind.THREAD)
        chatOpenExecutor.openChat(
          normalizedPath = normalizedPath,
          thread = thread,
          subAgent = null,
          launchSpecOverride = null,
          initialMessageDispatchPlan = effectiveInitialMessageDispatchPlan,
        )
        promptLaunchResolved?.invoke(AgentPromptLaunchResult.SUCCESS)
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.INTERNAL_ERROR))
        throw t
      }
    }
  }

  fun openChatSubAgent(
    path: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent,
    entryPoint: AgentWorkbenchEntryPoint,
    currentProject: Project? = null,
  ) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    AgentSessionProviders.find(thread.provider)?.onConversationOpened()
    launchDropAction(
      key = buildOpenSubAgentActionKey(path = normalizedPath, thread = thread, subAgent = subAgent),
      droppedActionMessage = "Dropped duplicate open sub-agent action for $normalizedPath:${thread.provider}:${thread.id}:${subAgent.id}",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
    ) {
      AgentWorkbenchTelemetry.logThreadOpenRequested(entryPoint, thread.provider, AgentWorkbenchTargetKind.SUB_AGENT)
      chatOpenExecutor.openChat(
        normalizedPath = normalizedPath,
        thread = thread,
        subAgent = subAgent,
        launchSpecOverride = null,
        initialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
      )
    }
  }

  fun createNewSession(
    path: String,
    provider: AgentSessionProvider,
    mode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
    entryPoint: AgentWorkbenchEntryPoint,
    currentProject: Project? = null,
    initialMessageRequest: AgentPromptInitialMessageRequest? = null,
    preferredDedicatedFrame: Boolean? = null,
    promptLaunchResolved: ((AgentPromptLaunchResult) -> Unit)? = null,
  ) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    AgentSessionProviders.find(provider)?.onConversationOpened()
    uiPreferencesState.updateProviderPreferencesOnLaunch(provider, mode, initialMessageRequest)
    launchDropAction(
      key = buildCreateSessionActionKey(normalizedPath, provider, mode),
      droppedActionMessage = "Dropped duplicate create session action for $normalizedPath:$provider:mode=$mode",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
      onDrop = promptLaunchResolved?.let { handler ->
        { handler(AgentPromptLaunchResult.failure(AgentPromptLaunchError.DROPPED_DUPLICATE)) }
      },
    ) {
      try {
        val descriptor = AgentSessionProviders.find(provider)
        if (descriptor == null) {
          logMissingProviderDescriptor(provider)
          syncService.appendProviderUnavailableWarning(normalizedPath, provider)
          promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
          return@launchDropAction
        }
        if (mode !in descriptor.supportedLaunchModes) {
          LOG.warn("Session provider ${provider.value} does not support launch mode $mode")
          syncService.appendProviderUnavailableWarning(normalizedPath, provider)
          promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE))
          return@launchDropAction
        }

        val createSpec = descriptor.createNewSession(path = normalizedPath, mode = mode)
        val launchSpec = AgentSessionLaunchSpecs.augment(
          projectPath = normalizedPath,
          provider = provider,
          launchSpec = createSpec.launchSpec,
        )
        val identity = createSpec.sessionId?.let { sessionId ->
          buildAgentSessionIdentity(provider, sessionId)
        } ?: buildAgentSessionNewIdentity(provider)
        val initialMessagePlan = initialMessageRequest
          ?.let(descriptor::buildInitialMessagePlan)
          ?: AgentInitialMessagePlan.EMPTY
        val initialMessageDispatchPlan = buildInitialMessageDispatchPlan(
          descriptor = descriptor,
          baseLaunchSpec = launchSpec,
          identity = identity,
          initialMessagePlan = initialMessagePlan,
        )
        logPreparedNewSessionLaunch(
          provider = provider,
          projectPath = normalizedPath,
          identity = identity,
          baseLaunchSpec = createSpec.launchSpec,
          resolvedLaunchSpec = launchSpec,
          initialMessageDispatchPlan = initialMessageDispatchPlan,
        )

        AgentWorkbenchTelemetry.logThreadCreateRequested(entryPoint, provider, mode)
        chatOpenExecutor.openNewChat(
          normalizedPath = normalizedPath,
          identity = identity,
          launchSpec = launchSpec,
          initialMessageDispatchPlan = initialMessageDispatchPlan,
          preferredDedicatedFrame = preferredDedicatedFrame,
        )
        if (AgentSessionProviders.find(provider)?.refreshPathAfterCreateNewSession == true) {
          syncService.refreshProviderForPath(path = normalizedPath, provider = provider)
        }
        promptLaunchResolved?.invoke(AgentPromptLaunchResult.SUCCESS)
      }
      if (mode !in bridge.supportedLaunchModes) {
        LOG.warn("Session provider bridge ${provider.value} does not support launch mode $mode")
        syncService.appendProviderUnavailableWarning(normalizedPath, provider)
        return@launchDropAction
      }

      val createSpec = bridge.createNewSession(path = normalizedPath, mode = mode)
      val identity = createSpec.sessionId?.let { sessionId ->
        buildAgentSessionIdentity(provider, sessionId)
      } ?: buildAgentSessionNewIdentity(provider)
      val initialMessagePlan = initialMessageRequest
        ?.let(bridge::buildInitialMessagePlan)
        ?: AgentInitialMessagePlan.EMPTY
      val initialMessageDispatchPlan = buildInitialMessageDispatchPlan(
        bridge = bridge,
        baseLaunchSpec = createSpec.launchSpec,
        identity = identity,
        initialMessagePlan = initialMessagePlan,
      )

      chatOpenExecutor.openNewChat(
        normalizedPath = normalizedPath,
        identity = identity,
        launchSpec = createSpec.launchSpec,
        initialMessageDispatchPlan = initialMessageDispatchPlan,
        preferredDedicatedFrame = preferredDedicatedFrame,
      )
      if (AgentSessionProviderBehaviors.find(provider)?.refreshPathAfterCreateNewSession == true) {
        syncService.refreshProviderForPath(path = normalizedPath, provider = provider)
      }
    }
  }

  fun launchPromptRequest(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
    fun reportPromptLaunchResolved(result: AgentPromptLaunchResult): AgentPromptLaunchResult {
      AgentWorkbenchTelemetry.logPromptLaunchResolved(request, result)
      return result
    }

    val result = run {
      val bridge = AgentSessionProviders.find(request.provider)
                   ?: return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
      if (request.launchMode !in bridge.supportedLaunchModes) {
        return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE))
      }

      val targetThreadId = request.targetThreadId?.trim()
      if (request.targetThreadId != null && targetThreadId.isNullOrEmpty()) {
        return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND))
      }

      try {
        if (targetThreadId == null) {
          createNewSession(
            path = request.projectPath,
            provider = request.provider,
            mode = request.launchMode,
            entryPoint = AgentWorkbenchEntryPoint.PROMPT,
            initialMessageRequest = request.initialMessageRequest,
            preferredDedicatedFrame = request.preferredDedicatedFrame,
            promptLaunchResolved = ::reportPromptLaunchResolved,
          )
        }
        else {
          val normalizedPath = normalizeAgentWorkbenchPath(request.projectPath)
          val targetThread = findPromptTargetThread(
            normalizedPath = normalizedPath,
            provider = request.provider,
            threadId = targetThreadId,
          ) ?: return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND))
          uiPreferencesState.updateProviderPreferencesOnLaunch(request.provider, request.launchMode, request.initialMessageRequest)

          openChatThread(
            path = normalizedPath,
            thread = targetThread,
            entryPoint = AgentWorkbenchEntryPoint.PROMPT,
            initialMessageRequest = request.initialMessageRequest,
            singleFlightPolicy = SingleFlightPolicy.RESTART_LATEST,
            launchOrigin = OpenThreadLaunchOrigin.PROMPT_LAUNCH,
            promptLaunchResolved = ::reportPromptLaunchResolved,
          )
        }
        AgentPromptLaunchResult.SUCCESS
      }
      catch (t: Throwable) {
        LOG.warn("Failed to launch prompt request for ${request.provider}:${request.projectPath}", t)
        reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.INTERNAL_ERROR))
      }
    }
    return result
  }

  private fun findPromptTargetThread(
    normalizedPath: String,
    provider: AgentSessionProvider,
    threadId: String,
  ): AgentSessionThread? {
    val stateSnapshot = stateStore.snapshot()
    stateSnapshot.projects.firstOrNull { project -> project.path == normalizedPath }
      ?.threads
      ?.firstOrNull { thread -> thread.matchesPromptTarget(provider = provider, threadId = threadId) }
      ?.let { thread -> return thread }

    stateSnapshot.projects.forEach { project ->
      project.worktrees
        .firstOrNull { worktree -> worktree.path == normalizedPath }
        ?.threads
        ?.firstOrNull { thread -> thread.matchesPromptTarget(provider = provider, threadId = threadId) }
        ?.let { thread -> return thread }
    }

    return null
  }

  private fun launchDropAction(
    key: String,
    droppedActionMessage: String,
    policy: SingleFlightPolicy = SingleFlightPolicy.DROP,
    progress: SingleFlightProgressRequest? = null,
    onDrop: (() -> Unit)? = null,
    block: suspend () -> Unit,
  ): Job? {
    return actionGate.launch(
      scope = serviceScope,
      key = key,
      policy = policy,
      progress = progress,
      onDrop = {
        LOG.debug(droppedActionMessage)
        onDrop?.invoke()
      },
      block = block,
    )
  }
}

private suspend fun openOrFocusProjectInternal(normalizedPath: String) {
  val project = openOrReuseSourceProjectByPath(normalizedPath) ?: return
  val projectUtilService = project.serviceAsync<ProjectUtilService>()
  withContext(Dispatchers.UI) {
    projectUtilService.focusProjectWindow()
  }
}

private suspend fun openOrFocusDedicatedFrameInternal() {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    focusProjectWindowAndActivateSessions(openProject)
    return
  }

  val dedicatedProjectDir = try {
    AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Failed to prepare dedicated chat frame project", e)
    return
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: return
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  focusProjectWindowAndActivateSessions(dedicatedProject)
}

private suspend fun openAgentSessionChat(
  normalizedPath: String,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec? = null,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
) {
  if (AgentChatOpenModeSettings.openInDedicatedFrame()) {
    openChatInDedicatedFrame(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
    )
    return
  }
  val openProject = openOrReuseSourceProjectByPath(normalizedPath) ?: return
  openChatInProject(
    project = openProject,
    projectPath = normalizedPath,
    thread = thread,
    subAgent = subAgent,
    launchSpecOverride = launchSpecOverride,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
  )
}

private fun buildOpenProjectActionKey(path: String): String {
  return "$OPEN_PROJECT_ACTION_KEY_PREFIX:$path"
}

private fun buildCreateSessionActionKey(path: String, provider: AgentSessionProvider, mode: AgentSessionLaunchMode): String {
  return "$CREATE_SESSION_ACTION_KEY_PREFIX:$path:$provider:mode=$mode"
}

private fun buildInitialMessageDispatchPlan(
    descriptor: AgentSessionProviderDescriptor,
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    identity: String,
    initialMessagePlan: AgentInitialMessagePlan,
): AgentInitialMessageDispatchPlan {
  val postStartDispatchSteps = buildPostStartDispatchSteps(
    provider = descriptor.provider,
    initialMessagePlan = initialMessagePlan,
  )
  if (postStartDispatchSteps.isEmpty()) {
    return AgentInitialMessageDispatchPlan.EMPTY
  }
  return AgentInitialMessageDispatchPlan(
    startupLaunchSpecOverride = buildStartupLaunchSpecOverride(
      descriptor = descriptor,
      baseLaunchSpec = baseLaunchSpec,
      initialMessagePlan = initialMessagePlan,
    ),
    postStartDispatchSteps = postStartDispatchSteps,
    initialMessageToken = buildInitialMessageToken(identity = identity, steps = postStartDispatchSteps),
  )
}

private fun buildStartupLaunchSpecOverride(
    descriptor: AgentSessionProviderDescriptor,
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
): AgentSessionTerminalLaunchSpec? {
  if (initialMessagePlan.startupPolicy != AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND) {
    return null
  }
  val prompt = initialMessagePlan.message ?: return null
  if (descriptor.provider == AgentSessionProvider.CODEX && prompt.isPlanModeCommand()) {
    LOG.debug {
      "Skipped startup prompt command override for ${descriptor.provider.value}: plan-mode commands must stay on post-start dispatch"
    }
    return null
  }
  val startupLaunchSpec = descriptor.buildLaunchSpecWithInitialPrompt(baseLaunchSpec = baseLaunchSpec, prompt = prompt) ?: return null
  val estimatedCommandSize = estimateCommandSizeBytes(startupLaunchSpec.command)
  if (estimatedCommandSize <= MAX_STARTUP_COMMAND_BYTES) {
    return startupLaunchSpec
  }
  LOG.debug {
    "Skipped startup prompt command override for ${descriptor.provider.value}: estimatedCommandSize=$estimatedCommandSize exceeds $MAX_STARTUP_COMMAND_BYTES"
  }
  return null
}

private fun logPreparedNewSessionLaunch(
  provider: AgentSessionProvider,
  projectPath: String,
  identity: String,
  baseLaunchSpec: AgentSessionTerminalLaunchSpec,
  resolvedLaunchSpec: AgentSessionTerminalLaunchSpec,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
) {
  val commandHead = resolvedLaunchSpec.command.firstOrNull() ?: "<empty>"
  val commandArgumentCount = (resolvedLaunchSpec.command.size - 1).coerceAtLeast(0)
  val hasPathKey = resolvedLaunchSpec.envVariables.keys.any(::isPathEnvironmentVariableName)
  LOG.debug {
    "Prepared new session launch(provider=${provider.value}, path=$projectPath, identity=$identity, commandHead=$commandHead, commandArgumentCount=$commandArgumentCount, envKeyCount=${resolvedLaunchSpec.envVariables.size}, hasPathKey=$hasPathKey, envAugmented=${resolvedLaunchSpec.envVariables != baseLaunchSpec.envVariables}, startupOverride=${initialMessageDispatchPlan.startupLaunchSpecOverride != null}, postStartDispatchSteps=${initialMessageDispatchPlan.postStartDispatchSteps.size})"
  }
}

private fun isPathEnvironmentVariableName(name: String): Boolean {
  return name.equals("PATH", ignoreCase = true)
}

private fun estimateCommandSizeBytes(command: List<String>): Int {
  return command.sumOf { part -> part.toByteArray().size + 1 }
}

private fun buildPostStartDispatchSteps(
  provider: AgentSessionProvider,
  initialMessagePlan: AgentInitialMessagePlan,
): List<AgentInitialMessageDispatchStep> {
  val message = initialMessagePlan.message ?: return emptyList()
  if (provider != AgentSessionProvider.CODEX || !message.isPlanModeCommand()) {
    return listOf(
      AgentInitialMessageDispatchStep(
        text = message,
        timeoutPolicy = initialMessagePlan.timeoutPolicy,
      )
    )
  }

  val steps = mutableListOf(
    AgentInitialMessageDispatchStep(
      text = "/plan",
      timeoutPolicy = initialMessagePlan.timeoutPolicy,
      completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
    )
  )
  val strippedPrompt = message.stripPlanModePrefix()
  if (strippedPrompt.isNotEmpty()) {
    steps += AgentInitialMessageDispatchStep(
      text = strippedPrompt,
      timeoutPolicy = initialMessagePlan.timeoutPolicy,
    )
  }
  return steps
}

private fun buildInitialMessageToken(identity: String, steps: List<AgentInitialMessageDispatchStep>): String {
  val sequenceKey = steps.joinToString(separator = "\u0000") { step ->
    listOf(step.text, step.timeoutPolicy.name, step.completionPolicy.name).joinToString(separator = "\u0001")
  }
  return "$identity:${sequenceKey.hashCode()}:${System.nanoTime()}"
}

private suspend fun resolvePromptInitialMessageDispatchPlan(
  normalizedPath: String,
  thread: AgentSessionThread,
  initialMessageRequest: AgentPromptInitialMessageRequest?,
): AgentInitialMessageDispatchPlan {
  if (initialMessageRequest == null) {
    return AgentInitialMessageDispatchPlan.EMPTY
  }

  val descriptor = AgentSessionProviders.find(thread.provider)
                  ?: return AgentInitialMessageDispatchPlan.EMPTY
  val initialMessagePlan = descriptor.buildInitialMessagePlan(initialMessageRequest)
  val identity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id)
  val resumeLaunchSpec = AgentSessionLaunchSpecs.resolveResume(
    projectPath = normalizedPath,
    provider = thread.provider,
    sessionId = thread.id,
  )
  return buildInitialMessageDispatchPlan(
    descriptor = descriptor,
    baseLaunchSpec = resumeLaunchSpec,
    identity = identity,
    initialMessagePlan = initialMessagePlan,
  )
}

private fun buildOpenThreadActionKey(
  path: String,
  thread: AgentSessionThread,
  launchOrigin: OpenThreadLaunchOrigin = OpenThreadLaunchOrigin.USER_OPEN,
): String {
  return "$OPEN_THREAD_ACTION_KEY_PREFIX:$path:${thread.provider}:${thread.id}${launchOrigin.keySuffix}"
}

private fun buildOpenSubAgentActionKey(path: String, thread: AgentSessionThread, subAgent: AgentSubAgent): String {
  return "$OPEN_SUB_AGENT_ACTION_KEY_PREFIX:$path:${thread.provider}:${thread.id}:${subAgent.id}"
}

private fun logMissingProviderDescriptor(provider: AgentSessionProvider) {
  LOG.warn("No session provider registered for ${provider.value}")
}

private fun dedicatedFrameOpenProgressRequest(currentProject: Project?): SingleFlightProgressRequest? {
  if (!AgentChatOpenModeSettings.openInDedicatedFrame()) return null
  return SingleFlightProgressRequest(
    project = currentOrDefaultProject(currentProject),
    title = AgentSessionsBundle.message("toolwindow.progress.opening.dedicated.frame"),
  )
}

private fun resolvePendingSessionMetadata(
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
) = parseAgentSessionIdentity(identity)
  ?.provider
  ?.let(AgentSessionProviders::find)
  ?.resolvePendingSessionMetadata(identity = identity, launchSpec = launchSpec)

private suspend fun openAgentSessionNewChat(
  normalizedPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
  preferredDedicatedFrame: Boolean?,
) {
  val title = AgentSessionsBundle.message("toolwindow.action.new.thread")
  val dedicatedFrame = preferredDedicatedFrame ?: AgentChatOpenModeSettings.openInDedicatedFrame()
  if (dedicatedFrame) {
    openNewChatInDedicatedFrame(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      title = title,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
    )
    return
  }
  val openProject = openOrReuseSourceProjectByPath(normalizedPath) ?: return
  openNewChatInProject(
    project = openProject,
    projectPath = normalizedPath,
    identity = identity,
    launchSpec = launchSpec,
    title = title,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
  )
}

private suspend fun openNewChatInDedicatedFrame(
  normalizedPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  title: String,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
) {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    openNewChatInProject(
      project = openProject,
      projectPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      title = title,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
    )
    return
  }

  val dedicatedProjectDir = try {
    AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Failed to prepare dedicated chat frame project", e)
    return
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: return
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  openNewChatInProject(
    project = dedicatedProject,
    projectPath = normalizedPath,
    identity = identity,
    launchSpec = launchSpec,
    title = title,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
  )
}

private suspend fun openNewChatInProject(
  project: Project,
  projectPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  title: String,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
) {
  val threadId = resolveAgentSessionId(identity)
  val pendingMetadata = resolvePendingSessionMetadata(identity = identity, launchSpec = launchSpec)
  openChat(
    project = project,
    projectPath = projectPath,
    threadIdentity = identity,
    shellCommand = launchSpec.command,
    shellEnvVariables = launchSpec.envVariables,
    threadId = threadId,
    threadTitle = title,
    subAgentId = null,
    threadActivity = com.intellij.agent.workbench.common.AgentThreadActivity.READY,
    pendingCreatedAtMs = pendingMetadata?.createdAtMs,
    pendingLaunchMode = pendingMetadata?.launchMode,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
  )
  focusProjectWindow(project)
}

private suspend fun openChatInDedicatedFrame(
  normalizedPath: String,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
) {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    openChatInProject(
      project = openProject,
      projectPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
    )
    return
  }

  val dedicatedProjectDir = try {
    AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
  }
  catch (e: Throwable) {
    LOG.warn("Failed to prepare dedicated chat frame project", e)
    return
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: return
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  openChatInProject(
    project = dedicatedProject,
    projectPath = normalizedPath,
    thread = thread,
    subAgent = subAgent,
    launchSpecOverride = launchSpecOverride,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
  )
}

private suspend fun openChatInProject(
  project: Project,
  projectPath: String,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
) {
  val chatOpenPayload = resolveAgentSessionChatOpenPayload(
    projectPath = projectPath,
    thread = thread,
    subAgent = subAgent,
    launchSpecOverride = launchSpecOverride,
  )
  val effectiveInitialMessageDispatchPlan = if (initialMessageDispatchPlan != AgentInitialMessageDispatchPlan.EMPTY) {
    initialMessageDispatchPlan
  }
  else {
    chatOpenPayload.initialMessageDispatchPlan
  }
  withContext(Dispatchers.EDT) {
    openChat(
      project = project,
      projectPath = projectPath,
      threadIdentity = chatOpenPayload.threadIdentity,
      shellCommand = chatOpenPayload.launchSpec.command,
      shellEnvVariables = chatOpenPayload.launchSpec.envVariables,
      threadId = chatOpenPayload.runtimeThreadId,
      threadTitle = chatOpenPayload.threadTitle,
      subAgentId = chatOpenPayload.subAgentId,
      threadActivity = thread.activity,
      initialMessageDispatchPlan = effectiveInitialMessageDispatchPlan,
    )
    focusProjectWindowSync(project)
  }
}

private suspend fun focusProjectWindow(project: Project) {
  withContext(Dispatchers.UI) {
    project.serviceAsync<ProjectUtilService>().focusProjectWindow()
  }
}

private fun focusProjectWindowSync(project: Project) {
  ProjectUtilService.getInstance(project).focusProjectWindow()
}

private suspend fun focusProjectWindowAndActivateSessions(project: Project) {
  withContext(Dispatchers.UI) {
    project.serviceAsync<ProjectUtilService>().focusProjectWindow()
    ToolWindowManager.getInstance(project).getToolWindow(AGENT_SESSIONS_TOOL_WINDOW_ID)?.activate(null)
  }
}

private suspend fun openDedicatedFrameProject(dedicatedProjectDir: Path): Project? {
  return openProject(
    normalizedPath = dedicatedProjectDir.invariantSeparatorsPathString,
    options = OpenProjectTask {
      forceOpenInNewFrame = true
      runConfigurators = false
      runConversionBeforeOpen = false
      preloadServices = false
      preventIprLookup = true
      showWelcomeScreen = false
      createModule = false
      projectFrameTypeId = AGENT_WORKBENCH_DEDICATED_FRAME_TYPE_ID
    },
  )
}

private suspend fun openProject(
  normalizedPath: String,
  options: OpenProjectTask = OpenProjectTask(),
): Project? {
  val projectPath = parseAgentWorkbenchPathOrNull(normalizedPath) ?: return null
  findOpenProject(normalizedPath)?.let { return it }

  try {
    return (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(
      projectIdentityFile = projectPath,
      options = options,
    )
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Failed to open project at $normalizedPath", e)
    return null
  }
}

private fun findOpenProject(normalizedPath: String): Project? {
  return ProjectManager.getInstance().openProjects.firstOrNull { project ->
    val projectPath = (project as? ProjectStoreOwner)
      ?.componentStore
      ?.storeDescriptor
      ?.presentableUrl
      ?.invariantSeparatorsPathString
      ?: return@firstOrNull false
    projectPath == normalizedPath
  }
}

private fun isBranchMismatchDialogSuppressed(): Boolean {
  return PropertiesComponent.getInstance().getBoolean(SUPPRESS_BRANCH_MISMATCH_DIALOG_KEY, false)
}

private fun showBranchMismatchDialog(originBranch: String, currentBranch: String): Boolean {
  return MessageDialogBuilder
    .okCancel(
      AgentSessionsBundle.message("toolwindow.thread.branch.mismatch.dialog.title"),
      AgentSessionsBundle.message("toolwindow.thread.branch.mismatch.dialog.message", originBranch, currentBranch),
    )
    .yesText(AgentSessionsBundle.message("toolwindow.thread.branch.mismatch.dialog.continue"))
    .doNotAsk(object : DoNotAskOption.Adapter() {
      override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
        if (isSelected) {
          PropertiesComponent.getInstance().setValue(SUPPRESS_BRANCH_MISMATCH_DIALOG_KEY, true)
        }
      }
    })
    .asWarning()
    .ask(null as Project?)
}

private fun AgentSessionThread.matchesPromptTarget(provider: AgentSessionProvider, threadId: String): Boolean {
  return this.provider == provider && !archived && id == threadId
}
