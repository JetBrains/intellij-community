// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/frame/agent-dedicated-frame.spec.md
// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md
// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/core/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.chat.AgentChatDeferredStartPhase
import com.intellij.agent.workbench.chat.AgentChatDeferredStartContent
import com.intellij.agent.workbench.chat.AgentChatDeferredStartState
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.collectOpenPendingAgentChatTabsByPath
import com.intellij.agent.workbench.chat.openChat
import com.intellij.agent.workbench.chat.rebindOpenPendingAgentChatTabs
import com.intellij.agent.workbench.chat.serializeAgentChatLaunchMode
import com.intellij.agent.workbench.chat.updateAgentChatDeferredStartState
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.parseAgentWorkbenchPathOrNull
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_POST_WINDOW_MS
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_PRE_WINDOW_MS
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchIntent
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchOperation
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchPlanner
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionPlannedLaunch
import com.intellij.platform.ai.agent.sessions.core.launch.resolveAgentSessionChatOpenPlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryPlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryStatus
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptRecord
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPendingSessionMetadata
import com.intellij.platform.ai.agent.sessions.core.providers.AgentTerminalPromptDispatch
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionLaunchProfileResolver
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderUiContributors
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.isBlockedForExistingThreadPlanMode
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchTargetKind
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.frame.AGENT_SESSIONS_TOOL_WINDOW_ID
import com.intellij.agent.workbench.sessions.frame.AGENT_WORKBENCH_DEDICATED_FRAME_TYPE_ID
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.providerDisplayName
import com.intellij.agent.workbench.settings.AgentSessionProviderSettingsService
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
import com.intellij.openapi.application.EDT
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
      initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
      launchMode: AgentSessionLaunchMode?,
      launchProfileId: String?,
      generationSettings: AgentPromptGenerationSettings,
      openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  )

  suspend fun openNewChat(
      normalizedPath: String,
      identity: String,
      launchSpec: AgentSessionTerminalLaunchSpec,
      initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
      launchMode: AgentSessionLaunchMode?,
      launchProfileId: String?,
      generationSettings: AgentPromptGenerationSettings,
      preferredDedicatedFrame: Boolean?,
      openedChatHandler: (suspend (Project, VirtualFile) -> Unit)?,
      threadTitle: String? = null,
  )

  suspend fun openPreparingNewChat(
      normalizedPath: String,
      identity: String,
      launchSpec: AgentSessionTerminalLaunchSpec,
      launchMode: AgentSessionLaunchMode?,
      launchProfileId: String?,
      generationSettings: AgentPromptGenerationSettings,
      preferredDedicatedFrame: Boolean?,
      openedChatHandler: (suspend (Project, VirtualFile) -> Unit)?,
      threadTitle: String?,
      waitingState: AgentChatDeferredStartState,
      deferredStartContentProvider: ((Project) -> AgentChatDeferredStartContent)?,
  ): DeferredAgentSessionChatOpenResult

  suspend fun completePreparingNewChat(
      openedChat: DeferredAgentSessionChatOpenResult,
      projectPath: String,
      identity: String,
      launchSpec: AgentSessionTerminalLaunchSpec,
      launchMode: AgentSessionLaunchMode?,
      launchProfileId: String?,
      generationSettings: AgentPromptGenerationSettings,
      preferredDedicatedFrame: Boolean?,
      initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
      threadTitle: String,
      pendingMetadata: AgentPendingSessionMetadata?,
  )

  suspend fun failPreparingNewChat(
      openedChat: DeferredAgentSessionChatOpenResult,
      title: @Nls String,
      message: @Nls String? = null,
  )
}

internal data class DeferredAgentSessionChatOpenResult(
  @JvmField val project: Project,
  @JvmField val file: VirtualFile,
)

private data class PreparedNewSessionLaunch(
  val descriptor: AgentSessionProviderDescriptor,
  val provider: AgentSessionProvider,
  val mode: AgentSessionLaunchMode,
  val launchProfileId: String?,
  val generationSettings: AgentPromptGenerationSettings,
  val launchSpec: AgentSessionTerminalLaunchSpec,
  val identity: String,
  val initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
  val pendingMetadata: AgentPendingSessionMetadata?,
)

private sealed interface NewSessionLaunchPreparationResult {
  data class Prepared(@JvmField val launch: PreparedNewSessionLaunch) : NewSessionLaunchPreparationResult
  data class Failed(@JvmField val error: AgentPromptLaunchError) : NewSessionLaunchPreparationResult
}

data class AgentDeferredNewSessionLaunchResult(
  @JvmField val handle: AgentDeferredNewSessionHandle? = null,
  @JvmField val error: AgentPromptLaunchError? = null,
)

interface AgentDeferredNewSessionHandle {
  val file: VirtualFile

  suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult

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
      initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
      launchMode: AgentSessionLaunchMode?,
      launchProfileId: String?,
      generationSettings: AgentPromptGenerationSettings,
      openedChatHandler: (suspend (Project, VirtualFile) -> Unit)?,
  ) {
    openAgentSessionChat(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      generationSettings = generationSettings,
      openedChatHandler = openedChatHandler,
    )
  }

  override suspend fun openNewChat(
      normalizedPath: String,
      identity: String,
      launchSpec: AgentSessionTerminalLaunchSpec,
      initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
      launchMode: AgentSessionLaunchMode?,
      launchProfileId: String?,
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
      launchProfileId = launchProfileId,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
      openedChatHandler = openedChatHandler,
      threadTitle = threadTitle,
    )
  }

  override suspend fun openPreparingNewChat(
      normalizedPath: String,
      identity: String,
      launchSpec: AgentSessionTerminalLaunchSpec,
      launchMode: AgentSessionLaunchMode?,
      launchProfileId: String?,
      generationSettings: AgentPromptGenerationSettings,
      preferredDedicatedFrame: Boolean?,
      openedChatHandler: (suspend (Project, VirtualFile) -> Unit)?,
      threadTitle: String?,
      waitingState: AgentChatDeferredStartState,
      deferredStartContentProvider: ((Project) -> AgentChatDeferredStartContent)?,
  ): DeferredAgentSessionChatOpenResult {
    return openAgentSessionDeferredNewChat(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
      openedChatHandler = openedChatHandler,
      threadTitle = threadTitle,
      waitingState = waitingState,
      deferredStartContentProvider = deferredStartContentProvider,
    )
  }

  override suspend fun completePreparingNewChat(
      openedChat: DeferredAgentSessionChatOpenResult,
      projectPath: String,
      identity: String,
      launchSpec: AgentSessionTerminalLaunchSpec,
      launchMode: AgentSessionLaunchMode?,
      launchProfileId: String?,
      generationSettings: AgentPromptGenerationSettings,
      preferredDedicatedFrame: Boolean?,
      initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
      threadTitle: String,
      pendingMetadata: AgentPendingSessionMetadata?,
  ) {
    updateAgentChatDeferredStartState(
      project = openedChat.project,
      file = openedChat.file,
      deferredStartState = AgentChatDeferredStartState(AgentChatDeferredStartPhase.READY_TO_START, title = ""),
      threadIdentity = identity,
      threadId = resolveAgentSessionId(identity),
      threadTitle = threadTitle,
      threadActivity = AgentThreadActivity.READY,
      pendingCreatedAtMs = pendingMetadata?.createdAtMs,
      pendingLaunchMode = pendingMetadata?.launchMode,
      startupLaunchSpecOverride = initialMessageDispatchPlan.startupLaunchSpecOverride ?: launchSpec,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      newSessionProvider = parseAgentSessionIdentity(identity)?.provider,
      newSessionLaunchMode = launchMode,
      launchProfileId = launchProfileId,
      generationSettings = generationSettings,
      persistSnapshot = true,
    )
  }

  override suspend fun failPreparingNewChat(
      openedChat: DeferredAgentSessionChatOpenResult,
      title: @Nls String,
      message: @Nls String?,
  ) {
    updateAgentChatDeferredStartState(
      project = openedChat.project,
      file = openedChat.file,
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

@Service(Service.Level.APP)
class AgentSessionLaunchService internal constructor(
  private val serviceScope: CoroutineScope,
  private val stateStore: AgentSessionsStateStore,
  private val syncService: AgentSessionRefreshService,
  private val uiPreferencesState: AgentSessionUiPreferencesStateService = AgentSessionUiPreferencesStateService(),
  private val launchProfileResolver: AgentSessionLaunchProfileResolver = service(),
  private val providerSettingsService: AgentSessionProviderSettingsService = service(),
  private val chatOpenExecutor: AgentSessionChatOpenExecutor = DefaultAgentSessionChatOpenExecutor,
  private val archiveTransitionSuppressions: AgentSessionArchiveTransitionSuppressions = AgentSessionArchiveTransitionSuppressions(),
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
    archiveTransitionSuppressions = service<AgentSessionArchiveTransitionSuppressions>(),
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
      initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
      initialMessageRequest: AgentPromptInitialMessageRequest? = null,
      launchProfileId: String? = null,
      generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
      precomputedInitialMessagePlan: AgentInitialMessagePlan? = null,
      resumeLaunchMode: AgentSessionLaunchMode? = null,
      singleFlightPolicy: SingleFlightPolicy = SingleFlightPolicy.DROP,
      launchOrigin: OpenThreadLaunchOrigin = OpenThreadLaunchOrigin.USER_OPEN,
      promptLaunchResolved: ((AgentPromptLaunchResult) -> Unit)? = null,
      extraEnvVariables: Map<String, String> = emptyMap(),
      extraCommandArgs: List<String> = emptyList(),
      openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  ) {
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      val descriptor = AgentSessionProviders.find(thread.provider)
      notifyAgentSessionConversationOpened(descriptor)
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
                  !isProviderCliAvailableForLaunch(
                      provider = openedThread.provider,
                      descriptor = descriptor,
                      currentProject = currentProject
                  )
              ) {
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
              } else {
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
              val resolvedLaunchProfile = launchProfileId?.let { profileId ->
                  launchProfileResolver.resolveLaunchProfile(
                      launchProfileId = profileId,
                      requiredProvider = effectiveThread.provider,
                  )
              }
              val launchGenerationSettings = resolvedLaunchProfile?.generationSettings ?: generationSettings
              val requestedResumeLaunchMode = resolvedLaunchProfile?.launchMode ?: resumeLaunchMode
              val effectiveResumeLaunchMode = resolveResumeLaunchMode(
                  descriptor = descriptor,
                  requestedLaunchMode = requestedResumeLaunchMode,
              )
              val launchModeForChatState = resolveLaunchModeForChatState(
                  requestedLaunchMode = requestedResumeLaunchMode,
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
                      generationSettings = launchGenerationSettings,
                  ),
                  project = currentProject,
                  initialMessagePlan = effectiveInitialMessagePlan ?: AgentInitialMessagePlan.EMPTY,
                  extraEnvVariables = extraEnvVariables,
                  extraCommandArgs = extraCommandArgs,
              )
              val effectiveInitialMessageDispatchPlan = if (initialMessageDispatchPlan != AgentInitialPromptDeliveryPlan.EMPTY) {
                  initialMessageDispatchPlan
              } else {
                  resolvePromptInitialMessageDispatchPlan(
                      normalizedPath = normalizedPath,
                      thread = effectiveThread,
                      initialMessageRequest = initialMessageRequest,
                      generationSettings = launchGenerationSettings,
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
                  launchProfileId = resolvedLaunchProfile?.id ?: launchProfileId,
                  generationSettings = plannedResumeLaunch.intent.generationSettings,
                  openedChatHandler = openedChatHandler,
              )
              scheduleRefreshAfterArchivedThreadOpen(archiveResolution)
              promptLaunchResolved?.invoke(AgentPromptLaunchResult.SUCCESS)
          } catch (t: Throwable) {
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
    notifyAgentSessionConversationOpened(descriptor)
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
            initialMessageDispatchPlan = AgentInitialPromptDeliveryPlan.EMPTY,
            launchMode = null,
            launchProfileId = null,
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
      val target = ArchiveThreadTarget.Thread(
        path = normalizedPath,
        provider = thread.provider,
        threadId = thread.id,
      )
      archiveTransitionSuppressions.unsuppressActive(target)
      archiveTransitionSuppressions.suppressArchived(target)
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
    launchProfileId: String,
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
    generationModelCatalog: List<AgentPromptGenerationModel> = emptyList(),
    extraEnvVariables: Map<String, String> = emptyMap(),
    extraCommandArgs: List<String> = emptyList(),
  ) {
    val resolvedLaunchProfile = launchProfileResolver.resolveLaunchProfile(launchProfileId = launchProfileId)
    if (resolvedLaunchProfile == null) {
      promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
      return
    }
    createNewSession(
      path = path,
      provider = resolvedLaunchProfile.provider,
      mode = resolvedLaunchProfile.launchMode,
      launchProfileId = resolvedLaunchProfile.id,
      entryPoint = entryPoint,
      currentProject = currentProject,
      initialMessageRequest = initialMessageRequest,
      preferredDedicatedFrame = preferredDedicatedFrame,
      openedChatHandler = openedChatHandler,
      promptLaunchResolved = promptLaunchResolved,
      singleFlightDiscriminator = singleFlightDiscriminator,
      updateGeneralProviderPreferences = updateGeneralProviderPreferences,
      launchModalityState = launchModalityState,
      threadTitle = threadTitle,
      generationSettings = resolvedLaunchProfile.generationSettings,
      generationModelCatalog = generationModelCatalog,
      extraEnvVariables = extraEnvVariables,
      extraCommandArgs = extraCommandArgs,
    )
  }

  fun createNewSession(
    path: String,
    provider: AgentSessionProvider,
    mode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
    launchProfileId: String? = null,
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
    val resolvedLaunchProfile = launchProfileId?.let { profileId ->
      launchProfileResolver.resolveLaunchProfile(
        launchProfileId = profileId,
        requiredProvider = provider,
      )
    }
    val effectiveProvider = resolvedLaunchProfile?.provider ?: provider
    val effectiveMode = resolvedLaunchProfile?.launchMode ?: mode
    val effectiveGenerationSettings = resolvedLaunchProfile?.generationSettings ?: generationSettings
    val effectiveLaunchProfileId = resolvedLaunchProfile?.id ?: launchProfileId
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val createSessionActionKey = buildCreateSessionActionKey(
      path = normalizedPath,
      provider = effectiveProvider,
      mode = effectiveMode,
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
        val preliminaryIdentity = buildAgentSessionNewIdentity(effectiveProvider)
        val openedChat = withContext(launchModalityState?.asContextElement() ?: EmptyCoroutineContext) {
          openPreparingNewSessionChat(
            normalizedPath = normalizedPath,
            identity = preliminaryIdentity,
            mode = effectiveMode,
            launchProfileId = effectiveLaunchProfileId,
            generationSettings = effectiveGenerationSettings,
            preferredDedicatedFrame = preferredDedicatedFrame,
            openedChatHandler = openedChatHandler,
            threadTitle = threadTitle,
            waitingTitle = defaultNewSessionWaitingTitle(),
          )
        }
        val prepared = prepareNewSessionLaunch(
          normalizedPath = normalizedPath,
          provider = effectiveProvider,
          mode = effectiveMode,
          launchProfileId = effectiveLaunchProfileId,
          currentProject = currentProject,
          initialMessageRequest = initialMessageRequest,
          updateGeneralProviderPreferences = updateGeneralProviderPreferences,
          generationSettings = effectiveGenerationSettings,
          generationModelCatalog = generationModelCatalog,
          extraEnvVariables = extraEnvVariables,
          extraCommandArgs = extraCommandArgs,
          fallbackPendingIdentity = preliminaryIdentity,
        )
        if (prepared is NewSessionLaunchPreparationResult.Failed) {
          promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(prepared.error))
          chatOpenExecutor.failPreparingNewChat(
            openedChat = openedChat,
            title = defaultNewSessionFailureTitle(effectiveProvider),
            message = defaultNewSessionFailureMessage(effectiveProvider, prepared.error),
          )
          return@launchDropAction
        }
        val launch = (prepared as NewSessionLaunchPreparationResult.Prepared).launch
        AgentWorkbenchTelemetry.logThreadCreateRequested(entryPoint, launch.provider, launch.mode)
        withContext(launchModalityState?.asContextElement() ?: EmptyCoroutineContext) {
          chatOpenExecutor.completePreparingNewChat(
            openedChat = openedChat,
            projectPath = normalizedPath,
            identity = launch.identity,
            launchSpec = launch.launchSpec,
            initialMessageDispatchPlan = launch.initialMessageDispatchPlan,
            launchMode = launch.mode,
            launchProfileId = launch.launchProfileId,
            generationSettings = launch.generationSettings,
            preferredDedicatedFrame = preferredDedicatedFrame,
            threadTitle = resolveNewSessionTitle(identity = launch.identity, threadTitle = threadTitle),
            pendingMetadata = launch.pendingMetadata,
          )
        }
        if (launch.descriptor.refreshPathAfterCreateNewSession) {
          syncService.refreshProviderForPath(path = normalizedPath, provider = launch.provider)
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
    launchProfileId: String? = null,
    generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
    launchModalityState: ModalityState? = null,
    threadTitle: String? = null,
    waitingTitle: @Nls String,
    waitingMessage: @Nls String? = null,
    deferredStartContentProvider: ((Project) -> AgentChatDeferredStartContent)? = null,
  ): AgentDeferredNewSessionLaunchResult {
    val resolvedLaunchProfile = launchProfileId?.let { profileId ->
      launchProfileResolver.resolveLaunchProfile(
        launchProfileId = profileId,
        requiredProvider = provider,
      )
    }
    val effectiveProvider = resolvedLaunchProfile?.provider ?: provider
    val effectiveMode = resolvedLaunchProfile?.launchMode ?: mode
    val effectiveGenerationSettings = resolvedLaunchProfile?.generationSettings ?: generationSettings
    val effectiveLaunchProfileId = resolvedLaunchProfile?.id ?: launchProfileId
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val preliminaryIdentity = buildAgentSessionNewIdentity(effectiveProvider)
    val openedChat = withContext(launchModalityState?.asContextElement() ?: EmptyCoroutineContext) {
      openPreparingNewSessionChat(
        normalizedPath = normalizedPath,
        identity = preliminaryIdentity,
        mode = effectiveMode,
        launchProfileId = effectiveLaunchProfileId,
        generationSettings = effectiveGenerationSettings,
        preferredDedicatedFrame = preferredDedicatedFrame,
        openedChatHandler = openedChatHandler,
        threadTitle = threadTitle,
        waitingTitle = waitingTitle,
        waitingMessage = waitingMessage,
        deferredStartContentProvider = deferredStartContentProvider,
      )
    }
    val resolutionRecorded = AtomicBoolean(false)
    return AgentDeferredNewSessionLaunchResult(
      handle = object : AgentDeferredNewSessionHandle {
        override val file: VirtualFile = openedChat.file

        override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
          val requestPath = normalizeAgentWorkbenchPath(request.projectPath)
          if (requestPath != normalizedPath) {
            return AgentPromptLaunchResult.failure(AgentPromptLaunchError.INTERNAL_ERROR)
          }
          if (request.containerMode) {
            return AgentPromptLaunchResult.failure(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE)
          }
          val targetThreadId = request.targetThreadId?.trim()
          if (request.targetThreadId != null && targetThreadId.isNullOrEmpty()) {
            return AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND)
          }
          if (targetThreadId != null) {
            return AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND)
          }
          if (resolutionRecorded.get()) {
            return AgentPromptLaunchResult.failure(AgentPromptLaunchError.DROPPED_DUPLICATE)
          }

          val resolvedLaunchProfile = request.launchProfileId?.let { profileId ->
            launchProfileResolver.resolveLaunchProfile(
              launchProfileId = profileId,
              requiredProvider = request.provider,
            )
          }
          val launchProvider = resolvedLaunchProfile?.provider ?: request.provider
          val launchMode = resolvedLaunchProfile?.launchMode ?: request.launchMode
          val launchProfileId = resolvedLaunchProfile?.id ?: request.launchProfileId
          val launchGenerationSettings = resolvedLaunchProfile?.generationSettings ?: request.generationSettings
          val descriptor = AgentSessionProviders.find(launchProvider)
                           ?: return AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
          if (!descriptor.supportsPromptLaunch) {
            return AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
          }
          if (launchMode !in descriptor.supportedLaunchModes) {
            return AgentPromptLaunchResult.failure(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE)
          }

          val prepared = prepareNewSessionLaunch(
            normalizedPath = normalizedPath,
            provider = launchProvider,
            mode = launchMode,
            launchProfileId = launchProfileId,
            currentProject = openedChat.project,
            initialMessageRequest = request.initialMessageRequest,
            updateGeneralProviderPreferences = updateGeneralProviderPreferences,
            generationSettings = launchGenerationSettings,
            generationModelCatalog = request.generationModelCatalog,
            extraEnvVariables = request.containerSessionEnvVariables,
            extraCommandArgs = request.containerSessionExtraArgs,
            fallbackPendingIdentity = preliminaryIdentity,
          )
          if (prepared is NewSessionLaunchPreparationResult.Failed) {
            return AgentPromptLaunchResult.failure(prepared.error)
          }
          if (!resolutionRecorded.compareAndSet(false, true)) {
            return AgentPromptLaunchResult.failure(AgentPromptLaunchError.DROPPED_DUPLICATE)
          }
          val launch = (prepared as NewSessionLaunchPreparationResult.Prepared).launch
          AgentWorkbenchTelemetry.logThreadCreateRequested(entryPoint, launch.provider, launch.mode)
          chatOpenExecutor.completePreparingNewChat(
            openedChat = openedChat,
            projectPath = normalizedPath,
            identity = launch.identity,
            launchSpec = launch.launchSpec,
            initialMessageDispatchPlan = launch.initialMessageDispatchPlan,
            launchMode = launch.mode,
            launchProfileId = launch.launchProfileId,
            generationSettings = launch.generationSettings,
            preferredDedicatedFrame = preferredDedicatedFrame,
            threadTitle = resolveNewSessionTitle(identity = launch.identity, threadTitle = threadTitle),
            pendingMetadata = launch.pendingMetadata,
          )
          if (launch.descriptor.refreshPathAfterCreateNewSession) {
            syncService.refreshProviderForPath(path = normalizedPath, provider = launch.provider)
          }
          return AgentPromptLaunchResult.SUCCESS
        }

        override suspend fun start(initialMessageRequest: AgentPromptInitialMessageRequest?) {
          if (!resolutionRecorded.compareAndSet(false, true)) {
            return
          }
          val prepared = prepareNewSessionLaunch(
            normalizedPath = normalizedPath,
            provider = effectiveProvider,
            mode = effectiveMode,
            launchProfileId = effectiveLaunchProfileId,
            currentProject = openedChat.project,
            initialMessageRequest = initialMessageRequest,
            updateGeneralProviderPreferences = updateGeneralProviderPreferences,
            generationSettings = effectiveGenerationSettings,
            fallbackPendingIdentity = preliminaryIdentity,
          )
          if (prepared is NewSessionLaunchPreparationResult.Failed) {
            chatOpenExecutor.failPreparingNewChat(
              openedChat = openedChat,
              title = defaultNewSessionFailureTitle(effectiveProvider),
              message = defaultNewSessionFailureMessage(effectiveProvider, prepared.error),
            )
            return
          }
          val launch = (prepared as NewSessionLaunchPreparationResult.Prepared).launch
          AgentWorkbenchTelemetry.logThreadCreateRequested(entryPoint, launch.provider, launch.mode)
          chatOpenExecutor.completePreparingNewChat(
            openedChat = openedChat,
            projectPath = normalizedPath,
            identity = launch.identity,
            launchSpec = launch.launchSpec,
            initialMessageDispatchPlan = launch.initialMessageDispatchPlan,
            launchMode = launch.mode,
            launchProfileId = launch.launchProfileId,
            generationSettings = launch.generationSettings,
            preferredDedicatedFrame = preferredDedicatedFrame,
            threadTitle = resolveNewSessionTitle(identity = launch.identity, threadTitle = threadTitle),
            pendingMetadata = launch.pendingMetadata,
          )
          if (launch.descriptor.refreshPathAfterCreateNewSession) {
            syncService.refreshProviderForPath(path = normalizedPath, provider = launch.provider)
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

  private suspend fun openPreparingNewSessionChat(
    normalizedPath: String,
    identity: String,
    mode: AgentSessionLaunchMode,
    launchProfileId: String?,
    generationSettings: AgentPromptGenerationSettings,
    preferredDedicatedFrame: Boolean?,
    openedChatHandler: (suspend (Project, VirtualFile) -> Unit)?,
    threadTitle: String?,
    waitingTitle: @Nls String,
    waitingMessage: @Nls String? = null,
    deferredStartContentProvider: ((Project) -> AgentChatDeferredStartContent)? = null,
  ): DeferredAgentSessionChatOpenResult {
    return chatOpenExecutor.openPreparingNewChat(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = AgentSessionTerminalLaunchSpec(command = emptyList()),
      launchMode = mode,
      launchProfileId = launchProfileId,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
      openedChatHandler = openedChatHandler,
      threadTitle = threadTitle,
      waitingState = AgentChatDeferredStartState(
        phase = AgentChatDeferredStartPhase.WAITING,
        title = waitingTitle,
        message = waitingMessage,
      ),
      deferredStartContentProvider = deferredStartContentProvider,
    )
  }

  private suspend fun prepareNewSessionLaunch(
    normalizedPath: String,
    provider: AgentSessionProvider,
    mode: AgentSessionLaunchMode,
    launchProfileId: String?,
    currentProject: Project?,
    initialMessageRequest: AgentPromptInitialMessageRequest?,
    updateGeneralProviderPreferences: Boolean,
    generationSettings: AgentPromptGenerationSettings,
    generationModelCatalog: List<AgentPromptGenerationModel> = emptyList(),
    extraEnvVariables: Map<String, String> = emptyMap(),
    extraCommandArgs: List<String> = emptyList(),
    fallbackPendingIdentity: String,
  ): NewSessionLaunchPreparationResult {
    return try {
      if (!providerSettingsService.isProviderEnabled(provider)) {
        return NewSessionLaunchPreparationResult.Failed(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
      }
      val descriptor = AgentSessionProviders.find(provider)
      if (descriptor == null) {
        logMissingProviderDescriptor(provider)
        syncService.appendProviderUnavailableWarning(normalizedPath, provider)
        return NewSessionLaunchPreparationResult.Failed(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
      }
      if (mode !in descriptor.supportedLaunchModes) {
        logUnsupportedLaunchMode(provider = provider, mode = mode)
        syncService.appendProviderUnavailableWarning(normalizedPath, provider)
        return NewSessionLaunchPreparationResult.Failed(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE)
      }
      if (!isProviderCliAvailableForLaunch(provider = provider, descriptor = descriptor, currentProject = currentProject)) {
        return NewSessionLaunchPreparationResult.Failed(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
      }
      notifyAgentSessionConversationOpened(descriptor)
      if (updateGeneralProviderPreferences && descriptor.supportsPromptLaunch) {
        uiPreferencesState.updateProviderOptionsOnLaunch(provider.value, initialMessageRequest)
      }

      val initialMessagePlan = initialMessageRequest
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
      val plannedLaunchSpec = plannedLaunch.launchSpec
      val prestartedLaunch = descriptor.prestartNewSessionLaunch(
        projectPath = normalizedPath,
        launchMode = mode,
        initialMessagePlan = initialMessagePlan,
        generationSettings = plannedLaunch.intent.generationSettings,
        generationModelCatalog = plannedLaunch.generationModelCatalog,
        launchSpec = plannedLaunchSpec,
      )
      val launchSpec = prestartedLaunch?.launchSpec ?: plannedLaunchSpec
      val identity = buildNewSessionIdentity(provider = provider, launchSpec = launchSpec, fallbackPendingIdentity = fallbackPendingIdentity)
      val initialMessageDispatchPlan = prestartedLaunch?.initialMessageDispatchPlan
                                         ?: buildInitialMessageDispatchPlan(
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
      NewSessionLaunchPreparationResult.Prepared(
        PreparedNewSessionLaunch(
          descriptor = descriptor,
          provider = provider,
          mode = mode,
          launchProfileId = launchProfileId,
          generationSettings = plannedLaunch.intent.generationSettings,
          launchSpec = launchSpec,
          identity = identity,
          initialMessageDispatchPlan = initialMessageDispatchPlan,
          pendingMetadata = resolvePendingSessionMetadata(identity = identity, launchSpec = launchSpec),
        )
      )
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      LOG.warn("Failed to prepare new agent session for $provider:$normalizedPath", t)
      NewSessionLaunchPreparationResult.Failed(AgentPromptLaunchError.INTERNAL_ERROR)
    }
  }

  fun launchPromptRequest(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
    fun reportPromptLaunchResolved(result: AgentPromptLaunchResult): AgentPromptLaunchResult {
      AgentWorkbenchTelemetry.logPromptLaunchResolved(request, result)
      return result
    }

    val result = run {
      val resolvedLaunchProfile = request.launchProfileId?.let { profileId ->
        launchProfileResolver.resolveLaunchProfile(
          launchProfileId = profileId,
          requiredProvider = request.provider,
        )
      }
      val launchProvider = resolvedLaunchProfile?.provider ?: request.provider
      val launchMode = resolvedLaunchProfile?.launchMode ?: request.launchMode
      val launchProfileId = resolvedLaunchProfile?.id ?: request.launchProfileId
      val launchGenerationSettings = resolvedLaunchProfile?.generationSettings ?: request.generationSettings
      val bridge = AgentSessionProviders.find(launchProvider)
                   ?: return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
      if (!bridge.supportsPromptLaunch) {
        return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
      }
      if (launchMode !in bridge.supportedLaunchModes) {
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
            provider = launchProvider,
            mode = launchMode,
            launchProfileId = launchProfileId,
            entryPoint = AgentWorkbenchEntryPoint.PROMPT,
            initialMessageRequest = request.initialMessageRequest,
            preferredDedicatedFrame = request.preferredDedicatedFrame,
            promptLaunchResolved = ::reportPromptLaunchResolved,
            generationSettings = launchGenerationSettings,
            generationModelCatalog = request.generationModelCatalog,
            extraEnvVariables = request.containerSessionEnvVariables,
            extraCommandArgs = request.containerSessionExtraArgs,
          )
        }
        else {
          val normalizedPath = normalizeAgentWorkbenchPath(request.projectPath)
          val targetThread = findPromptTargetThread(
            normalizedPath = normalizedPath,
            provider = launchProvider,
            threadId = targetThreadId,
          )
                             ?: return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND))
          val effectiveInitialMessageRequest = request.initialMessageRequest
          val initialMessagePlan = bridge.buildInitialMessagePlan(effectiveInitialMessageRequest)
          if (initialMessagePlan.isBlockedForExistingThreadPlanMode(targetThread.activity)) {
            return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_BUSY_FOR_PLAN_MODE))
          }
          uiPreferencesState.updateProviderOptionsOnLaunch(
            launchProvider.value,
            effectiveInitialMessageRequest
          )

          openChatThread(
            path = normalizedPath,
            thread = targetThread,
            entryPoint = AgentWorkbenchEntryPoint.PROMPT,
            initialMessageRequest = effectiveInitialMessageRequest,
            precomputedInitialMessagePlan = initialMessagePlan,
            launchProfileId = launchProfileId,
            generationSettings = launchGenerationSettings,
            resumeLaunchMode = launchMode,
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
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
    launchMode: AgentSessionLaunchMode? = null,
    launchProfileId: String? = null,
    generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
    openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
) {
  if (AgentChatOpenModeSettings.openInDedicatedFrame()) {
    openChatInDedicatedFrame(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      generationSettings = generationSettings,
      openedChatHandler = openedChatHandler,
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
    launchProfileId = launchProfileId,
    generationSettings = generationSettings,
    openedChatHandler = openedChatHandler,
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
): AgentInitialPromptDeliveryPlan {
  val postStartDispatchSteps = descriptor.buildPostStartDispatchSteps(initialMessagePlan)
  val startupLaunchSpecOverride = buildStartupLaunchSpecOverride(
    descriptor = descriptor,
    baseLaunchSpec = baseLaunchSpec,
    initialMessagePlan = initialMessagePlan,
    allowStartupPromptOverride = allowStartupPromptOverride,
  )
  if (postStartDispatchSteps.isEmpty() && startupLaunchSpecOverride == null) {
    return AgentInitialPromptDeliveryPlan.EMPTY
  }
  val terminalDispatch = AgentTerminalPromptDispatch(steps = postStartDispatchSteps).normalized()
  val deliveryChannel = if (startupLaunchSpecOverride != null) {
    AgentInitialPromptDeliveryChannel.STARTUP_COMMAND
  }
  else {
    terminalDispatch?.let { AgentInitialPromptDeliveryChannel.TERMINAL }
  }
  val deliveryStatus = if (startupLaunchSpecOverride != null) {
    AgentInitialPromptDeliveryStatus.DELIVERED
  }
  else {
    AgentInitialPromptDeliveryStatus.PENDING
  }
  val token = terminalDispatch
    ?.steps
    ?.takeIf { steps -> steps.isNotEmpty() }
    ?.let { steps -> buildInitialMessageToken(identity = identity, steps = steps) }
  return AgentInitialPromptDeliveryPlan(
    startupLaunchSpecOverride = startupLaunchSpecOverride,
    promptRecord = AgentInitialPromptRecord(
      message = initialMessagePlan.message,
      mode = initialMessagePlan.mode,
      token = token,
      deliveryStatus = deliveryStatus,
      deliveryChannel = deliveryChannel,
    ),
    terminalDispatch = terminalDispatch.takeIf { startupLaunchSpecOverride == null },
    startupFallbackTerminalDispatch = terminalDispatch.takeIf { startupLaunchSpecOverride != null },
  )
}

private fun buildNewSessionIdentity(
  provider: AgentSessionProvider,
  launchSpec: AgentSessionTerminalLaunchSpec,
  fallbackPendingIdentity: String? = null,
): String {
  val sessionId = launchSpec.preallocatedSessionId ?: launchSpec.containerSessionId
  return sessionId?.let { buildAgentSessionIdentity(provider, it) } ?: fallbackPendingIdentity ?: buildAgentSessionNewIdentity(provider)
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
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
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
    listOf(step.text, step.timeoutPolicy.name, step.action.name, step.recordsPrompt.toString())
      .joinToString(separator = "\u0001")
  }
  return "$identity:${sequenceKey.hashCode()}:${System.nanoTime()}"
}

private suspend fun resolvePromptInitialMessageDispatchPlan(
  normalizedPath: String,
  thread: AgentSessionThread,
  initialMessageRequest: AgentPromptInitialMessageRequest?,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  precomputedInitialMessagePlan: AgentInitialMessagePlan? = null,
  precomputedResumeLaunch: AgentSessionPlannedLaunch? = null,
): AgentInitialPromptDeliveryPlan {
  if (initialMessageRequest == null) {
    return AgentInitialPromptDeliveryPlan.EMPTY
  }

  val descriptor = AgentSessionProviders.find(thread.provider)
                   ?: return AgentInitialPromptDeliveryPlan.EMPTY
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

private fun defaultNewSessionWaitingTitle(): @Nls String {
  return AgentSessionsBundle.message("toolwindow.thread.preparing.title")
}

private fun defaultNewSessionFailureTitle(provider: AgentSessionProvider): @Nls String {
  return AgentSessionsBundle.message("toolwindow.thread.preparing.failed.title", providerDisplayName(provider))
}

private fun defaultNewSessionFailureMessage(provider: AgentSessionProvider, error: AgentPromptLaunchError): @Nls String {
  if (error == AgentPromptLaunchError.PROVIDER_UNAVAILABLE) {
    val descriptor = AgentSessionProviders.find(provider)
    if (descriptor != null) {
      return AgentSessionsBundle.message(descriptor.cliMissingMessageKey)
    }
  }
  return when (error) {
    AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE -> AgentSessionsBundle.message("toolwindow.thread.preparing.failed.unsupported.mode")
    AgentPromptLaunchError.CANCELLED -> AgentSessionsBundle.message("toolwindow.thread.preparing.failed.cancelled")
    else -> AgentSessionsBundle.message("toolwindow.thread.preparing.failed.generic")
  }
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
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
    launchMode: AgentSessionLaunchMode?,
    launchProfileId: String?,
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
      launchProfileId = launchProfileId,
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
    launchProfileId = launchProfileId,
    generationSettings = generationSettings,
    openedChatHandler = openedChatHandler,
  )
}

private suspend fun openAgentSessionDeferredNewChat(
  normalizedPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  launchMode: AgentSessionLaunchMode?,
  launchProfileId: String?,
  generationSettings: AgentPromptGenerationSettings,
  preferredDedicatedFrame: Boolean?,
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  threadTitle: String? = null,
  waitingState: AgentChatDeferredStartState,
  deferredStartContentProvider: ((Project) -> AgentChatDeferredStartContent)? = null,
): DeferredAgentSessionChatOpenResult {
  val title = resolveNewSessionTitle(identity = identity, threadTitle = threadTitle)
  val dedicatedFrame = preferredDedicatedFrame ?: AgentChatOpenModeSettings.openInDedicatedFrame()
  if (dedicatedFrame) {
    return openDeferredNewChatInDedicatedFrame(
      normalizedPath = normalizedPath,
      identity = identity,
      launchSpec = launchSpec,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      generationSettings = generationSettings,
      title = title,
      openedChatHandler = openedChatHandler,
      waitingState = waitingState,
      deferredStartContentProvider = deferredStartContentProvider,
    )
  }
  val openProject = openOrReuseSourceProjectByPath(normalizedPath) ?: error("Project could not be opened for $normalizedPath")
  return openDeferredNewChatInProject(
    project = openProject,
    projectPath = normalizedPath,
    identity = identity,
    launchSpec = launchSpec,
    launchMode = launchMode,
    launchProfileId = launchProfileId,
    generationSettings = generationSettings,
    title = title,
    openedChatHandler = openedChatHandler,
    waitingState = waitingState,
    deferredStartContentProvider = deferredStartContentProvider,
  )
}

private suspend fun openNewChatInDedicatedFrame(
    normalizedPath: String,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    title: String,
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
    launchMode: AgentSessionLaunchMode?,
    launchProfileId: String?,
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
      launchProfileId = launchProfileId,
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
    launchProfileId = launchProfileId,
    generationSettings = generationSettings,
    openedChatHandler = openedChatHandler,
  )
}

private suspend fun openDeferredNewChatInDedicatedFrame(
  normalizedPath: String,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  launchMode: AgentSessionLaunchMode?,
  launchProfileId: String?,
  generationSettings: AgentPromptGenerationSettings,
  title: String,
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  waitingState: AgentChatDeferredStartState,
  deferredStartContentProvider: ((Project) -> AgentChatDeferredStartContent)? = null,
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
      launchProfileId = launchProfileId,
      generationSettings = generationSettings,
      title = title,
      openedChatHandler = openedChatHandler,
      waitingState = waitingState,
      deferredStartContentProvider = deferredStartContentProvider,
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
    launchProfileId = launchProfileId,
    generationSettings = generationSettings,
    title = title,
    openedChatHandler = openedChatHandler,
    waitingState = waitingState,
    deferredStartContentProvider = deferredStartContentProvider,
  )
}

private suspend fun openNewChatInProject(
    project: Project,
    projectPath: String,
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    title: String,
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
    launchMode: AgentSessionLaunchMode?,
    launchProfileId: String?,
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
    launchProfileId = launchProfileId,
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
  launchProfileId: String?,
  generationSettings: AgentPromptGenerationSettings,
  title: String,
  openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  waitingState: AgentChatDeferredStartState,
  deferredStartContentProvider: ((Project) -> AgentChatDeferredStartContent)? = null,
): DeferredAgentSessionChatOpenResult {
  val threadId = resolveAgentSessionId(identity)
  val pendingMetadata = resolvePendingSessionMetadata(identity = identity, launchSpec = launchSpec)
  val provider = parseAgentSessionIdentity(identity)?.provider
  val deferredStartContent = deferredStartContentProvider?.let { provider ->
    withContext(Dispatchers.EDT) {
      provider(project)
    }
  }
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
    launchProfileId = launchProfileId,
    newSessionProvider = provider,
    newSessionLaunchMode = launchMode,
    initialMessageDispatchPlan = AgentInitialPromptDeliveryPlan.EMPTY,
    generationSettings = generationSettings,
    persistSnapshot = false,
    deferredStartState = waitingState,
    deferredStartContent = deferredStartContent,
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
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
    launchMode: AgentSessionLaunchMode? = null,
    launchProfileId: String? = null,
    generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
    openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
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
      launchProfileId = launchProfileId,
      generationSettings = generationSettings,
      openedChatHandler = openedChatHandler,
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
    launchProfileId = launchProfileId,
    generationSettings = generationSettings,
    openedChatHandler = openedChatHandler,
  )
}

private suspend fun openChatInProject(
    project: Project,
    projectPath: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    launchSpecOverride: AgentSessionTerminalLaunchSpec?,
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
    launchMode: AgentSessionLaunchMode? = null,
    launchProfileId: String? = null,
    generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
    openedChatHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
) {
  val chatOpenPlan = resolveAgentSessionChatOpenPlan(
    projectPath = projectPath,
    thread = thread,
    subAgent = subAgent,
    launchSpecOverride = launchSpecOverride,
    launchMode = launchMode ?: AgentSessionLaunchMode.STANDARD,
    generationSettings = generationSettings,
    project = project,
  )
  val effectiveInitialMessageDispatchPlan = if (initialMessageDispatchPlan != AgentInitialPromptDeliveryPlan.EMPTY) {
    initialMessageDispatchPlan
  }
  else {
    chatOpenPlan.initialMessageDispatchPlan
  }
  val file = openChat(
    project = project,
    projectPath = projectPath,
    threadIdentity = chatOpenPlan.threadIdentity,
    shellCommand = chatOpenPlan.launchSpec.command,
    shellEnvVariables = chatOpenPlan.launchSpec.envVariables,
    threadId = chatOpenPlan.runtimeThreadId,
    threadTitle = chatOpenPlan.threadTitle,
    subAgentId = chatOpenPlan.subAgentId,
    threadActivity = thread.activity,
    launchMode = serializeAgentChatLaunchMode(launchMode),
    launchProfileId = launchProfileId,
    initialMessageDispatchPlan = effectiveInitialMessageDispatchPlan,
    generationSettings = generationSettings,
    startupLaunchSpec = chatOpenPlan.launchSpec,
  )

  focusProjectWindow(project)
  openedChatHandler?.invoke(project, file)
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

private fun notifyAgentSessionConversationOpened(descriptor: AgentSessionProviderDescriptor?) {
  descriptor ?: return
  descriptor.onConversationOpened()
  AgentSessionProviderUiContributors.forProvider(descriptor.provider).forEach { contributor ->
    contributor.onConversationOpened()
  }
}

private fun AgentSessionThread.matchesPromptTarget(provider: AgentSessionProvider, threadId: String): Boolean {
  return this.provider == provider && !archived && !isAgentSessionNewSessionId(id) && id == threadId
}
