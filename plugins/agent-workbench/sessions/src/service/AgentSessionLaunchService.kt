// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

// @spec plugins/ij-air/spec/sessions/agent-sessions.spec.md
// @spec plugins/ij-air/spec/frame/agent-dedicated-frame.spec.md
// @spec plugins/ij-air/spec/actions/new-thread.spec.md
// @spec plugins/ij-air/spec/sessions/agent-terminal-sessions.spec.md
// @spec plugins/ij-air/spec/actions/global-prompt-entry.spec.md
// @spec plugins/ij-air/spec/core/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.thread.view.AgentThreadViewDeferredStartPhase
import com.intellij.agent.workbench.thread.view.AgentThreadViewDeferredStartContent
import com.intellij.agent.workbench.thread.view.AgentThreadViewDeferredStartState
import com.intellij.agent.workbench.thread.view.AgentThreadViewPendingTabRebindReport
import com.intellij.agent.workbench.thread.view.AgentThreadViewPendingTabRebindRequest
import com.intellij.agent.workbench.thread.view.AgentThreadViewPendingTabSnapshot
import com.intellij.agent.workbench.thread.view.collectOpenPendingAgentThreadViewTabsByPath
import com.intellij.agent.workbench.thread.view.openThreadView
import com.intellij.agent.workbench.thread.view.rebindOpenPendingAgentThreadViewTabs
import com.intellij.agent.workbench.thread.view.serializeAgentThreadViewLaunchMode
import com.intellij.agent.workbench.thread.view.updateAgentThreadViewDeferredStartState
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
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionOutOfBandLaunch
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionOutOfBandLaunchContext
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionPlannedLaunch
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaceId
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaces
import com.intellij.platform.ai.agent.sessions.core.launch.effectiveAgentSessionSurfaceId as resolveAgentSessionSurfaceId
import com.intellij.platform.ai.agent.sessions.core.launch.resolveAgentSessionThreadViewOpenPlan
import com.intellij.platform.ai.agent.sessions.core.paths.resolveAgentWorkbenchOwningProjectBasePath
import com.intellij.platform.ai.agent.sessions.core.paths.resolveAgentWorkbenchProjectDirectory
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryPlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryStatus
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptRecord
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPendingSessionMetadata
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPrestartNewSessionLaunchRequest
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
import com.intellij.agent.workbench.sessions.frame.AgentThreadViewOpenModeSettings
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

internal interface AgentSessionThreadViewOpenExecutor {
  suspend fun openThreadView(
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
    openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  )

  suspend fun openNewThreadView(
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
    threadTitle: String? = null,
  )

  suspend fun openPreparingNewThreadView(
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
  ): DeferredAgentSessionThreadViewOpenResult

  suspend fun completePreparingNewThreadView(
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
  )

  suspend fun failPreparingNewThreadView(
    openedThreadView: DeferredAgentSessionThreadViewOpenResult,
    title: @Nls String,
    message: @Nls String? = null,
  )
}

internal data class DeferredAgentSessionThreadViewOpenResult(
  @JvmField val project: Project,
  @JvmField val file: VirtualFile,
)

private data class PreparedNewSessionLaunch(
  val descriptor: AgentSessionProviderDescriptor,
  val provider: AgentSessionProvider,
  val mode: AgentSessionLaunchMode,
  val launchProfileId: String?,
  val launchTargetId: String?,
  val surfaceId: AgentSessionSurfaceId,
  val generationSettings: AgentPromptGenerationSettings,
  val launchSpec: AgentSessionTerminalLaunchSpec,
  val identity: String,
  val initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
  val pendingMetadata: AgentPendingSessionMetadata?,
  val outOfBandLaunch: AgentSessionOutOfBandLaunch?,
  val outOfBandThreadId: String?,
  val outOfBandContext: AgentSessionOutOfBandLaunchContext?,
  val outOfBandPrompt: String?,
)

private sealed interface NewSessionLaunchPreparationResult {
  data class Prepared(@JvmField val launch: PreparedNewSessionLaunch) : NewSessionLaunchPreparationResult
  data class Failed(@JvmField val error: AgentPromptLaunchError) : NewSessionLaunchPreparationResult
}

data class AgentDeferredNewSessionLaunchResult(
  @JvmField val handle: AgentDeferredNewSessionHandle? = null,
  @JvmField val error: AgentPromptLaunchError? = null,
)

