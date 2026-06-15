// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/frame/agent-dedicated-frame.spec.md
// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md
// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/core/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.chat.AgentChatDeferredStartPhase
import com.intellij.agent.workbench.chat.AgentChatDeferredStartState
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.collectOpenPendingAgentChatTabsByPath
import com.intellij.agent.workbench.chat.openChat
import com.intellij.agent.workbench.chat.rebindOpenPendingAgentChatTabs
import com.intellij.agent.workbench.chat.serializeAgentChatLaunchMode
import com.intellij.agent.workbench.chat.updateAgentChatDeferredStartState
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_POST_WINDOW_MS
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_PRE_WINDOW_MS
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchIntent
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchOperation
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchPlanner
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionPlannedLaunch
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOptionTarget
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.isBlockedForExistingThreadPlanMode
import com.intellij.agent.workbench.sessions.core.providers.resolveEffectiveProviderOptionIds
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTargetKind
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.frame.AGENT_SESSIONS_TOOL_WINDOW_ID
import com.intellij.agent.workbench.sessions.frame.AGENT_WORKBENCH_DEDICATED_FRAME_TYPE_ID
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.util.SingleFlightActionGate
import com.intellij.agent.workbench.sessions.util.SingleFlightPolicy
import com.intellij.agent.workbench.sessions.util.SingleFlightProgressRequest
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionNewIdentity
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.agent.workbench.sessions.util.parseAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.resolveAgentSessionId
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.project.ProjectStoreOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.milliseconds

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
    launchMode: AgentSessionLaunchMode?,
    generationSettings: AgentPromptGenerationSettings,
  )

  suspend fun openNewChat(
    normalizedPath: String,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
    launchMode: AgentSessionLaunchMode?,
    generationSettings: AgentPromptGenerationSettings,
    preferredDedicatedFrame: Boolean?,
    openedChatHandler: (suspend (Project, VirtualFile) -> Unit)?,
    threadTitle: String? = null,
  )
}

data class AgentDeferredNewSessionLaunchResult(
  @JvmField val handle: AgentDeferredNewSessionHandle? = null,
  @JvmField val error: AgentPromptLaunchError? = null,
)

interface AgentDeferredNewSessionHandle {
  val file: VirtualFile

  suspend fun start(initialMessageRequest: AgentPromptInitialMessageRequest? = null)

  suspend fun completeWithoutStart(title: @Nls String, message: @Nls String? = null)

  suspend fun fail(title: @Nls String, message: @Nls String? = null)
}

private data class ArchivedThreadOpenResolution(
  @JvmField val thread: AgentSessionThread,
  @JvmField val refreshDelayMs: Long? = null,
) {
  val unarchived: Boolean
    get() = refreshDelayMs != null
}

private object DefaultAgentSessionChatOpenExecutor : AgentSessionChatOpenExecutor {
  override suspend fun openChat(
    normalizedPath: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    launchSpecOverride: AgentSessionTerminalLaunchSpec?,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
    launchMode: AgentSessionLaunchMode?,
    generationSettings: AgentPromptGenerationSettings,
  ) {
    openAgentSessionChat(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      generationSettings = generationSettings,
    )
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
    openAgentSessionNewChat(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
      openedChatHandler = openedChatHandler,
      threadTitle = threadTitle,
    )
  }
}