data class AgentPreparedNewSessionLaunchContext(
  @JvmField val projectPath: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
  @JvmField val identity: String,
  @JvmField val launchProfileId: String?,
  @JvmField val launchTargetId: String?,
  val surfaceId: AgentSessionSurfaceId?,
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

private object DefaultAgentSessionThreadViewOpenExecutor : AgentSessionThreadViewOpenExecutor {
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
    openAgentSessionThreadView(
      normalizedPath = normalizedPath,
      projectDirectory = projectDirectory,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      openedThreadViewHandler = openedThreadViewHandler,
    )
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
    openAgentSessionNewThreadView(
      normalizedPath = normalizedPath,
      projectDirectory = projectDirectory,
      identity = identity,
      launchSpec = launchSpec,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
      openedThreadViewHandler = openedThreadViewHandler,
      threadTitle = threadTitle,
    )
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
    return openAgentSessionDeferredNewThreadView(
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
      openedThreadViewHandler = openedThreadViewHandler,
      threadTitle = threadTitle,
      waitingState = waitingState,
      deferredStartContentProvider = deferredStartContentProvider,
    )
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
    updateAgentThreadViewDeferredStartState(
      project = openedThreadView.project,
      file = openedThreadView.file,
      deferredStartState = AgentThreadViewDeferredStartState(AgentThreadViewDeferredStartPhase.READY_TO_START, title = ""),
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
      launchTargetId = launchTargetId,
      surfaceId = surfaceId?.value,
      generationSettings = generationSettings,
      persistSnapshot = true,
    )
  }

  override suspend fun failPreparingNewThreadView(
    openedThreadView: DeferredAgentSessionThreadViewOpenResult,
    title: @Nls String,
    message: @Nls String?,
  ) {
    updateAgentThreadViewDeferredStartState(
      project = openedThreadView.project,
      file = openedThreadView.file,
      deferredStartState = AgentThreadViewDeferredStartState(
        phase = AgentThreadViewDeferredStartPhase.FAILURE_NO_START,
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
  private val threadViewOpenExecutor: AgentSessionThreadViewOpenExecutor = DefaultAgentSessionThreadViewOpenExecutor,
  private val archiveTransitionSuppressions: AgentSessionArchiveTransitionSuppressions = AgentSessionArchiveTransitionSuppressions(),
  private val openPendingAgentThreadViewTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentThreadViewPendingTabSnapshot>> =
    ::collectOpenPendingAgentThreadViewTabsByPath,
  private val openAgentThreadViewPendingTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentThreadViewPendingTabRebindRequest>>,
  ) -> AgentThreadViewPendingTabRebindReport = ::rebindOpenPendingAgentThreadViewTabs,
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
    threadViewOpenExecutor = DefaultAgentSessionThreadViewOpenExecutor,
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

  fun openThreadViewThread(
    path: String,
    thread: AgentSessionThread,
    entryPoint: AgentWorkbenchEntryPoint,
    currentProject: Project? = null,
    initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
    initialMessageRequest: AgentPromptInitialMessageRequest? = null,
    launchProfileId: String? = null,
    launchTargetId: String? = null,
    surfaceId: AgentSessionSurfaceId? = null,
    generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
    precomputedInitialMessagePlan: AgentInitialMessagePlan? = null,
    resumeLaunchMode: AgentSessionLaunchMode? = null,
    singleFlightPolicy: SingleFlightPolicy = SingleFlightPolicy.DROP,
    launchOrigin: OpenThreadLaunchOrigin = OpenThreadLaunchOrigin.USER_OPEN,
    promptLaunchResolved: ((AgentPromptLaunchResult) -> Unit)? = null,
    extraEnvVariables: Map<String, String> = emptyMap(),
    extraCommandArgs: List<String> = emptyList(),
    openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  ) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val descriptor = AgentSessionProviders.find(thread.provider)
    notifyAgentSessionThreadViewOpened(descriptor)
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
          if (effectiveInitialMessagePlan?.isBlockedForExistingThreadPlanMode(refreshedThread.activityReport.rowActivity) == true) {
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
        val resolvedLaunchProfile = launchProfileId?.let { profileId ->
          launchProfileResolver.resolveLaunchProfile(
            launchProfileId = profileId,
            requiredProvider = effectiveThread.provider,
          )
        }
        val launchGenerationSettings = resolvedLaunchProfile?.generationSettings ?: generationSettings
        val effectiveLaunchTargetId = resolvedLaunchProfile?.launchTargetId ?: launchTargetId?.trim()?.takeIf(String::isNotEmpty)
        val effectiveSurfaceId = resolvedLaunchProfile?.surfaceId ?: effectiveAgentSessionSurfaceId(effectiveThread.provider, surfaceId)
        val requestedResumeLaunchMode = resolvedLaunchProfile?.launchMode ?: resumeLaunchMode
        val effectiveResumeLaunchMode = resolveResumeLaunchMode(
          descriptor = descriptor,
          requestedLaunchMode = requestedResumeLaunchMode,
        )
        val launchModeForThreadViewState = resolveLaunchModeForThreadViewState(
          requestedLaunchMode = requestedResumeLaunchMode,
          effectiveLaunchMode = effectiveResumeLaunchMode,
        )
        AgentWorkbenchTelemetry.logThreadOpenRequested(entryPoint, effectiveThread.provider, AgentWorkbenchTargetKind.THREAD)
        val projectDirectory =
          resolveLaunchProjectDirectory(path = normalizedPath, currentProject = currentProject, stateStore = stateStore)
        val plannedResumeLaunch = AgentSessionLaunchPlanner.plan(
          intent = AgentSessionLaunchIntent(
            projectPath = normalizedPath,
            projectDirectory = projectDirectory,
            provider = effectiveThread.provider,
            operation = AgentSessionLaunchOperation.RESUME,
            sessionId = effectiveThread.id,
            launchMode = effectiveResumeLaunchMode,
            launchTargetId = effectiveLaunchTargetId,
            surfaceId = effectiveSurfaceId,
            generationSettings = launchGenerationSettings,
          ),
          project = currentProject,
          initialMessagePlan = effectiveInitialMessagePlan ?: AgentInitialMessagePlan.EMPTY,
          extraEnvVariables = extraEnvVariables,
          extraCommandArgs = extraCommandArgs,
        )
        val effectiveInitialMessageDispatchPlan = if (initialMessageDispatchPlan != AgentInitialPromptDeliveryPlan.EMPTY) {
          initialMessageDispatchPlan
        }
        else {
          resolvePromptInitialMessageDispatchPlan(
            normalizedPath = normalizedPath,
            thread = effectiveThread,
            initialMessageRequest = initialMessageRequest,
            generationSettings = launchGenerationSettings,
            precomputedInitialMessagePlan = effectiveInitialMessagePlan,
            precomputedResumeLaunch = plannedResumeLaunch,
          )
        }

        threadViewOpenExecutor.openThreadView(
          normalizedPath = normalizedPath,
          projectDirectory = projectDirectory,
          thread = effectiveThread,
          subAgent = null,
          launchSpecOverride = plannedResumeLaunch.launchSpec,
          initialMessageDispatchPlan = effectiveInitialMessageDispatchPlan,
          launchMode = launchModeForThreadViewState,
          launchProfileId = resolvedLaunchProfile?.id ?: launchProfileId,
          launchTargetId = effectiveLaunchTargetId,
          surfaceId = effectiveSurfaceId,
          generationSettings = plannedResumeLaunch.intent.generationSettings,
          openedThreadViewHandler = openedThreadViewHandler,
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
      openPendingAgentThreadViewTabsProvider(thread.provider)[normalizedPath].orEmpty()
    }
    catch (t: Throwable) {
      if (t is CancellationException) throw t
      LOG.warn("Failed to collect pending tabs before opening ${thread.provider}:${thread.id}", t)
      return
    }
    if (pendingTabs.isEmpty()) {
      return
    }

    val target = buildAgentSessionThreadViewRebindTarget(
      path = normalizedPath,
      projectDirectory = resolveLaunchProjectDirectory(path = normalizedPath, stateStore = stateStore),
      provider = thread.provider,
      threadId = thread.id,
      title = thread.title,
      activity = thread.activityReport.rowActivity,
      updatedAt = thread.updatedAt,
    )
    val matchResult = PendingAgentThreadViewTabMatcher.match(
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
        AgentThreadViewPendingTabRebindRequest(
          pendingTabKey = binding.pendingTabKey,
          pendingThreadIdentity = binding.pendingThreadIdentity,
          target = binding.target,
        )
      }
    )
    val report = try {
      openAgentThreadViewPendingTabsBinder(thread.provider, requestsByPath)
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

  fun openThreadViewSubAgent(
    path: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent,
    entryPoint: AgentWorkbenchEntryPoint,
    currentProject: Project? = null,
  ) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val descriptor = AgentSessionProviders.find(thread.provider)
    notifyAgentSessionThreadViewOpened(descriptor)
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
      val projectDirectory = resolveLaunchProjectDirectory(path = normalizedPath, currentProject = currentProject, stateStore = stateStore)
      threadViewOpenExecutor.openThreadView(
        normalizedPath = normalizedPath,
        projectDirectory = projectDirectory,
        thread = archiveResolution.thread,
        subAgent = subAgent,
        launchSpecOverride = null,
        initialMessageDispatchPlan = AgentInitialPromptDeliveryPlan.EMPTY,
        launchMode = null,
        launchProfileId = null,
        launchTargetId = null,
        surfaceId = effectiveAgentSessionSurfaceId(thread.provider, surfaceId = null as AgentSessionSurfaceId?),
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

  private fun resolveLaunchModeForThreadViewState(
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
    initialMessageRequestBuilder: ((AgentPreparedNewSessionLaunchContext) -> AgentPromptInitialMessageRequest?)? = null,
    preparedLaunchHandler: ((AgentPreparedNewSessionLaunchContext) -> Unit)? = null,
    preferredDedicatedFrame: Boolean? = null,
    openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
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
      launchTargetId = resolvedLaunchProfile.launchTargetId,
      surfaceId = resolvedLaunchProfile.surfaceId,
      entryPoint = entryPoint,
      currentProject = currentProject,
      initialMessageRequest = initialMessageRequest,
      initialMessageRequestBuilder = initialMessageRequestBuilder,
      preparedLaunchHandler = preparedLaunchHandler,
      preferredDedicatedFrame = preferredDedicatedFrame,
      openedThreadViewHandler = openedThreadViewHandler,
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
    launchTargetId: String? = null,
    surfaceId: AgentSessionSurfaceId? = null,
    entryPoint: AgentWorkbenchEntryPoint,
    currentProject: Project? = null,
    initialMessageRequest: AgentPromptInitialMessageRequest? = null,
    initialMessageRequestBuilder: ((AgentPreparedNewSessionLaunchContext) -> AgentPromptInitialMessageRequest?)? = null,
    preparedLaunchHandler: ((AgentPreparedNewSessionLaunchContext) -> Unit)? = null,
    preferredDedicatedFrame: Boolean? = null,
    openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
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
    val effectiveLaunchTargetId = resolvedLaunchProfile?.launchTargetId ?: launchTargetId?.trim()?.takeIf(String::isNotEmpty)
    val effectiveSurfaceId = resolvedLaunchProfile?.surfaceId ?: effectiveAgentSessionSurfaceId(effectiveProvider, surfaceId)
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val createSessionActionKey = buildCreateSessionActionKey(
      path = normalizedPath,
      provider = effectiveProvider,
      mode = effectiveMode,
      launchTargetId = effectiveLaunchTargetId,
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
        val openedThreadView = withContext(launchModalityState?.asContextElement() ?: EmptyCoroutineContext) {
          openPreparingNewSessionThreadView(
            normalizedPath = normalizedPath,
            identity = preliminaryIdentity,
            mode = effectiveMode,
            launchProfileId = effectiveLaunchProfileId,
            launchTargetId = effectiveLaunchTargetId,
            surfaceId = effectiveSurfaceId,
            generationSettings = effectiveGenerationSettings,
            preferredDedicatedFrame = preferredDedicatedFrame,
            openedThreadViewHandler = openedThreadViewHandler,
            threadTitle = threadTitle,
            waitingTitle = defaultNewSessionWaitingTitle(),
          )
        }
        val prepared = prepareNewSessionLaunch(
          normalizedPath = normalizedPath,
          provider = effectiveProvider,
          mode = effectiveMode,
          launchProfileId = effectiveLaunchProfileId,
          launchTargetId = effectiveLaunchTargetId,
          surfaceId = effectiveSurfaceId,
          currentProject = currentProject,
          initialMessageRequest = initialMessageRequest,
          initialMessageRequestBuilder = initialMessageRequestBuilder,
          preparedLaunchHandler = preparedLaunchHandler,
          updateGeneralProviderPreferences = updateGeneralProviderPreferences,
          generationSettings = effectiveGenerationSettings,
          generationModelCatalog = generationModelCatalog,
          extraEnvVariables = extraEnvVariables,
          extraCommandArgs = extraCommandArgs,
          fallbackPendingIdentity = preliminaryIdentity,
        )
        if (prepared is NewSessionLaunchPreparationResult.Failed) {
          promptLaunchResolved?.invoke(AgentPromptLaunchResult.failure(prepared.error))
          threadViewOpenExecutor.failPreparingNewThreadView(
            openedThreadView = openedThreadView,
            title = defaultNewSessionFailureTitle(effectiveProvider),
            message = defaultNewSessionFailureMessage(effectiveProvider, prepared.error),
          )
          return@launchDropAction
        }
        val launch = (prepared as NewSessionLaunchPreparationResult.Prepared).launch
        AgentWorkbenchTelemetry.logThreadCreateRequested(entryPoint, launch.provider, launch.mode)
        withContext(launchModalityState?.asContextElement() ?: EmptyCoroutineContext) {
          threadViewOpenExecutor.completePreparingNewThreadView(
            openedThreadView = openedThreadView,
            projectPath = normalizedPath,
            identity = launch.identity,
            launchSpec = launch.launchSpec,
            initialMessageDispatchPlan = launch.initialMessageDispatchPlan,
            launchMode = launch.mode,
            launchProfileId = launch.launchProfileId,
            launchTargetId = launch.launchTargetId,
            surfaceId = launch.surfaceId,
            generationSettings = launch.generationSettings,
            preferredDedicatedFrame = preferredDedicatedFrame,
            threadTitle = resolveNewSessionTitle(identity = launch.identity, threadTitle = threadTitle),
            pendingMetadata = launch.pendingMetadata,
          )
        }
        runOutOfBandLaunchIfNeeded(launch, openedThreadView.project, normalizedPath)
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
    openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
    updateGeneralProviderPreferences: Boolean = true,
    launchProfileId: String? = null,
    launchTargetId: String? = null,
    surfaceId: AgentSessionSurfaceId? = null,
    generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
    launchModalityState: ModalityState? = null,
    threadTitle: String? = null,
    waitingTitle: @Nls String,
    waitingMessage: @Nls String? = null,
    deferredStartContentProvider: ((Project) -> AgentThreadViewDeferredStartContent)? = null,
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
    val effectiveLaunchTargetId = resolvedLaunchProfile?.launchTargetId ?: launchTargetId?.trim()?.takeIf(String::isNotEmpty)
    val effectiveSurfaceId = resolvedLaunchProfile?.surfaceId ?: effectiveAgentSessionSurfaceId(effectiveProvider, surfaceId)
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val preliminaryIdentity = buildAgentSessionNewIdentity(effectiveProvider)
    val openedThreadView = withContext(launchModalityState?.asContextElement() ?: EmptyCoroutineContext) {
      openPreparingNewSessionThreadView(
        normalizedPath = normalizedPath,
        identity = preliminaryIdentity,
        mode = effectiveMode,
        launchProfileId = effectiveLaunchProfileId,
        launchTargetId = effectiveLaunchTargetId,
        surfaceId = effectiveSurfaceId,
        generationSettings = effectiveGenerationSettings,
        preferredDedicatedFrame = preferredDedicatedFrame,
        openedThreadViewHandler = openedThreadViewHandler,
        threadTitle = threadTitle,
        waitingTitle = waitingTitle,
        waitingMessage = waitingMessage,
        deferredStartContentProvider = deferredStartContentProvider,
      )
    }
    val resolutionRecorded = AtomicBoolean(false)
    return AgentDeferredNewSessionLaunchResult(
      handle = object : AgentDeferredNewSessionHandle {
        override val file: VirtualFile = openedThreadView.file

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
          val launchTargetId = resolvedLaunchProfile?.launchTargetId ?: request.launchTargetId?.trim()?.takeIf(String::isNotEmpty)
          val surfaceId = resolvedLaunchProfile?.surfaceId ?: effectiveAgentSessionSurfaceId(launchProvider, request.surfaceId)
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
            launchTargetId = launchTargetId,
            surfaceId = surfaceId,
            currentProject = openedThreadView.project,
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
          threadViewOpenExecutor.completePreparingNewThreadView(
            openedThreadView = openedThreadView,
            projectPath = normalizedPath,
            identity = launch.identity,
            launchSpec = launch.launchSpec,
            initialMessageDispatchPlan = launch.initialMessageDispatchPlan,
            launchMode = launch.mode,
            launchProfileId = launch.launchProfileId,
            launchTargetId = launch.launchTargetId,
            surfaceId = launch.surfaceId,
            generationSettings = launch.generationSettings,
            preferredDedicatedFrame = preferredDedicatedFrame,
            threadTitle = resolveNewSessionTitle(identity = launch.identity, threadTitle = threadTitle),
            pendingMetadata = launch.pendingMetadata,
          )
          runOutOfBandLaunchIfNeeded(launch, openedThreadView.project, normalizedPath)
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
            launchTargetId = effectiveLaunchTargetId,
            surfaceId = effectiveSurfaceId,
            currentProject = openedThreadView.project,
            initialMessageRequest = initialMessageRequest,
            updateGeneralProviderPreferences = updateGeneralProviderPreferences,
            generationSettings = effectiveGenerationSettings,
            fallbackPendingIdentity = preliminaryIdentity,
          )
          if (prepared is NewSessionLaunchPreparationResult.Failed) {
            threadViewOpenExecutor.failPreparingNewThreadView(
              openedThreadView = openedThreadView,
              title = defaultNewSessionFailureTitle(effectiveProvider),
              message = defaultNewSessionFailureMessage(effectiveProvider, prepared.error),
            )
            return
          }
          val launch = (prepared as NewSessionLaunchPreparationResult.Prepared).launch
          AgentWorkbenchTelemetry.logThreadCreateRequested(entryPoint, launch.provider, launch.mode)
          threadViewOpenExecutor.completePreparingNewThreadView(
            openedThreadView = openedThreadView,
            projectPath = normalizedPath,
            identity = launch.identity,
            launchSpec = launch.launchSpec,
            initialMessageDispatchPlan = launch.initialMessageDispatchPlan,
            launchMode = launch.mode,
            launchProfileId = launch.launchProfileId,
            launchTargetId = launch.launchTargetId,
            surfaceId = launch.surfaceId,
            generationSettings = launch.generationSettings,
            preferredDedicatedFrame = preferredDedicatedFrame,
            threadTitle = resolveNewSessionTitle(identity = launch.identity, threadTitle = threadTitle),
            pendingMetadata = launch.pendingMetadata,
          )
          runOutOfBandLaunchIfNeeded(launch, openedThreadView.project, normalizedPath)
          if (launch.descriptor.refreshPathAfterCreateNewSession) {
            syncService.refreshProviderForPath(path = normalizedPath, provider = launch.provider)
          }
        }

        override suspend fun completeWithoutStart(title: @Nls String, message: @Nls String?) {
          if (!resolutionRecorded.compareAndSet(false, true)) {
            return
          }
          updateAgentThreadViewDeferredStartState(
            project = openedThreadView.project,
            file = file,
            deferredStartState = AgentThreadViewDeferredStartState(
              phase = AgentThreadViewDeferredStartPhase.SUCCESS_NO_START,
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
          updateAgentThreadViewDeferredStartState(
            project = openedThreadView.project,
            file = file,
            deferredStartState = AgentThreadViewDeferredStartState(
              phase = AgentThreadViewDeferredStartPhase.FAILURE_NO_START,
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

  private suspend fun openPreparingNewSessionThreadView(
    normalizedPath: String,
    identity: String,
    mode: AgentSessionLaunchMode,
    launchProfileId: String?,
    launchTargetId: String?,
    surfaceId: AgentSessionSurfaceId?,
    generationSettings: AgentPromptGenerationSettings,
    preferredDedicatedFrame: Boolean?,
    openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)?,
    threadTitle: String?,
    waitingTitle: @Nls String,
    waitingMessage: @Nls String? = null,
    deferredStartContentProvider: ((Project) -> AgentThreadViewDeferredStartContent)? = null,
  ): DeferredAgentSessionThreadViewOpenResult {
    val projectDirectory = resolveLaunchProjectDirectory(path = normalizedPath, stateStore = stateStore)
    return threadViewOpenExecutor.openPreparingNewThreadView(
      normalizedPath = normalizedPath,
      projectDirectory = projectDirectory,
      identity = identity,
      launchSpec = AgentSessionTerminalLaunchSpec(command = emptyList()),
      launchMode = mode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      preferredDedicatedFrame = preferredDedicatedFrame,
      openedThreadViewHandler = openedThreadViewHandler,
      threadTitle = threadTitle,
      waitingState = AgentThreadViewDeferredStartState(
        phase = AgentThreadViewDeferredStartPhase.WAITING,
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
    launchTargetId: String?,
    surfaceId: AgentSessionSurfaceId?,
    currentProject: Project?,
    initialMessageRequest: AgentPromptInitialMessageRequest?,
    initialMessageRequestBuilder: ((AgentPreparedNewSessionLaunchContext) -> AgentPromptInitialMessageRequest?)? = null,
    preparedLaunchHandler: ((AgentPreparedNewSessionLaunchContext) -> Unit)? = null,
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
      notifyAgentSessionThreadViewOpened(descriptor)
      val staticInitialMessagePlan = if (initialMessageRequestBuilder == null) {
        initialMessageRequest?.let(descriptor::buildInitialMessagePlan) ?: AgentInitialMessagePlan.EMPTY
      }
      else {
        AgentInitialMessagePlan.EMPTY
      }
      val projectDirectory = resolveLaunchProjectDirectory(path = normalizedPath, currentProject = currentProject, stateStore = stateStore)
      val plannedLaunch = AgentSessionLaunchPlanner.plan(
        intent = AgentSessionLaunchIntent(
          projectPath = normalizedPath,
          projectDirectory = projectDirectory,
          provider = provider,
          operation = AgentSessionLaunchOperation.NEW,
          launchMode = mode,
          launchTargetId = launchTargetId,
          surfaceId = surfaceId,
          generationSettings = generationSettings,
        ),
        project = currentProject,
        initialMessagePlan = staticInitialMessagePlan,
        generationModelCatalog = generationModelCatalog,
        extraEnvVariables = extraEnvVariables,
        extraCommandArgs = extraCommandArgs,
      )
      val baseLaunchSpec = plannedLaunch.baseLaunchSpec
      val plannedLaunchSpec = plannedLaunch.launchSpec
      val plannedSurfaceId = checkNotNull(plannedLaunch.intent.surfaceId) {
        "Resolved new-session launch surface is missing for ${provider.value}"
      }
      val preliminaryIdentity = buildNewSessionIdentity(
        provider = provider,
        launchSpec = plannedLaunchSpec,
        fallbackPendingIdentity = fallbackPendingIdentity,
      )
      val preliminaryContext = AgentPreparedNewSessionLaunchContext(
        projectPath = normalizedPath,
        provider = provider,
        threadId = resolveAgentSessionId(preliminaryIdentity),
        identity = preliminaryIdentity,
        launchProfileId = launchProfileId,
        launchTargetId = plannedLaunch.intent.launchTargetId,
        surfaceId = plannedSurfaceId,
      )
      val effectiveInitialMessageRequest = initialMessageRequestBuilder?.invoke(preliminaryContext) ?: initialMessageRequest
      if (updateGeneralProviderPreferences && descriptor.supportsPromptLaunch) {
        uiPreferencesState.updateProviderOptionsOnLaunch(provider.value, effectiveInitialMessageRequest)
      }
      val initialMessagePlan = if (initialMessageRequestBuilder == null) {
        staticInitialMessagePlan
      }
      else {
        effectiveInitialMessageRequest?.let(descriptor::buildInitialMessagePlan) ?: AgentInitialMessagePlan.EMPTY
      }
      val prestartedLaunch = descriptor.prestartNewSessionLaunch(
        AgentPrestartNewSessionLaunchRequest(
          projectPath = normalizedPath,
          launchMode = mode,
          initialMessagePlan = initialMessagePlan,
          generationSettings = plannedLaunch.intent.generationSettings,
          generationModelCatalog = plannedLaunch.generationModelCatalog,
          launchSpec = plannedLaunchSpec,
        )
      )
      val launchSpec = prestartedLaunch?.launchSpec ?: plannedLaunchSpec
      val identity =
        buildNewSessionIdentity(provider = provider, launchSpec = launchSpec, fallbackPendingIdentity = fallbackPendingIdentity)
      val preparedContext = if (identity == preliminaryContext.identity) {
        preliminaryContext
      }
      else {
        AgentPreparedNewSessionLaunchContext(
          projectPath = normalizedPath,
          provider = provider,
          threadId = resolveAgentSessionId(identity),
          identity = identity,
          launchProfileId = launchProfileId,
          launchTargetId = plannedLaunch.intent.launchTargetId,
          surfaceId = plannedSurfaceId,
        )
      }
      preparedLaunchHandler?.invoke(preparedContext)
      val outOfBandContext = AgentSessionOutOfBandLaunchContext(
        provider = provider,
        launchMode = mode,
        launchProfileId = launchProfileId,
        launchTargetId = plannedLaunch.intent.launchTargetId,
        surfaceId = plannedSurfaceId,
        generationSettings = plannedLaunch.intent.generationSettings,
      )
      val outOfBandLaunch = AgentSessionOutOfBandLaunch.forContext(outOfBandContext)
      val initialMessageDispatchPlan = if (outOfBandLaunch != null) {
        AgentInitialPromptDeliveryPlan.EMPTY
      }
      else {
        prestartedLaunch?.initialMessageDispatchPlan
        ?: buildInitialMessageDispatchPlan(
          descriptor = descriptor,
          baseLaunchSpec = launchSpec,
          identity = identity,
          initialMessagePlan = initialMessagePlan,
          allowStartupPromptOverride = true,
        )
      }
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
          launchTargetId = plannedLaunch.intent.launchTargetId,
          surfaceId = plannedSurfaceId,
          generationSettings = plannedLaunch.intent.generationSettings,
          launchSpec = launchSpec,
          identity = identity,
          initialMessageDispatchPlan = initialMessageDispatchPlan,
          pendingMetadata = resolvePendingSessionMetadata(identity = identity, launchSpec = launchSpec),
          outOfBandLaunch = outOfBandLaunch,
          outOfBandThreadId = launchSpec.preallocatedSessionId,
          outOfBandContext = outOfBandContext,
          outOfBandPrompt = initialMessagePlan.message,
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

  private suspend fun runOutOfBandLaunchIfNeeded(
    launch: PreparedNewSessionLaunch,
    project: Project,
    normalizedPath: String,
  ) {
    val outOfBandLaunch = launch.outOfBandLaunch ?: return
    val threadId = launch.outOfBandThreadId ?: return
    val context = launch.outOfBandContext ?: return
    outOfBandLaunch.launch(project, normalizedPath, threadId, context, launch.outOfBandPrompt)
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
      val launchTargetId = resolvedLaunchProfile?.launchTargetId ?: request.launchTargetId?.trim()?.takeIf(String::isNotEmpty)
      val surfaceId = resolvedLaunchProfile?.surfaceId ?: effectiveAgentSessionSurfaceId(launchProvider, request.surfaceId)
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
            launchTargetId = launchTargetId,
            surfaceId = surfaceId,
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
          if (initialMessagePlan.isBlockedForExistingThreadPlanMode(targetThread.activityReport.rowActivity)) {
            return@run reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.TARGET_THREAD_BUSY_FOR_PLAN_MODE))
          }
          uiPreferencesState.updateProviderOptionsOnLaunch(
            launchProvider.value,
            effectiveInitialMessageRequest
          )

          openThreadViewThread(
            path = normalizedPath,
            thread = targetThread,
            entryPoint = AgentWorkbenchEntryPoint.PROMPT,
            initialMessageRequest = effectiveInitialMessageRequest,
            precomputedInitialMessagePlan = initialMessagePlan,
            launchProfileId = launchProfileId,
            launchTargetId = launchTargetId,
            surfaceId = surfaceId,
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
    LOG.warn("Failed to prepare dedicated threadView frame project", e)
    return
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: return
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  focusProjectWindowAndActivateSessions(dedicatedProject)
}

private suspend fun openAgentSessionThreadView(
  normalizedPath: String,
  projectDirectory: String? = null,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec? = null,
  initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
  launchMode: AgentSessionLaunchMode? = null,
  launchProfileId: String? = null,
  launchTargetId: String? = null,
  surfaceId: AgentSessionSurfaceId? = null,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
) {
  if (AgentThreadViewOpenModeSettings.openInDedicatedFrame()) {
    openThreadViewInDedicatedFrame(
      normalizedPath = normalizedPath,
      projectDirectory = projectDirectory,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      openedThreadViewHandler = openedThreadViewHandler,
    )
    return
  }
  val openProject = openOrReuseSourceProjectByPath(normalizedPath) ?: return
  openThreadViewInProject(
    project = openProject,
    projectPath = normalizedPath,
    projectDirectory = projectDirectory,
    thread = thread,
    subAgent = subAgent,
    launchSpecOverride = launchSpecOverride,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
    launchMode = launchMode,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId,
    generationSettings = generationSettings,
    openedThreadViewHandler = openedThreadViewHandler,
  )
}

private fun buildOpenProjectActionKey(path: String): String {
  return "$OPEN_PROJECT_ACTION_KEY_PREFIX:$path"
}

private fun buildCreateSessionActionKey(
  path: String,
  provider: AgentSessionProvider,
  mode: AgentSessionLaunchMode,
  launchTargetId: String? = null,
  singleFlightDiscriminator: String? = null,
): String {
  val targetId = launchTargetId?.takeIf(String::isNotBlank)?.let { ":target=$it" }.orEmpty()
  val baseKey = "$CREATE_SESSION_ACTION_KEY_PREFIX:$path:$provider:mode=$mode$targetId"
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

private fun resolveLaunchProjectDirectory(
  path: String,
  currentProject: Project? = null,
  stateStore: AgentSessionsStateStore = service(),
): String? {
  val projectBasePath = currentProject
    ?.takeIf { project -> !AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project) }
    ?.basePath
  resolveAgentWorkbenchOwningProjectBasePath(identityPath = path, projectBasePath = projectBasePath)?.let { projectDirectory ->
    return projectDirectory
  }
  stateStore.findProjectDirectory(path)?.let { projectDirectory ->
    return projectDirectory
  }
  return resolveAgentWorkbenchProjectDirectory(identityPath = path)
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
      projectDirectory = resolveLaunchProjectDirectory(path = normalizedPath),
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
  if (!AgentThreadViewOpenModeSettings.openInDedicatedFrame()) return null
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

private suspend fun openAgentSessionNewThreadView(
  normalizedPath: String,
  projectDirectory: String? = null,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
  launchMode: AgentSessionLaunchMode?,
  launchProfileId: String?,
  launchTargetId: String?,
  surfaceId: AgentSessionSurfaceId?,
  generationSettings: AgentPromptGenerationSettings,
  preferredDedicatedFrame: Boolean?,
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  threadTitle: String? = null,
) {
  val title = resolveNewSessionTitle(identity = identity, threadTitle = threadTitle)
  val dedicatedFrame = preferredDedicatedFrame ?: AgentThreadViewOpenModeSettings.openInDedicatedFrame()
  if (dedicatedFrame) {
    openNewThreadViewInDedicatedFrame(
      normalizedPath = normalizedPath,
      projectDirectory = projectDirectory,
      identity = identity,
      launchSpec = launchSpec,
      title = title,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      openedThreadViewHandler = openedThreadViewHandler,
    )
    return
  }
  val openProject = openOrReuseSourceProjectByPath(normalizedPath) ?: return
  openNewThreadViewInProject(
    project = openProject,
    projectPath = normalizedPath,
    projectDirectory = projectDirectory,
    identity = identity,
    launchSpec = launchSpec,
    title = title,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
    launchMode = launchMode,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId,
    generationSettings = generationSettings,
    openedThreadViewHandler = openedThreadViewHandler,
  )
}

private suspend fun openAgentSessionDeferredNewThreadView(
  normalizedPath: String,
  projectDirectory: String? = null,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  launchMode: AgentSessionLaunchMode?,
  launchProfileId: String?,
  launchTargetId: String?,
  surfaceId: AgentSessionSurfaceId?,
  generationSettings: AgentPromptGenerationSettings,
  preferredDedicatedFrame: Boolean?,
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  threadTitle: String? = null,
  waitingState: AgentThreadViewDeferredStartState,
  deferredStartContentProvider: ((Project) -> AgentThreadViewDeferredStartContent)? = null,
): DeferredAgentSessionThreadViewOpenResult {
  val title = resolveNewSessionTitle(identity = identity, threadTitle = threadTitle)
  val dedicatedFrame = preferredDedicatedFrame ?: AgentThreadViewOpenModeSettings.openInDedicatedFrame()
  if (dedicatedFrame) {
    return openDeferredNewThreadViewInDedicatedFrame(
      normalizedPath = normalizedPath,
      projectDirectory = projectDirectory,
      identity = identity,
      launchSpec = launchSpec,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      title = title,
      openedThreadViewHandler = openedThreadViewHandler,
      waitingState = waitingState,
      deferredStartContentProvider = deferredStartContentProvider,
    )
  }
  val openProject = openOrReuseSourceProjectByPath(normalizedPath) ?: error("Project could not be opened for $normalizedPath")
  return openDeferredNewThreadViewInProject(
    project = openProject,
    projectPath = normalizedPath,
    projectDirectory = projectDirectory,
    identity = identity,
    launchSpec = launchSpec,
    launchMode = launchMode,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId,
    generationSettings = generationSettings,
    title = title,
    openedThreadViewHandler = openedThreadViewHandler,
    waitingState = waitingState,
    deferredStartContentProvider = deferredStartContentProvider,
  )
}

private suspend fun openNewThreadViewInDedicatedFrame(
  normalizedPath: String,
  projectDirectory: String? = null,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  title: String,
  initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
  launchMode: AgentSessionLaunchMode?,
  launchProfileId: String?,
  launchTargetId: String?,
  surfaceId: AgentSessionSurfaceId?,
  generationSettings: AgentPromptGenerationSettings,
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
) {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    openNewThreadViewInProject(
      project = openProject,
      projectPath = normalizedPath,
      projectDirectory = projectDirectory,
      identity = identity,
      launchSpec = launchSpec,
      title = title,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      openedThreadViewHandler = openedThreadViewHandler,
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
    LOG.warn("Failed to prepare dedicated threadView frame project", e)
    return
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: return
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  openNewThreadViewInProject(
    project = dedicatedProject,
    projectPath = normalizedPath,
    projectDirectory = projectDirectory,
    identity = identity,
    launchSpec = launchSpec,
    title = title,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
    launchMode = launchMode,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId,
    generationSettings = generationSettings,
    openedThreadViewHandler = openedThreadViewHandler,
  )
}

private suspend fun openDeferredNewThreadViewInDedicatedFrame(
  normalizedPath: String,
  projectDirectory: String? = null,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  launchMode: AgentSessionLaunchMode?,
  launchProfileId: String?,
  launchTargetId: String?,
  surfaceId: AgentSessionSurfaceId?,
  generationSettings: AgentPromptGenerationSettings,
  title: String,
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  waitingState: AgentThreadViewDeferredStartState,
  deferredStartContentProvider: ((Project) -> AgentThreadViewDeferredStartContent)? = null,
): DeferredAgentSessionThreadViewOpenResult {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    return openDeferredNewThreadViewInProject(
      project = openProject,
      projectPath = normalizedPath,
      projectDirectory = projectDirectory,
      identity = identity,
      launchSpec = launchSpec,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      title = title,
      openedThreadViewHandler = openedThreadViewHandler,
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
    LOG.warn("Failed to prepare dedicated threadView frame project", e)
    throw e
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: error("Dedicated frame project could not be opened")
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  return openDeferredNewThreadViewInProject(
    project = dedicatedProject,
    projectPath = normalizedPath,
    projectDirectory = projectDirectory,
    identity = identity,
    launchSpec = launchSpec,
    launchMode = launchMode,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId,
    generationSettings = generationSettings,
    title = title,
    openedThreadViewHandler = openedThreadViewHandler,
    waitingState = waitingState,
    deferredStartContentProvider = deferredStartContentProvider,
  )
}

private suspend fun openNewThreadViewInProject(
  project: Project,
  projectPath: String,
  projectDirectory: String? = null,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  title: String,
  initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan,
  launchMode: AgentSessionLaunchMode?,
  launchProfileId: String?,
  launchTargetId: String?,
  surfaceId: AgentSessionSurfaceId?,
  generationSettings: AgentPromptGenerationSettings,
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
) {
  val threadId = resolveAgentSessionId(identity)
  val pendingMetadata = resolvePendingSessionMetadata(identity = identity, launchSpec = launchSpec)
  val provider = parseAgentSessionIdentity(identity)?.provider
  val file = openThreadView(
    project = project,
    projectPath = projectPath,
    projectDirectory = projectDirectory,
    threadIdentity = identity,
    shellCommand = launchSpec.command,
    shellEnvVariables = launchSpec.envVariables,
    threadId = threadId,
    threadTitle = title,
    subAgentId = null,
    threadActivity = AgentThreadActivity.READY,
    pendingCreatedAtMs = pendingMetadata?.createdAtMs,
    pendingLaunchMode = pendingMetadata?.launchMode,
    launchMode = serializeAgentThreadViewLaunchMode(launchMode) ?: pendingMetadata?.launchMode,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId?.value,
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
  openedThreadViewHandler?.invoke(project, file)
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

private suspend fun openDeferredNewThreadViewInProject(
  project: Project,
  projectPath: String,
  projectDirectory: String? = null,
  identity: String,
  launchSpec: AgentSessionTerminalLaunchSpec,
  launchMode: AgentSessionLaunchMode?,
  launchProfileId: String?,
  launchTargetId: String?,
  surfaceId: AgentSessionSurfaceId?,
  generationSettings: AgentPromptGenerationSettings,
  title: String,
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
  waitingState: AgentThreadViewDeferredStartState,
  deferredStartContentProvider: ((Project) -> AgentThreadViewDeferredStartContent)? = null,
): DeferredAgentSessionThreadViewOpenResult {
  val threadId = resolveAgentSessionId(identity)
  val pendingMetadata = resolvePendingSessionMetadata(identity = identity, launchSpec = launchSpec)
  val provider = parseAgentSessionIdentity(identity)?.provider
  val deferredStartContent = deferredStartContentProvider?.let { provider ->
    withContext(Dispatchers.EDT) {
      provider(project)
    }
  }
  val file = openThreadView(
    project = project,
    projectPath = projectPath,
    projectDirectory = projectDirectory,
    threadIdentity = identity,
    shellCommand = launchSpec.command,
    shellEnvVariables = launchSpec.envVariables,
    threadId = threadId,
    threadTitle = title,
    subAgentId = null,
    threadActivity = AgentThreadActivity.READY,
    pendingCreatedAtMs = pendingMetadata?.createdAtMs,
    pendingLaunchMode = pendingMetadata?.launchMode,
    launchMode = serializeAgentThreadViewLaunchMode(launchMode) ?: pendingMetadata?.launchMode,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId?.value,
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
  openedThreadViewHandler?.invoke(project, file)
  return DeferredAgentSessionThreadViewOpenResult(project = project, file = file)
}

private suspend fun openThreadViewInDedicatedFrame(
  normalizedPath: String,
  projectDirectory: String? = null,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
  launchMode: AgentSessionLaunchMode? = null,
  launchProfileId: String? = null,
  launchTargetId: String? = null,
  surfaceId: AgentSessionSurfaceId? = null,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
) {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    openThreadViewInProject(
      project = openProject,
      projectPath = normalizedPath,
      projectDirectory = projectDirectory,
      thread = thread,
      subAgent = subAgent,
      launchSpecOverride = launchSpecOverride,
      initialMessageDispatchPlan = initialMessageDispatchPlan,
      launchMode = launchMode,
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = surfaceId,
      generationSettings = generationSettings,
      openedThreadViewHandler = openedThreadViewHandler,
    )
    return
  }

  val dedicatedProjectDir = try {
    AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
  }
  catch (e: Throwable) {
    LOG.warn("Failed to prepare dedicated threadView frame project", e)
    return
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: return
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  openThreadViewInProject(
    project = dedicatedProject,
    projectPath = normalizedPath,
    projectDirectory = projectDirectory,
    thread = thread,
    subAgent = subAgent,
    launchSpecOverride = launchSpecOverride,
    initialMessageDispatchPlan = initialMessageDispatchPlan,
    launchMode = launchMode,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId,
    generationSettings = generationSettings,
    openedThreadViewHandler = openedThreadViewHandler,
  )
}

private suspend fun openThreadViewInProject(
  project: Project,
  projectPath: String,
  projectDirectory: String? = null,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  launchSpecOverride: AgentSessionTerminalLaunchSpec?,
  initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
  launchMode: AgentSessionLaunchMode? = null,
  launchProfileId: String? = null,
  launchTargetId: String? = null,
  surfaceId: AgentSessionSurfaceId? = null,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  openedThreadViewHandler: (suspend (Project, VirtualFile) -> Unit)? = null,
) {
  val resolvedProjectDirectory = projectDirectory ?: resolveLaunchProjectDirectory(path = projectPath, currentProject = project)
  val threadViewOpenPlan = resolveAgentSessionThreadViewOpenPlan(
    projectPath = projectPath,
    projectDirectory = resolvedProjectDirectory,
    thread = thread,
    subAgent = subAgent,
    launchSpecOverride = launchSpecOverride,
    launchMode = launchMode ?: AgentSessionLaunchMode.STANDARD,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId,
    generationSettings = generationSettings,
    project = project,
  )
  val effectiveInitialMessageDispatchPlan = if (initialMessageDispatchPlan != AgentInitialPromptDeliveryPlan.EMPTY) {
    initialMessageDispatchPlan
  }
  else {
    threadViewOpenPlan.initialMessageDispatchPlan
  }
  val file = openThreadView(
    project = project,
    projectPath = projectPath,
    projectDirectory = resolvedProjectDirectory,
    threadIdentity = threadViewOpenPlan.threadIdentity,
    shellCommand = threadViewOpenPlan.launchSpec.command,
    shellEnvVariables = threadViewOpenPlan.launchSpec.envVariables,
    threadId = threadViewOpenPlan.runtimeThreadId,
    threadTitle = threadViewOpenPlan.threadTitle,
    subAgentId = threadViewOpenPlan.subAgentId,
    threadActivity = thread.activityReport.rowActivity,
    launchMode = serializeAgentThreadViewLaunchMode(launchMode),
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = surfaceId?.value,
    initialMessageDispatchPlan = effectiveInitialMessageDispatchPlan,
    generationSettings = generationSettings,
    startupLaunchSpec = threadViewOpenPlan.launchSpec,
  )

  focusProjectWindow(project)
  openedThreadViewHandler?.invoke(project, file)
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

private fun effectiveAgentSessionSurfaceId(provider: AgentSessionProvider, surfaceId: String?): AgentSessionSurfaceId {
  return effectiveAgentSessionSurfaceId(provider, AgentSessionSurfaceId.fromOrNull(surfaceId))
}

private fun effectiveAgentSessionSurfaceId(provider: AgentSessionProvider, surfaceId: AgentSessionSurfaceId?): AgentSessionSurfaceId {
  val descriptor = AgentSessionProviders.find(provider)
  return if (descriptor != null) resolveAgentSessionSurfaceId(descriptor, surfaceId) else surfaceId ?: AgentSessionSurfaces.TERMINAL
}

private fun notifyAgentSessionThreadViewOpened(descriptor: AgentSessionProviderDescriptor?) {
  descriptor ?: return
  descriptor.onThreadViewOpened()
  AgentSessionProviderUiContributors.forProvider(descriptor.provider).forEach { contributor ->
    contributor.onThreadViewOpened()
  }
}

private fun AgentSessionThread.matchesPromptTarget(provider: AgentSessionProvider, threadId: String): Boolean {
  return this.provider == provider && !archived && !isAgentSessionNewSessionId(id) && id == threadId
}