@Service(Service.Level.APP)
class AgentSessionLaunchService internal constructor(
  private val serviceScope: CoroutineScope,
  private val stateStore: AgentSessionsStateStore,
  private val syncService: AgentSessionRefreshService,
  private val uiPreferencesState: AgentSessionUiPreferencesStateService = AgentSessionUiPreferencesStateService(),
  private val providerSettingsService: AgentSessionProviderSettingsService = service(),
  private val chatOpenExecutor: AgentSessionChatOpenExecutor = DefaultAgentSessionChatOpenExecutor,
  private val openPendingAgentChatTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatPendingTabSnapshot>> =
    ::collectOpenPendingAgentChatTabsByPath,
  private val openAgentChatPendingTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingAgentChatTabs,
  private val archivedSessionsRefreshIfLoaded: () -> Unit = {},
  private val branchMismatchConfirmation: suspend (Project?, String, String) -> Boolean = { project, originBranch, currentBranch ->
    showBranchMismatchDialog(project, originBranch, currentBranch)
  },
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    stateStore = service<AgentSessionsStateStore>(),
    syncService = service<AgentSessionRefreshService>(),
    uiPreferencesState = service<AgentSessionUiPreferencesStateService>(),
    providerSettingsService = service<AgentSessionProviderSettingsService>(),
    chatOpenExecutor = DefaultAgentSessionChatOpenExecutor,
    archivedSessionsRefreshIfLoaded = { service<AgentArchivedSessionsService>().refreshIfLoaded() },
  )

  private val actionGate = SingleFlightActionGate()

  private suspend fun isProviderCliAvailableForLaunch(
    provider: AgentSessionProvider,
    descriptor: AgentSessionProviderDescriptor,
    currentProject: Project?,
  ): Boolean {
    if (currentProject != null && !currentProject.isDisposed) {
      return currentProject.serviceAsync<AgentSessionProviderAvailabilityService>().refreshNow(listOf(descriptor))[provider] == true
    }
    return descriptor.isCliAvailable()
  }

  fun openOrFocusProject(path: String, entryPoint: AgentWorkbenchEntryPoint) {
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
    generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
    precomputedInitialMessagePlan: AgentInitialMessagePlan? = null,
    resumeLaunchMode: AgentSessionLaunchMode? = null,
    singleFlightPolicy: SingleFlightPolicy = SingleFlightPolicy.DROP,
    launchOrigin: OpenThreadLaunchOrigin = OpenThreadLaunchOrigin.USER_OPEN,
    promptLaunchResolved: ((AgentPromptLaunchResult) -> Unit)? = null,
    extraEnvVariables: Map<String, String> = emptyMap(),
    extraCommandArgs: List<String> = emptyList(),
  ) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val descriptor = AgentSessionProviders.find(thread.provider)
    descriptor?.onConversationOpened()
    syncService.prepareThreadForOpen(
      path = normalizedPath,
      provider = thread.provider,
      threadId = thread.id,
      updatedAt = thread.updatedAt
    )
    launchDropAction(
      key = buildOpenThreadActionKey(path = normalizedPath, thread = thread, launchOrigin = launchOrigin),
      droppedActionMessage = "Dropped duplicate open thread action for $normalizedPath:${thread.provider}:${thread.id}",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
      policy = singleFlightPolicy,
    ) {
      try {
        val archiveResolution = resolveArchivedThreadOpen(
          normalizedPath = normalizedPath,
          thread = thread,
          descriptor = descriptor,
        )
        val openedThread = archiveResolution.thread
        if (initialMessageRequest != null && descriptor != null &&
            !isProviderCliAvailableForLaunch(provider = openedThread.provider, descriptor = descriptor, currentProject = currentProject)) {
          promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
          return@launchDropAction
        }
        val effectiveInitialMessagePlan = when {
          initialMessageRequest == null -> null
          precomputedInitialMessagePlan != null && descriptor?.requiresCliAvailabilityForInitialMessagePlan == true -> {
            descriptor.buildInitialMessagePlan(initialMessageRequest)
          }
          precomputedInitialMessagePlan != null -> precomputedInitialMessagePlan
          else -> descriptor?.buildInitialMessagePlan(initialMessageRequest)
        }
        val effectiveThread = if (initialMessageRequest != null) {
          val refreshedThread = findPromptTargetThread(
            normalizedPath = normalizedPath,
            provider = openedThread.provider,
            threadId = openedThread.id,
          ) ?: openedThread.takeIf { archiveResolution.unarchived }
                                ?: run {
            promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND))
            return@launchDropAction
          }
          if (effectiveInitialMessagePlan?.isBlockedForExistingThreadPlanMode(refreshedThread.activity) == true) {
            promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_BUSY_FOR_PLAN_MODE))
            return@launchDropAction
          }
          refreshedThread
        }
        else {
          openedThread
        }
        val worktreeBranch = stateStore.findWorktreeBranch(normalizedPath)
        val originBranch = effectiveThread.originBranch
        if (worktreeBranch != null && originBranch != null && originBranch != worktreeBranch && !isBranchMismatchDialogSuppressed()) {
          val proceed = withContext(Dispatchers.UiWithModelAccess) {
            branchMismatchConfirmation(currentProject, originBranch, worktreeBranch)
          }
          if (!proceed) {
            promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.CANCELLED))
            return@launchDropAction
          }
        }
        rebindMatchingPendingTabBeforeOpen(
          normalizedPath = normalizedPath,
          thread = effectiveThread,
          descriptor = descriptor,
        )
        val effectiveResumeLaunchMode = resolveResumeLaunchMode(
          descriptor = descriptor,
          requestedLaunchMode = resumeLaunchMode,
        )
        val launchModeForChatState = resolveLaunchModeForChatState(
          requestedLaunchMode = resumeLaunchMode,
          effectiveLaunchMode = effectiveResumeLaunchMode,
        )
        AgentWorkbenchTelemetry.logThreadOpenRequested(entryPoint, effectiveThread.provider, AgentWorkbenchTargetKind.THREAD)
        val plannedResumeLaunch = AgentSessionLaunchPlanner.plan(
          intent = AgentSessionLaunchIntent(
            projectPath = normalizedPath,
            provider = effectiveThread.provider,
            operation = AgentSessionLaunchOperation.RESUME,
            sessionId = effectiveThread.id,
            launchMode = effectiveResumeLaunchMode,
            generationSettings = generationSettings,
          ),
          project = currentProject,
          initialMessagePlan = effectiveInitialMessagePlan ?: AgentInitialMessagePlan.EMPTY,
          extraEnvVariables = extraEnvVariables,
          extraCommandArgs = extraCommandArgs,
        )
        val effectiveInitialMessageDispatchPlan = if (initialMessageDispatchPlan != AgentInitialMessageDispatchPlan.EMPTY) {
          initialMessageDispatchPlan
        }
        else {
          resolvePromptInitialMessageDispatchPlan(
            normalizedPath = normalizedPath,
            thread = effectiveThread,
            initialMessageRequest = initialMessageRequest,
            generationSettings = generationSettings,
            precomputedInitialMessagePlan = effectiveInitialMessagePlan,
            precomputedResumeLaunch = plannedResumeLaunch,
          )
        }

        chatOpenExecutor.openChat(
          normalizedPath = normalizedPath,
          thread = effectiveThread,
          subAgent = null,
          launchSpecOverride = plannedResumeLaunch.launchSpec,
          initialMessageDispatchPlan = effectiveInitialMessageDispatchPlan,
          launchMode = launchModeForChatState,
          generationSettings = plannedResumeLaunch.intent.generationSettings,
        )
        scheduleRefreshAfterArchivedThreadOpen(archiveResolution)
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

  private suspend fun rebindMatchingPendingTabBeforeOpen(
    normalizedPath: String,
    thread: AgentSessionThread,
    descriptor: AgentSessionProviderDescriptor?,
  ) {
    if (descriptor?.supportsPendingEditorTabRebind != true) {
      return
    }
    if (thread.archived || isAgentSessionNewSessionId(thread.id)) {
      return
    }

    val pendingTabs = try {
      openPendingAgentChatTabsProvider(thread.provider)[normalizedPath].orEmpty()
    }
    catch (t: Throwable) {
      if (t is CancellationException) throw t
      LOG.warn("Failed to collect pending tabs before opening ${thread.provider}:${thread.id}", t)
      return
    }
    if (pendingTabs.isEmpty()) {
      return
    }

    val target = buildAgentSessionChatRebindTarget(
      path = normalizedPath,
      provider = thread.provider,
      threadId = thread.id,
      title = thread.title,
      activity = thread.activity,
      updatedAt = thread.updatedAt,
    )
    val matchResult = PendingAgentChatTabMatcher.match(
      pendingTabsByPath = mapOf(normalizedPath to pendingTabs),
      candidatesByPath = mapOf(normalizedPath to listOf(target)),
      preWindowMs = PENDING_THREAD_MATCH_PRE_WINDOW_MS,
      postWindowMs = PENDING_THREAD_MATCH_POST_WINDOW_MS,
    )
    val bindings = matchResult.bindingsByPath[normalizedPath].orEmpty()
    if (bindings.isEmpty()) {
      return
    }

    val requestsByPath = mapOf(
      normalizedPath to bindings.map { binding ->
        AgentChatPendingTabRebindRequest(
          pendingTabKey = binding.pendingTabKey,
          pendingThreadIdentity = binding.pendingThreadIdentity,
          target = binding.target,
        )
      }
    )
    val report = try {
      openAgentChatPendingTabsBinder(thread.provider, requestsByPath)
    }
    catch (t: Throwable) {
      if (t is CancellationException) throw t
      LOG.warn("Failed to rebind pending tab before opening ${thread.provider}:${thread.id}", t)
      return
    }
    LOG.debug {
      "Pending tab pre-open rebind for ${thread.provider.value}:$normalizedPath:${thread.id} " +
      "requested=${report.requestedBindings}, rebound=${report.reboundBindings}, outcomes=${report.outcomesByPath.size}"
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
    val descriptor = AgentSessionProviders.find(thread.provider)
    descriptor?.onConversationOpened()
    launchDropAction(
      key = buildOpenSubAgentActionKey(path = normalizedPath, thread = thread, subAgent = subAgent),
      droppedActionMessage = "Dropped duplicate open sub-agent action for $normalizedPath:${thread.provider}:${thread.id}:${subAgent.id}",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
    ) {
      val archiveResolution = resolveArchivedThreadOpen(
        normalizedPath = normalizedPath,
        thread = thread,
        descriptor = descriptor,
      )
      AgentWorkbenchTelemetry.logThreadOpenRequested(entryPoint, thread.provider, AgentWorkbenchTargetKind.SUB_AGENT)
      chatOpenExecutor.openChat(
        normalizedPath = normalizedPath,
        thread = archiveResolution.thread,
        subAgent = subAgent,
        launchSpecOverride = null,
        initialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
        launchMode = null,
        generationSettings = AgentPromptGenerationSettings.AUTO,
      )
      scheduleRefreshAfterArchivedThreadOpen(archiveResolution)
    }
  }

  private suspend fun resolveArchivedThreadOpen(
    normalizedPath: String,
    thread: AgentSessionThread,
    descriptor: AgentSessionProviderDescriptor?,
  ): ArchivedThreadOpenResolution {
    if (!thread.archived) {
      return ArchivedThreadOpenResolution(thread = thread)
    }
    if (descriptor == null) {
      logMissingProviderDescriptor(thread.provider)
      return ArchivedThreadOpenResolution(thread = thread)
    }
    if (!descriptor.supportsUnarchiveThread) {
      return ArchivedThreadOpenResolution(thread = thread)
    }

    val unarchived = try {
      descriptor.unarchiveThread(path = normalizedPath, threadId = thread.id)
    }
    catch (t: Throwable) {
      if (t is CancellationException) {
        throw t
      }
      LOG.warn("Failed to unarchive opened archived thread ${thread.provider}:${thread.id}", t)
      false
    }
    if (!unarchived) {
      return ArchivedThreadOpenResolution(thread = thread)
    }
    if (descriptor.suppressArchivedThreadsDuringRefresh) {
      syncService.unsuppressArchivedTarget(
        ArchiveThreadTarget.Thread(
          path = normalizedPath,
          provider = thread.provider,
          threadId = thread.id,
        )
      )
    }
    return ArchivedThreadOpenResolution(
      thread = thread.copy(archived = false),
      refreshDelayMs = descriptor.archiveRefreshDelayMs,
    )
  }

  private fun scheduleRefreshAfterArchivedThreadOpen(resolution: ArchivedThreadOpenResolution) {
    val refreshDelayMs = resolution.refreshDelayMs ?: return
    serviceScope.launch(Dispatchers.IO) {
      if (refreshDelayMs > 0L) {
        delay(refreshDelayMs.milliseconds)
      }
      syncService.refresh()
      archivedSessionsRefreshIfLoaded()
    }
  }

  private fun resolveResumeLaunchMode(
    descriptor: AgentSessionProviderDescriptor?,
    requestedLaunchMode: AgentSessionLaunchMode?,
  ): AgentSessionLaunchMode {
    return requestedLaunchMode
             ?.takeIf { launchMode -> descriptor != null && launchMode in descriptor.supportedLaunchModes }
           ?: AgentSessionLaunchMode.STANDARD
  }

  private fun resolveLaunchModeForChatState(
    requestedLaunchMode: AgentSessionLaunchMode?,
    effectiveLaunchMode: AgentSessionLaunchMode,
  ): AgentSessionLaunchMode? {
    if (requestedLaunchMode != null) {
      return effectiveLaunchMode
    }
    return effectiveLaunchMode.takeIf { launchMode -> launchMode != AgentSessionLaunchMode.STANDARD }
  }

  fun createNewSession(
    path: String,
    provider: AgentSessionProvider,
    mode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
    entryPoint: AgentWorkbenchEntryPoint,
    currentProject: Project? = null,
    initialMessageRequest: AgentPromptInitialMessageRequest? = null,
    preferredDedicatedFrame: Boolean? = null,
    openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
    promptLaunchResolved: ((AgentPromptLaunchResult) -> Unit)? = null,
    singleFlightDiscriminator: String? = null,
    updateGeneralProviderPreferences: Boolean = true,
    launchModalityState: ModalityState? = null,
    threadTitle: String? = null,
    generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
    generationModelCatalog: List<AgentPromptGenerationModel> = emptyList(),
    extraEnvVariables: Map<String, String> = emptyMap(),
    extraCommandArgs: List<String> = emptyList(),
  ) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val createSessionActionKey = buildCreateSessionActionKey(
      path = normalizedPath,
      provider = provider,
      mode = mode,
      singleFlightDiscriminator = singleFlightDiscriminator,
    )
    launchDropAction(
      key = createSessionActionKey,
      droppedActionMessage = "Dropped duplicate create session action for $createSessionActionKey",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
      onDrop = promptLaunchResolved?.let { handler ->
        { handler(AgentPromptLaunchResult.failure(AgentPromptLaunchError.DROPPED_DUPLICATE)) }
      },
    ) {
      try {
        if (!providerSettingsService.isProviderEnabled(provider)) {
          promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
          return@launchDropAction
        }
        val descriptor = AgentSessionProviders.find(provider)
        if (descriptor == null) {
          logMissingProviderDescriptor(provider)
          syncService.appendProviderUnavailableWarning(normalizedPath, provider)
          promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
          return@launchDropAction
        }
        if (mode !in descriptor.supportedLaunchModes) {
          logUnsupportedLaunchMode(provider = provider, mode = mode)
          syncService.appendProviderUnavailableWarning(normalizedPath, provider)
          promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE))
          return@launchDropAction
        }
        if (!isProviderCliAvailableForLaunch(provider = provider, descriptor = descriptor, currentProject = currentProject)) {
          promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
          return@launchDropAction
        }
        descriptor.onConversationOpened()
        val effectiveInitialMessageRequest = initialMessageRequest?.withEffectiveProviderOptions(
          descriptor = descriptor,
          target = AgentPromptProviderOptionTarget.NEW_TASK,
        )
        if (updateGeneralProviderPreferences && descriptor.supportsPromptLaunch) {
          uiPreferencesState.updateProviderPreferencesOnLaunch(provider, mode, effectiveInitialMessageRequest)
        }

        val initialMessagePlan = effectiveInitialMessageRequest
                                   ?.let(descriptor::buildInitialMessagePlan)
                                 ?: AgentInitialMessagePlan.EMPTY
        val plannedLaunch = AgentSessionLaunchPlanner.plan(
          intent = AgentSessionLaunchIntent(
            projectPath = normalizedPath,
            provider = provider,
            operation = AgentSessionLaunchOperation.NEW,
            launchMode = mode,
            generationSettings = generationSettings,
          ),
          project = currentProject,
          initialMessagePlan = initialMessagePlan,
          generationModelCatalog = generationModelCatalog,
          extraEnvVariables = extraEnvVariables,
          extraCommandArgs = extraCommandArgs,
        )
        val baseLaunchSpec = plannedLaunch.baseLaunchSpec
        val launchSpec = plannedLaunch.launchSpec
        val identity = buildNewSessionIdentity(provider = provider, launchSpec = launchSpec)
        val initialMessageDispatchPlan = buildInitialMessageDispatchPlan(
          descriptor = descriptor,
          baseLaunchSpec = launchSpec,
          identity = identity,
          initialMessagePlan = initialMessagePlan,
          allowStartupPromptOverride = true,
        )
        logPreparedNewSessionLaunch(
          provider = provider,
          projectPath = normalizedPath,
          identity = identity,
          baseLaunchSpec = baseLaunchSpec,
          resolvedLaunchSpec = launchSpec,
          initialMessageDispatchPlan = initialMessageDispatchPlan,
        )

        AgentWorkbenchTelemetry.logThreadCreateRequested(entryPoint, provider, mode)
        withContext(launchModalityState?.asContextElement() ?: EmptyCoroutineContext) {
          chatOpenExecutor.openNewChat(
            normalizedPath = normalizedPath,
            identity = identity,
            launchSpec = launchSpec,
            initialMessageDispatchPlan = initialMessageDispatchPlan,
            launchMode = mode,
            generationSettings = plannedLaunch.intent.generationSettings,
            preferredDedicatedFrame = preferredDedicatedFrame,
            openedChatHandler = openedChatHandler,
            threadTitle = threadTitle,
          )
        }
        if (descriptor.refreshPathAfterCreateNewSession) {
          syncService.refreshProviderForPath(path = normalizedPath, provider = provider)
        }
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

  suspend fun createDeferredNewSession(
    path: String,
    provider: AgentSessionProvider,
    mode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
    entryPoint: AgentWorkbenchEntryPoint,
    preferredDedicatedFrame: Boolean? = null,
    openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
    updateGeneralProviderPreferences: Boolean = true,
    launchModalityState: ModalityState? = null,
    threadTitle: String? = null,
    waitingTitle: @Nls String,
    waitingMessage: @Nls String? = null,
  ): AgentDeferredNewSessionLaunchResult {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    if (!providerSettingsService.isProviderEnabled(provider)) {
      return AgentDeferredNewSessionLaunchResult(error = AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
    }

    val descriptor = AgentSessionProviders.find(provider)
    if (descriptor == null) {
      logMissingProviderDescriptor(provider)
      syncService.appendProviderUnavailableWarning(normalizedPath, provider)
      return AgentDeferredNewSessionLaunchResult(error = AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
    }
    if (mode !in descriptor.supportedLaunchModes) {
      logUnsupportedLaunchMode(provider = provider, mode = mode)
      syncService.appendProviderUnavailableWarning(normalizedPath, provider)
      return AgentDeferredNewSessionLaunchResult(error = AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE)
    }
    if (!isProviderCliAvailableForLaunch(provider = provider, descriptor = descriptor, currentProject = null)) {
      return AgentDeferredNewSessionLaunchResult(error = AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
    }
    descriptor.onConversationOpened()
    if (updateGeneralProviderPreferences && descriptor.supportsPromptLaunch) {
      uiPreferencesState.updateProviderPreferencesOnLaunch(provider, mode, initialMessageRequest = null)
    }

    val launchSpec = AgentSessionLaunchPlanner.plan(
      intent = AgentSessionLaunchIntent(
        projectPath = normalizedPath,
        provider = provider,
        operation = AgentSessionLaunchOperation.NEW,
        launchMode = mode,
      ),
    ).launchSpec
    val identity = buildNewSessionIdentity(provider = provider, launchSpec = launchSpec)
    val waitingState = AgentChatDeferredStartState(
      phase = AgentChatDeferredStartPhase.WAITING,
      title = waitingTitle,
      message = waitingMessage,
    )
    AgentWorkbenchTelemetry.logThreadCreateRequested(entryPoint, provider, mode)
    val openedChat = withContext(launchModalityState?.asContextElement() ?: EmptyCoroutineContext) {
      openAgentSessionDeferredNewChat(
        normalizedPath = normalizedPath,
        identity = identity,
        launchSpec = launchSpec,
        launchMode = mode,
        preferredDedicatedFrame = preferredDedicatedFrame,
        openedChatHandler = openedChatHandler,
        threadTitle = threadTitle,
        waitingState = waitingState,
      )
    }
    val resolutionRecorded = AtomicBoolean(false)
    return AgentDeferredNewSessionLaunchResult(
      handle = object : AgentDeferredNewSessionHandle {
        override val file: VirtualFile = openedChat.file

        override suspend fun start(initialMessageRequest: AgentPromptInitialMessageRequest?) {
          if (!resolutionRecorded.compareAndSet(false, true)) {
            return
          }
          val initialMessagePlan = initialMessageRequest?.let(descriptor::buildInitialMessagePlan) ?: AgentInitialMessagePlan.EMPTY
          val initialMessageDispatchPlan = buildInitialMessageDispatchPlan(
            descriptor = descriptor,
            baseLaunchSpec = launchSpec,
            identity = identity,
            initialMessagePlan = initialMessagePlan,
            allowStartupPromptOverride = true,
          )
          updateAgentChatDeferredStartState(
            project = openedChat.project,
            file = file,
            deferredStartState = AgentChatDeferredStartState(AgentChatDeferredStartPhase.READY_TO_START, title = ""),
            threadActivity = AgentThreadActivity.READY,
            startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride,
            initialMessageDispatchPlan = initialMessageDispatchPlan,
            newSessionProvider = provider,
            newSessionLaunchMode = mode,
            persistSnapshot = true,
          )
          if (descriptor.refreshPathAfterCreateNewSession) {
            syncService.refreshProviderForPath(path = normalizedPath, provider = provider)
          }
        }

        override suspend fun completeWithoutStart(title: @Nls String, message: @Nls String?) {
          if (!resolutionRecorded.compareAndSet(false, true)) {
            return
          }
          updateAgentChatDeferredStartState(
            project = openedChat.project,
            file = file,
            deferredStartState = AgentChatDeferredStartState(
              phase = AgentChatDeferredStartPhase.SUCCESS_NO_START,
              title = title,
              message = message,
            ),
            threadActivity = AgentThreadActivity.READY,
            forgetPersistedSnapshot = true,
          )
        }

        override suspend fun fail(title: @Nls String, message: @Nls String?) {
          if (!resolutionRecorded.compareAndSet(false, true)) {
            return
          }
          updateAgentChatDeferredStartState(
            project = openedChat.project,
            file = file,
            deferredStartState = AgentChatDeferredStartState(
              phase = AgentChatDeferredStartPhase.FAILURE_NO_START,
              title = title,
              message = message,
            ),
            threadActivity = AgentThreadActivity.READY,
            forgetPersistedSnapshot = true,
          )
        }
      }
    )
  }

  fun launchPromptRequest(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
    fun reportPromptLaunchResolved(result: AgentPromptLaunchResult): AgentPromptLaunchResult {
      AgentWorkbenchTelemetry.logPromptLaunchResolved(request, result)
      return result
    }

    val result = run {
      val bridge = AgentSessionProviders.find(request.provider)
                   ?: return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
      if (!bridge.supportsPromptLaunch) {
        return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
      }
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
            generationSettings = request.generationSettings,
            generationModelCatalog = request.generationModelCatalog,
            extraEnvVariables = request.containerSessionEnvVariables,
            extraCommandArgs = request.containerSessionExtraArgs,
          )
        }
        else {
          val normalizedPath = normalizeAgentWorkbenchPath(request.projectPath)
          val targetThread = findPromptTargetThread(
            normalizedPath = normalizedPath,
            provider = request.provider,
            threadId = targetThreadId,
          )
                             ?: return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND))
          val effectiveInitialMessageRequest = request.initialMessageRequest.withEffectiveProviderOptions(
            descriptor = bridge,
            target = AgentPromptProviderOptionTarget.EXISTING_TASK,
          )
          val initialMessagePlan = bridge.buildInitialMessagePlan(effectiveInitialMessageRequest)
          if (initialMessagePlan.isBlockedForExistingThreadPlanMode(targetThread.activity)) {
            return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_BUSY_FOR_PLAN_MODE))
          }
          uiPreferencesState.updateProviderPreferencesOnLaunch(
            request.provider,
            request.launchMode,
            effectiveInitialMessageRequest
          )

          openChatThread(
            path = normalizedPath,
            thread = targetThread,
            entryPoint = AgentWorkbenchEntryPoint.PROMPT,
            initialMessageRequest = effectiveInitialMessageRequest,
            precomputedInitialMessagePlan = initialMessagePlan,
            generationSettings = request.generationSettings,
            resumeLaunchMode = request.launchMode,
            singleFlightPolicy = SingleFlightPolicy.RESTART_LATEST,
            launchOrigin = OpenThreadLaunchOrigin.PROMPT_LAUNCH,
            promptLaunchResolved = ::reportPromptLaunchResolved,
            extraEnvVariables = request.containerSessionEnvVariables,
            extraCommandArgs = request.containerSessionExtraArgs,
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
  launchMode: AgentSessionLaunchMode? = null,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
) {
  if (AgentChatOpenModeSettings.openInDedicatedFrame()) {
    openChatInDedicatedFrame(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      generationSettings = generationSettings,
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
    launchMode = launchMode,
    generationSettings = generationSettings,
  )
}

private fun buildOpenProjectActionKey(path: String): String {
  return "$OPEN_PROJECT_ACTION_KEY_PREFIX:$path"
}

private fun buildCreateSessionActionKey(
  path: String,
  provider: AgentSessionProvider,
  mode: AgentSessionLaunchMode,
  singleFlightDiscriminator: String? = null,
): String {
  val baseKey = "$CREATE_SESSION_ACTION_KEY_PREFIX:$path:$provider:mode=$mode"
  val discriminator = singleFlightDiscriminator?.takeIf(String::isNotBlank) ?: return baseKey
  return "$baseKey:discriminator=$discriminator"
}

private fun buildInitialMessageDispatchPlan(
  descriptor: AgentSessionProviderDescriptor,
  baseLaunchSpec: AgentSessionTerminalLaunchSpec,
  identity: String,
  initialMessagePlan: AgentInitialMessagePlan,
  allowStartupPromptOverride: Boolean,
): AgentInitialMessageDispatchPlan {
  val postStartDispatchSteps = descriptor.buildPostStartDispatchSteps(initialMessagePlan)
  val startupLaunchSpecOverride = buildStartupLaunchSpecOverride(
    descriptor = descriptor,
    baseLaunchSpec = baseLaunchSpec,
    initialMessagePlan = initialMessagePlan,
    allowStartupPromptOverride = allowStartupPromptOverride,
  )
  if (postStartDispatchSteps.isEmpty() && startupLaunchSpecOverride == null) {
    return AgentInitialMessageDispatchPlan.EMPTY
  }
  return AgentInitialMessageDispatchPlan(
    startupLaunchSpecOverride = startupLaunchSpecOverride,
    postStartDispatchSteps = postStartDispatchSteps,
    initialMessageToken = postStartDispatchSteps
      .takeIf { steps -> steps.isNotEmpty() }
      ?.let { steps -> buildInitialMessageToken(identity = identity, steps = steps) },
  )
}

private fun buildNewSessionIdentity(
  provider: AgentSessionProvider,
  launchSpec: AgentSessionTerminalLaunchSpec,
): String {
  val sessionId = launchSpec.preallocatedSessionId ?: launchSpec.containerSessionId
  return sessionId?.let { buildAgentSessionIdentity(provider, it) } ?: buildAgentSessionNewIdentity(provider)
}

private fun buildStartupLaunchSpecOverride(
  descriptor: AgentSessionProviderDescriptor,
  baseLaunchSpec: AgentSessionTerminalLaunchSpec,
  initialMessagePlan: AgentInitialMessagePlan,
  allowStartupPromptOverride: Boolean,
): AgentSessionTerminalLaunchSpec? {
  if (!allowStartupPromptOverride) {
    return null
  }
  if (initialMessagePlan.startupPolicy != AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND) {
    return null
  }
  if (initialMessagePlan.message == null && initialMessagePlan.mode == AgentInitialMessageMode.STANDARD) {
    return null
  }
  val startupLaunchSpec = descriptor.buildLaunchSpecWithInitialMessage(
    baseLaunchSpec = baseLaunchSpec,
    initialMessagePlan = initialMessagePlan,
  ) ?: return null
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

private fun buildInitialMessageToken(identity: String, steps: List<AgentInitialMessageDispatchStep>): String {
  val sequenceKey = steps.joinToString(separator = "\u0000") { step ->
    listOf(step.text, step.timeoutPolicy.name, step.completionPolicy.name).joinToString(separator = "\u0001")
  }
  return "$identity:${sequenceKey.hashCode()}:${System.nanoTime()}"
}

private fun AgentPromptInitialMessageRequest.withEffectiveProviderOptions(
  descriptor: AgentSessionProviderDescriptor,
  target: AgentPromptProviderOptionTarget,
): AgentPromptInitialMessageRequest {
  val effectiveOptionIds = resolveEffectiveProviderOptionIds(
    selectedProvider = descriptor,
    selectedOptionIds = providerOptionIds,
    target = target,
  )
  return if (effectiveOptionIds == providerOptionIds) this else copy(providerOptionIds = effectiveOptionIds)
}

private suspend fun resolvePromptInitialMessageDispatchPlan(
  normalizedPath: String,
  thread: AgentSessionThread,
  initialMessageRequest: AgentPromptInitialMessageRequest?,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  precomputedInitialMessagePlan: AgentInitialMessagePlan? = null,
  precomputedResumeLaunch: AgentSessionPlannedLaunch? = null,
): AgentInitialMessageDispatchPlan {
  if (initialMessageRequest == null) {
    return AgentInitialMessageDispatchPlan.EMPTY
  }

  val descriptor = AgentSessionProviders.find(thread.provider)
                   ?: return AgentInitialMessageDispatchPlan.EMPTY
  val initialMessagePlan = precomputedInitialMessagePlan ?: descriptor.buildInitialMessagePlan(initialMessageRequest)
  val identity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id)
  val plannedResumeLaunch = precomputedResumeLaunch ?: AgentSessionLaunchPlanner.plan(
    intent = AgentSessionLaunchIntent(
      projectPath = normalizedPath,
      provider = thread.provider,
      operation = AgentSessionLaunchOperation.RESUME,
      sessionId = thread.id,
      generationSettings = generationSettings,
    ),
    initialMessagePlan = initialMessagePlan,
  )
  val allowStartupPromptOverride = initialMessagePlan.mode == AgentInitialMessageMode.PLAN
  val resumeLaunchSpec =
    if (allowStartupPromptOverride && initialMessagePlan.startupPolicy == AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND) {
      plannedResumeLaunch.launchSpec
    }
    else {
      plannedResumeLaunch.baseLaunchSpec
    }
  return buildInitialMessageDispatchPlan(
    descriptor = descriptor,
    baseLaunchSpec = resumeLaunchSpec,
    identity = identity,
    initialMessagePlan = initialMessagePlan,
    allowStartupPromptOverride = allowStartupPromptOverride,
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

private fun logUnsupportedLaunchMode(provider: AgentSessionProvider, mode: AgentSessionLaunchMode) {
  LOG.warn("Session provider ${provider.value} does not support launch mode $mode")
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
  launchMode: AgentSessionLaunchMode?,
  generationSettings: AgentPromptGenerationSettings,
  preferredDedicatedFrame: Boolean?,
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  threadTitle: String? = null,
) {
  val title = resolveNewSessionTitle(identity = identity, threadTitle = threadTitle)
  val dedicatedFrame = preferredDedicatedFrame ?: AgentChatOpenModeSettings.openInDedicatedFrame()
  if (dedicatedFrame) {
    openNewChatInDedicatedFrame(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      title = title,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      generationSettings = generationSettings,
      openedChatHandler = openedChatHandler,
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
    launchMode = launchMode,
    generationSettings = generationSettings,
    openedChatHandler = openedChatHandler,
  )
}

private data class DeferredAgentSessionChatOpenResult(
  @JvmField val project: Project,
  @JvmField val file: VirtualFile,
)

private suspend fun openAgentSessionDeferredNewChat(
  normalizedPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  launchMode: AgentSessionLaunchMode?,
  preferredDedicatedFrame: Boolean?,
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  threadTitle: String? = null,
  waitingState: AgentChatDeferredStartState,
): DeferredAgentSessionChatOpenResult {
  val title = resolveNewSessionTitle(identity = identity, threadTitle = threadTitle)
  val dedicatedFrame = preferredDedicatedFrame ?: AgentChatOpenModeSettings.openInDedicatedFrame()
  if (dedicatedFrame) {
    return openDeferredNewChatInDedicatedFrame(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      launchMode = launchMode,
      title = title,
      openedChatHandler = openedChatHandler,
      waitingState = waitingState,
    )
  }
  val openProject = openOrReuseSourceProjectByPath(normalizedPath) ?: error("Project could not be opened for $normalizedPath")
  return openDeferredNewChatInProject(
    project = openProject,
    projectPath = normalizedPath,
    identity = identity,
    launchSpec = launchSpec,
    launchMode = launchMode,
    title = title,
    openedChatHandler = openedChatHandler,
    waitingState = waitingState,
  )
}

private suspend fun openNewChatInDedicatedFrame(
  normalizedPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  title: String,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
  launchMode: AgentSessionLaunchMode?,
  generationSettings: AgentPromptGenerationSettings,
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
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
      launchMode = launchMode,
      generationSettings = generationSettings,
      openedChatHandler = openedChatHandler,
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
    launchMode = launchMode,
    generationSettings = generationSettings,
    openedChatHandler = openedChatHandler,
  )
}

private suspend fun openDeferredNewChatInDedicatedFrame(
  normalizedPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  launchMode: AgentSessionLaunchMode?,
  title: String,
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  waitingState: AgentChatDeferredStartState,
): DeferredAgentSessionChatOpenResult {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    return openDeferredNewChatInProject(
      project = openProject,
      projectPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      launchMode = launchMode,
      title = title,
      openedChatHandler = openedChatHandler,
      waitingState = waitingState,
    )
  }

  val dedicatedProjectDir = try {
    AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Failed to prepare dedicated chat frame project", e)
    throw e
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: error("Dedicated frame project could not be opened")
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  return openDeferredNewChatInProject(
    project = dedicatedProject,
    projectPath = normalizedPath,
    identity = identity,
    launchSpec = launchSpec,
    launchMode = launchMode,
    title = title,
    openedChatHandler = openedChatHandler,
    waitingState = waitingState,
  )
}

private suspend fun openNewChatInProject(
  project: Project,
  projectPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  title: String,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
  launchMode: AgentSessionLaunchMode?,
  generationSettings: AgentPromptGenerationSettings,
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
) {
  val threadId = resolveAgentSessionId(identity)
  val pendingMetadata = resolvePendingSessionMetadata(identity = identity, launchSpec = launchSpec)
  val provider = parseAgentSessionIdentity(identity)?.provider
  val file = openChat(
    project = project,
    projectPath = projectPath,
    threadIdentity = identity,
    shellCommand = launchSpec.command,
    shellEnvVariables = launchSpec.envVariables,
    threadId = threadId,
    threadTitle = title,
    subAgentId = null,
    threadActivity = AgentThreadActivity.READY,
    pendingCreatedAtMs = pendingMetadata?.createdAtMs,
    pendingLaunchMode = pendingMetadata?.launchMode,
    launchMode = serializeAgentChatLaunchMode(launchMode) ?: pendingMetadata?.launchMode,
    newSessionProvider = provider,
    newSessionLaunchMode = launchMode,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
    generationSettings = generationSettings,
    startupLaunchSpec = launchSpec,
  )
  recordOpenedNewSession(
    projectPath = projectPath,
    provider = provider,
    threadId = threadId,
    title = title,
    createdAtMs = pendingMetadata?.createdAtMs,
  )
  focusProjectWindow(project)
  openedChatHandler?.invoke(project, file)
}

private fun resolveNewSessionTitle(identity: String, threadTitle: String?): String {
  if (threadTitle != null) {
    return threadTitle
  }
  val descriptor = parseAgentSessionIdentity(identity)?.provider?.let(AgentSessionProviders::find)
  if (descriptor != null) {
    return AgentSessionsBundle.message(descriptor.newSessionTitleKey)
  }
  return AgentSessionsBundle.message("toolwindow.action.new.thread")
}

private fun recordOpenedNewSession(
  projectPath: String,
  provider: AgentSessionProvider?,
  threadId: String,
  title: String,
  createdAtMs: Long?,
) {
  val descriptor = provider?.let(AgentSessionProviders::find) ?: return
  descriptor.recordNewSession(
    path = projectPath,
    threadId = threadId,
    title = title,
    createdAtMs = createdAtMs ?: System.currentTimeMillis(),
  )
}

private suspend fun openDeferredNewChatInProject(
  project: Project,
  projectPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  launchMode: AgentSessionLaunchMode?,
  title: String,
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  waitingState: AgentChatDeferredStartState,
): DeferredAgentSessionChatOpenResult {
  val threadId = resolveAgentSessionId(identity)
  val pendingMetadata = resolvePendingSessionMetadata(identity = identity, launchSpec = launchSpec)
  val provider = parseAgentSessionIdentity(identity)?.provider
  val file = openChat(
    project = project,
    projectPath = projectPath,
    threadIdentity = identity,
    shellCommand = launchSpec.command,
    shellEnvVariables = launchSpec.envVariables,
    threadId = threadId,
    threadTitle = title,
    subAgentId = null,
    threadActivity = AgentThreadActivity.READY,
    pendingCreatedAtMs = pendingMetadata?.createdAtMs,
    pendingLaunchMode = pendingMetadata?.launchMode,
    launchMode = serializeAgentChatLaunchMode(launchMode) ?: pendingMetadata?.launchMode,
    newSessionProvider = provider,
    newSessionLaunchMode = launchMode,
    initialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
    persistSnapshot = false,
    deferredStartState = waitingState,
    startupLaunchSpec = launchSpec,
  )
  focusProjectWindow(project)
  openedChatHandler?.invoke(project, file)
  return DeferredAgentSessionChatOpenResult(project = project, file = file)
}

private suspend fun openChatInDedicatedFrame(
  normalizedPath: String,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
  launchMode: AgentSessionLaunchMode? = null,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
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
      launchMode = launchMode,
      generationSettings = generationSettings,
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
    launchMode = launchMode,
    generationSettings = generationSettings,
  )
}

private suspend fun openChatInProject(
  project: Project,
  projectPath: String,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  initialMessageDispatchPlan: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan.EMPTY,
  launchMode: AgentSessionLaunchMode? = null,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
) {
  val chatOpenPayload = resolveAgentSessionChatOpenPayload(
    projectPath = projectPath,
    thread = thread,
    subAgent = subAgent,
    launchSpecOverride = launchSpecOverride,
    launchMode = launchMode ?: AgentSessionLaunchMode.STANDARD,
    generationSettings = generationSettings,
  )
  val effectiveInitialMessageDispatchPlan = if (initialMessageDispatchPlan != AgentInitialMessageDispatchPlan.EMPTY) {
    initialMessageDispatchPlan
  }
  else {
    chatOpenPayload.initialMessageDispatchPlan
  }
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
    launchMode = serializeAgentChatLaunchMode(launchMode),
    initialMessageDispatchPlan = effectiveInitialMessageDispatchPlan,
    generationSettings = generationSettings,
    startupLaunchSpec = chatOpenPayload.launchSpec,
  )

  focusProjectWindow(project)
}

private suspend fun focusProjectWindow(project: Project) {
  val projectUtilService = project.serviceAsync<ProjectUtilService>()
  withContext(Dispatchers.UI) {
    projectUtilService.focusProjectWindow()
  }
}

private suspend fun focusProjectWindowAndActivateSessions(project: Project) {
  val projectUtilService = project.serviceAsync<ProjectUtilService>()
  val toolWindowManager = project.serviceAsync<ToolWindowManager>()
  withContext(Dispatchers.UI) {
    projectUtilService.focusProjectWindow()
    toolWindowManager.getToolWindow(AGENT_SESSIONS_TOOL_WINDOW_ID)?.activate(null)
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
    @Suppress("UnsafeOpenServiceCast")
    return (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(projectIdentityFile = projectPath, options = options)
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
  return getOpenedProjects().firstOrNull { project ->
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

private fun showBranchMismatchDialog(project: Project?, originBranch: String, currentBranch: String): Boolean {
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
    .ask(project)
}

private fun AgentSessionThread.matchesPromptTarget(provider: AgentSessionProvider, threadId: String): Boolean {
  return this.provider == provider && !archived && !isAgentSessionNewSessionId(id) && id == threadId
}
