// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.thread.view

// @spec plugins/ij-air/spec/thread-view/agent-thread-view.spec.md

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextToTargetResult
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchIntent
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchOperation
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchPlanner
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadPresentationUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private class AgentThreadViewEditorServiceLog

private val LOG = logger<AgentThreadViewEditorServiceLog>()

private data class AgentThreadViewScopedRefreshSignal(
  val provider: AgentSessionProvider,
  @JvmField val updateEvent: AgentSessionSourceUpdateEvent,
)

private object AgentThreadViewScopedRefreshSignalBus {
  private val signalFlow = MutableSharedFlow<AgentThreadViewScopedRefreshSignal>(extraBufferCapacity = 64)

  fun signal(
    provider: AgentSessionProvider,
    projectPath: String,
    threadId: String? = null,
    activityReport: AgentThreadActivityReport? = null,
  ): Boolean {
    return signal(
      provider = provider,
      projectPath = projectPath,
      threadId = threadId,
      threadTitle = null,
      activityReport = activityReport,
    )
  }

  fun signal(
    provider: AgentSessionProvider,
    projectPath: String,
    threadId: String?,
    threadTitle: String?,
    activityReport: AgentThreadActivityReport?,
  ): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(projectPath)
    val normalizedThreadId = threadId?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedThreadTitle = threadTitle?.trim()?.takeIf { it.isNotEmpty() }
    val activityUpdatesByThreadId = if (normalizedThreadId != null && activityReport != null) {
      mapOf(normalizedThreadId to AgentSessionThreadActivityUpdate(activityReport))
    }
    else {
      emptyMap()
    }
    val presentationUpdatesByThreadId = if (normalizedThreadId != null && normalizedThreadTitle != null) {
      mapOf(normalizedThreadId to AgentSessionThreadPresentationUpdate(title = normalizedThreadTitle))
    }
    else {
      emptyMap()
    }
    if (normalizedPath.isBlank()) {
      return false
    }
    val updateEvent = if (activityUpdatesByThreadId.isEmpty()) {
      AgentSessionSourceUpdateEvent.threadsChanged(
        scopedPaths = setOf(normalizedPath),
        threadIds = normalizedThreadId?.let { setOf(it) },
        presentationUpdatesByThreadId = presentationUpdatesByThreadId,
      )
    }
    else {
      AgentSessionSourceUpdateEvent.hintsChanged(
        scopedPaths = setOf(normalizedPath),
        activityUpdatesByThreadId = activityUpdatesByThreadId,
        presentationUpdatesByThreadId = presentationUpdatesByThreadId,
      )
    }
    return signal(
      provider = provider,
      updateEvent = updateEvent,
    )
  }

  fun signal(
    provider: AgentSessionProvider,
    updateEvent: AgentSessionSourceUpdateEvent,
  ): Boolean {
    return signalFlow.tryEmit(
      AgentThreadViewScopedRefreshSignal(
        provider = provider,
        updateEvent = updateEvent,
      )
    )
  }

  fun signals(provider: AgentSessionProvider): Flow<AgentSessionSourceUpdateEvent> {
    return signalFlow.asSharedFlow()
      .filter { signal -> signal.provider == provider }
      .map { signal -> signal.updateEvent }
  }
}

data class AgentThreadViewTabRebindTarget(
  @JvmField val projectPath: String,
  @JvmField val projectDirectory: String? = null,
  val provider: AgentSessionProvider,
  @JvmField val threadIdentity: String,
  @JvmField val threadId: String,
  @JvmField val threadTitle: String,
  // threadActivity is the row-compatible activity; threadActivityReport carries editor chrome state.
  @JvmField val threadActivity: AgentThreadActivity,
  @JvmField val threadActivityReport: AgentThreadActivityReport = AgentThreadActivityReport(threadActivity),
  @JvmField val threadUpdatedAt: Long = 0L,
  // Callers that already know the resume command can avoid provider launch planning side effects.
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec? = null,
)

data class AgentThreadViewPendingTabSnapshot(
  @JvmField val projectPath: String,
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val pendingCreatedAtMs: Long?,
  @JvmField val pendingFirstInputAtMs: Long?,
  @JvmField val pendingLaunchMode: String?,
  @JvmField val pinnedEditorTab: Boolean = false,
)

data class AgentThreadViewConcreteTabSnapshot(
  @JvmField val projectPath: String,
  @JvmField val tabKey: String,
  @JvmField val currentThreadIdentity: String,
  @JvmField val newThreadRebindRequestedAtMs: Long,
)

data class AgentThreadViewPendingTabRebindRequest(
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val target: AgentThreadViewTabRebindTarget,
)

private fun AgentThreadViewPendingTabRebindRequest.hasConcreteTargetForProvider(provider: AgentSessionProvider): Boolean {
  val targetCoordinates = resolveAgentThreadViewThreadCoordinates(target.threadIdentity) ?: return false
  // Pending tabs may only rebind to discovered concrete identities; `new-*` targets are synthetic
  // placeholders used for projection and must be rejected here even if a caller passes one in.
  return target.provider == provider &&
         targetCoordinates.provider == provider &&
         !targetCoordinates.isPending &&
         target.threadIdentity != pendingThreadIdentity
}

data class AgentThreadViewConcreteTabRebindRequest(
  @JvmField val tabKey: String,
  @JvmField val currentThreadIdentity: String,
  @JvmField val newThreadRebindRequestedAtMs: Long,
  @JvmField val target: AgentThreadViewTabRebindTarget,
)

enum class AgentThreadViewPendingTabRebindStatus {
  REBOUND,
  PENDING_TAB_NOT_OPEN,
  INVALID_PENDING_TAB,
  TARGET_ALREADY_OPEN,
}

data class AgentThreadViewPendingTabRebindOutcome(
  @JvmField val projectPath: String,
  @JvmField val request: AgentThreadViewPendingTabRebindRequest,
  @JvmField val status: AgentThreadViewPendingTabRebindStatus,
  @JvmField val reboundFiles: Int,
)

data class AgentThreadViewPendingTabRebindReport(
  @JvmField val requestedBindings: Int,
  @JvmField val reboundBindings: Int,
  @JvmField val reboundFiles: Int,
  @JvmField val updatedPresentations: Int,
  @JvmField val outcomesByPath: Map<String, List<AgentThreadViewPendingTabRebindOutcome>>,
)

enum class AgentThreadViewConcreteTabRebindStatus {
  REBOUND,
  CONCRETE_TAB_NOT_OPEN,
  INVALID_CONCRETE_TAB,
  TARGET_ALREADY_OPEN,
}

data class AgentThreadViewConcreteTabRebindOutcome(
  @JvmField val projectPath: String,
  @JvmField val request: AgentThreadViewConcreteTabRebindRequest,
  @JvmField val status: AgentThreadViewConcreteTabRebindStatus,
  @JvmField val reboundFiles: Int,
)

data class AgentThreadViewConcreteTabRebindReport(
  @JvmField val requestedBindings: Int,
  @JvmField val reboundBindings: Int,
  @JvmField val reboundFiles: Int,
  @JvmField val updatedPresentations: Int,
  @JvmField val outcomesByPath: Map<String, List<AgentThreadViewConcreteTabRebindOutcome>>,
)

suspend fun openThreadView(
  project: Project,
  projectPath: String,
  projectDirectory: String? = null,
  threadIdentity: String,
  shellCommand: List<String>,
  shellEnvVariables: Map<String, String> = emptyMap(),
  threadId: String,
  threadTitle: String,
  subAgentId: String?,
  threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
  pendingCreatedAtMs: Long? = null,
  pendingFirstInputAtMs: Long? = null,
  pendingLaunchMode: String? = null,
  launchMode: String? = null,
  launchProfileId: String? = null,
  launchTargetId: String? = null,
  surfaceId: String? = null,
  newSessionProvider: AgentSessionProvider? = null,
  newSessionLaunchMode: AgentSessionLaunchMode? = null,
  initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  persistSnapshot: Boolean = true,
  deferredStartState: AgentThreadViewDeferredStartState? = null,
  deferredStartContent: AgentThreadViewDeferredStartContent? = null,
  startupLaunchSpec: AgentSessionTerminalLaunchSpec? = null,
): VirtualFile {
  val manager = FileEditorManagerEx.getInstanceExAsync(project)

  val tabKey = AgentThreadViewTabKey.fromIdentity(
    AgentThreadViewTabIdentity(
      projectHash = project.locationHash,
      projectPath = projectPath,
      projectDirectory = projectDirectory,
      threadIdentity = threadIdentity,
      subAgentId = subAgentId,
    )
  )
  val existing = findExistingThreadViewByTabKey(manager.openFiles, tabKey.value)
                 ?: findExistingThreadView(manager.openFiles, threadIdentity, subAgentId)
  val launchSpec = startupLaunchSpec ?: AgentSessionTerminalLaunchSpec(command = shellCommand, envVariables = shellEnvVariables)
  val isNewTab = existing == null
  val effectiveGenerationSettings = if (generationSettings == AgentPromptGenerationSettings.AUTO) {
    existing?.generationSettings ?: AgentPromptGenerationSettings.AUTO
  }
  else {
    generationSettings
  }
  val effectiveLaunchProfileId = launchProfileId?.trim()?.takeIf(String::isNotEmpty) ?: existing?.launchProfileId
  val effectiveLaunchTargetId = launchTargetId?.trim()?.takeIf(String::isNotEmpty) ?: existing?.launchTargetId
  val effectiveSurfaceId = normalizeAgentThreadViewSurfaceId(surfaceId) ?: existing?.surfaceId
  val startupOverrideForTab = if (isNewTab) {
    initialMessageDispatchPlan.startupLaunchSpecOverride ?: launchSpec.takeIf(::shouldUseStartupLaunchSpecOverride)
  }
  else {
    null
  }
  val startupIntentForTab = if (isNewTab && persistSnapshot) {
    buildNewSessionStartupIntent(
      provider = newSessionProvider,
      launchMode = newSessionLaunchMode,
      launchProfileId = effectiveLaunchProfileId,
      launchTargetId = effectiveLaunchTargetId,
      surfaceId = effectiveSurfaceId,
    ) ?: pendingProviderForThreadIdentity(threadIdentity)?.let { provider ->
      AgentThreadViewStartupIntent.NewSession(provider = provider,
                                        launchMode = parseAgentThreadViewLaunchMode(pendingLaunchMode),
                                        launchProfileId = effectiveLaunchProfileId,
                                        launchTargetId = effectiveLaunchTargetId,
                                        surfaceId = parseAgentThreadViewSurfaceId(effectiveSurfaceId))
    }
  }
  else {
    null
  }
  val effectiveInitialMessageDispatchPlan = if (isNewTab) {
    initialMessageDispatchPlan
  }
  else {
    initialMessageDispatchPlan.withStartupDeliveryIgnored()
  }
  val snapshotInitialPromptRecord = effectiveInitialMessageDispatchPlan.promptRecord
  val snapshotTerminalPromptDispatch = effectiveInitialMessageDispatchPlan.terminalDispatch
  val hasExplicitInitialPromptDelivery = snapshotInitialPromptRecord != null || snapshotTerminalPromptDispatch != null
  val snapshot = AgentThreadViewTabSnapshot.create(
    projectHash = project.locationHash,
    projectPath = projectPath,
    projectDirectory = projectDirectory,
    threadIdentity = threadIdentity,
    threadId = threadId,
    threadTitle = threadTitle,
    subAgentId = subAgentId,
    threadActivity = threadActivity,
    pendingCreatedAtMs = pendingCreatedAtMs,
    pendingFirstInputAtMs = pendingFirstInputAtMs,
    pendingLaunchMode = pendingLaunchMode,
    launchMode = launchMode ?: existing?.launchMode,
    launchProfileId = effectiveLaunchProfileId,
    launchTargetId = effectiveLaunchTargetId,
    surfaceId = effectiveSurfaceId,
    generationSettings = effectiveGenerationSettings,
    newThreadRebindRequestedAtMs = existing?.newThreadRebindRequestedAtMs,
    initialPromptRecord = snapshotInitialPromptRecord,
    terminalPromptDispatch = snapshotTerminalPromptDispatch,
  )
  LOG.debug {
    "openThreadView(project=${project.name}, path=$projectPath, identity=$threadIdentity, " +
    "subAgentId=$subAgentId, existing=${existing != null}, title=$threadTitle)"
  }
  val file = existing ?: agentThreadViewVirtualFileSystem().getOrCreateFile(snapshot)
  if (deferredStartContent != null) {
    file.replaceDeferredStartContent(deferredStartContent)
  }
  if (existing != null) {
    val oldLaunchMode = existing.launchMode
    existing.updateRestoreOnRestart(persistSnapshot)
    if (!persistSnapshot) {
      existing.updateStartupIntent(null)
    }
    existing.updateFromResolution(AgentThreadViewTabResolution.Resolved(snapshot))
    existing.updateThreadId(threadId)
    val titleUpdated = existing.updateBootstrapThreadTitle(threadTitle)
    val activityUpdated = existing.updateBootstrapThreadActivity(threadActivity)
    val deferredStartStateUpdated = existing.updateDeferredStartState(deferredStartState)
    val pendingUpdated = (pendingCreatedAtMs != null || pendingFirstInputAtMs != null || pendingLaunchMode != null) &&
                         existing.updatePendingMetadata(
                           pendingCreatedAtMs = pendingCreatedAtMs,
                           pendingFirstInputAtMs = pendingFirstInputAtMs,
                           pendingLaunchMode = pendingLaunchMode,
                         )
    val launchModeUpdated = oldLaunchMode != existing.launchMode
    if (hasExplicitInitialPromptDelivery) {
      existing.updateInitialPromptDelivery(
        promptRecord = snapshotInitialPromptRecord,
        terminalDispatch = snapshotTerminalPromptDispatch,
      )
    }
    LOG.debug {
      "openThreadView existing tab update(identity=$threadIdentity, subAgentId=$subAgentId): " +
      "titleUpdated=$titleUpdated, activityUpdated=$activityUpdated, " +
      "currentName=${existing.name}," +
      " currentTitle=${existing.threadTitle}, currentActivity=${existing.threadActivity}"
    }
    if (titleUpdated || activityUpdated || pendingUpdated || launchModeUpdated || hasExplicitInitialPromptDelivery ||
        deferredStartStateUpdated) {
      withContext(Dispatchers.EDT) {
        manager.updateFilePresentation(existing)
        if (deferredStartStateUpdated) {
          refreshOpenEditors(manager = manager, file = existing)
        }
      }
    }
  }
  else {
    file.updateRestoreOnRestart(persistSnapshot)
    file.updateStartupIntent(startupIntentForTab)
    file.updateDeferredStartState(deferredStartState)
    if (startupOverrideForTab != null) {
      file.setStartupLaunchSpecOverride(
        launchSpec = startupOverrideForTab,
        suppressInitialMessageDispatch = initialMessageDispatchPlan.startupLaunchSpecOverride != null,
      )
    }
    LOG.debug {
      "openThreadView created new tab(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name}, activity=$threadActivity)"
    }
  }
  if (ApplicationManager.getApplication().isUnitTestMode) {
    // TestEditorManagerImpl uses FileEditorProvider.KEY for non-text editors and otherwise falls back to doOpenTextEditor.
    file.putUserData(FileEditorProvider.KEY, AgentThreadViewFileEditorProvider())
  }
  val focusDeferredStartContent = deferredStartContent != null
  withContext(Dispatchers.UiWithModelAccess) {
    manager.openFile(file = file, options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true))
    if (existing != null && hasExplicitInitialPromptDelivery && !file.initialMessageSent) {
      flushPendingInitialMessageForOpenEditors(manager = manager, file = file)
    }
  }
  LOG.debug {
    "openThreadView openFile completed(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name}, activity=$threadActivity)"
  }

  val pendingProvider = pendingProviderForThreadIdentity(threadIdentity)
  if (pendingProvider != null) {
    project.service<AgentThreadViewPendingEditorLifecycleService>()
    service<AgentThreadViewOpenTabsPresentationStateService>().refreshOpenTabs()
    if (AgentSessionProviders.find(pendingProvider)?.emitsScopedRefreshSignals == true) {
      notifyAgentThreadViewScopedRefresh(provider = pendingProvider, projectPath = projectPath)
    }
  }

  if (focusDeferredStartContent) {
    withContext(Dispatchers.UiWithModelAccess) {
      focusOpenThreadViewEditorPreferredComponent(project = project, manager = manager, file = file)
    }
    withContext(Dispatchers.EDT) {
      yield()
      focusOpenThreadViewEditorPreferredComponent(project = project, manager = manager, file = file)
    }
  }

  return file
}

private fun shouldUseStartupLaunchSpecOverride(launchSpec: AgentSessionTerminalLaunchSpec): Boolean {
  return launchSpec.command.isNotEmpty() ||
         launchSpec.useTerminalDefaultShell ||
         launchSpec.workingDirectory != null ||
         launchSpec.preallocatedSessionId != null ||
         launchSpec.containerSessionId != null
}

private fun buildNewSessionStartupIntent(
  provider: AgentSessionProvider?,
  launchMode: AgentSessionLaunchMode?,
  launchProfileId: String?,
  launchTargetId: String?,
  surfaceId: String?,
): AgentThreadViewStartupIntent.NewSession? {
  val resolvedProvider = provider ?: return null
  return AgentThreadViewStartupIntent.NewSession(
    provider = resolvedProvider,
    launchMode = launchMode ?: AgentSessionLaunchMode.STANDARD,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
    surfaceId = parseAgentThreadViewSurfaceId(surfaceId),
  )
}

fun persistAgentThreadViewTabMetadata(file: VirtualFile) {
  if (file !is AgentThreadViewVirtualFile) return
  file.updateRestoreOnRestart(true)
  // Agent Thread View restore metadata is serialized from FileEditor.getState() with the workspace editor state.
}

suspend fun refreshOpenAgentThreadViewFile(project: Project, file: VirtualFile) {
  val threadViewFile = file as? AgentThreadViewVirtualFile ?: return
  val manager = FileEditorManagerEx.getInstanceExAsync(project)
  withContext(Dispatchers.EDT) {
    manager.updateFilePresentation(threadViewFile)
    refreshOpenEditors(manager = manager, file = threadViewFile)
  }
}

suspend fun updateAgentThreadViewDeferredStartState(
  project: Project,
  file: VirtualFile,
  deferredStartState: AgentThreadViewDeferredStartState?,
  threadIdentity: String? = null,
  threadId: String? = null,
  threadTitle: String? = null,
  threadActivity: AgentThreadActivity? = null,
  pendingCreatedAtMs: Long? = null,
  pendingLaunchMode: String? = null,
  startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec? = null,
  initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan? = null,
  newSessionProvider: AgentSessionProvider? = null,
  newSessionLaunchMode: AgentSessionLaunchMode? = null,
  launchProfileId: String? = null,
  launchTargetId: String? = null,
  surfaceId: String? = null,
  generationSettings: AgentPromptGenerationSettings? = null,
  persistSnapshot: Boolean = false,
  forgetPersistedSnapshot: Boolean = false,
) {
  val threadViewFile = file as? AgentThreadViewVirtualFile ?: return
  startupLaunchSpecOverride?.let { launchSpec ->
    threadViewFile.setStartupLaunchSpecOverride(
      launchSpec = launchSpec,
      suppressInitialMessageDispatch = initialMessageDispatchPlan?.startupLaunchSpecOverride != null,
    )
  }
  threadViewFile.updateDeferredStartState(deferredStartState)
  if (threadIdentity != null && threadId != null) {
    threadViewFile.rebindPendingThread(
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle ?: threadViewFile.threadTitle,
      threadActivity = threadActivity ?: threadViewFile.threadActivity,
    )
  }
  else {
    threadActivity?.let {
      threadViewFile.updateBootstrapThreadActivity(it)
    }
  }
  if (pendingCreatedAtMs != null || pendingLaunchMode != null) {
    threadViewFile.updatePendingMetadata(
      pendingCreatedAtMs = pendingCreatedAtMs,
      pendingFirstInputAtMs = threadViewFile.pendingFirstInputAtMs,
      pendingLaunchMode = pendingLaunchMode,
    )
  }
  launchProfileId?.let {
    threadViewFile.updateLaunchProfileId(it)
  }
  launchTargetId?.let {
    threadViewFile.updateLaunchTargetId(it)
  }
  surfaceId?.let {
    threadViewFile.updateSurfaceId(it)
  }
  generationSettings?.let {
    threadViewFile.updateGenerationSettings(it)
  }
  initialMessageDispatchPlan?.let { dispatchPlan ->
    threadViewFile.updateInitialPromptDelivery(
      promptRecord = dispatchPlan.promptRecord,
      terminalDispatch = dispatchPlan.terminalDispatch,
    )
  }
  if (persistSnapshot) {
    threadViewFile.updateRestoreOnRestart(true)
    if (deferredStartState?.phase == AgentThreadViewDeferredStartPhase.READY_TO_START) {
      threadViewFile.updateStartupIntent(
        buildNewSessionStartupIntent(
          provider = newSessionProvider,
          launchMode = newSessionLaunchMode,
          launchProfileId = threadViewFile.launchProfileId,
          launchTargetId = threadViewFile.launchTargetId,
          surfaceId = threadViewFile.surfaceId,
        ) ?: resolveAgentThreadViewNewSessionStartupIntent(threadViewFile)
      )
    }
    persistAgentThreadViewTabMetadata(threadViewFile)
  }
  else if (forgetPersistedSnapshot) {
    threadViewFile.updateRestoreOnRestart(false)
    threadViewFile.updateStartupIntent(null)
    forgetAgentThreadViewTabMetadata(threadViewFile.tabKey)
  }
  refreshOpenAgentThreadViewFile(project = project, file = threadViewFile)
}

suspend fun collectOpenPendingAgentThreadViewProjectPaths(): Set<String> {
  return collectOpenAgentThreadViewProjectPaths(includePendingOnly = true)
}

fun notifyAgentThreadViewScopedRefresh(
  provider: AgentSessionProvider,
  projectPath: String,
  threadId: String? = null,
  activityReport: AgentThreadActivityReport? = null,
) {
  AgentThreadViewScopedRefreshSignalBus.signal(provider, projectPath, threadId, activityReport)
}

fun notifyAgentThreadViewScopedRefresh(
  provider: AgentSessionProvider,
  projectPath: String,
  threadId: String?,
  threadTitle: String?,
  activityReport: AgentThreadActivityReport?,
) {
  AgentThreadViewScopedRefreshSignalBus.signal(provider, projectPath, threadId, threadTitle, activityReport)
}

fun notifyAgentThreadViewScopedRefresh(
  provider: AgentSessionProvider,
  updateEvent: AgentSessionSourceUpdateEvent,
) {
  AgentThreadViewScopedRefreshSignalBus.signal(provider, updateEvent)
}

fun agentThreadViewScopedRefreshSignals(provider: AgentSessionProvider): Flow<AgentSessionSourceUpdateEvent> {
  return AgentThreadViewScopedRefreshSignalBus.signals(provider)
}

suspend fun collectOpenPendingAgentThreadViewTabsByPath(
  provider: AgentSessionProvider,
): Map<String, List<AgentThreadViewPendingTabSnapshot>> {
  return collectOpenAgentThreadViewTabsSnapshotOnUi().pendingTabsByPath(provider)
}

suspend fun collectOpenConcreteAgentThreadViewTabsAwaitingNewThreadRebindByPath(
  provider: AgentSessionProvider,
): Map<String, List<AgentThreadViewConcreteTabSnapshot>> {
  return collectOpenAgentThreadViewTabsSnapshotOnUi().concreteTabsAwaitingNewThreadRebindByPath(provider)
}

suspend fun collectOpenConcreteAgentThreadViewThreadIdentitiesByPath(): Map<String, Set<String>> {
  return collectOpenAgentThreadViewTabsSnapshotOnUi().concreteThreadIdentitiesByPath()
}

suspend fun addContextToOpenTopLevelAgentThreadView(
  projectPath: String,
  provider: AgentSessionProvider,
  threadId: String,
  contextItems: List<AgentPromptContextItem>,
): AgentPromptAddContextToTargetResult = withContext(Dispatchers.UiWithModelAccess) {
  val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
  if (contextItems.isEmpty()) {
    return@withContext AgentPromptAddContextToTargetResult.UNAVAILABLE
  }
  val openEntry = collectOpenAgentThreadViewTabsSnapshot().findOpenTopLevelConcreteEntry(
    normalizedPath = normalizedProjectPath,
    provider = provider,
    threadId = threadId,
  ) ?: return@withContext AgentPromptAddContextToTargetResult.UNAVAILABLE
  val manager = openEntry.manager
  if (manager is FileEditorManagerEx) {
    manager.openFile(file = openEntry.file, options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true))
  }
  else {
    manager.openFile(openEntry.file, true)
  }
  val editor = manager.getAllEditors(openEntry.file).filterIsInstance<AgentThreadViewFileEditor>().firstOrNull()
               ?: return@withContext AgentPromptAddContextToTargetResult.UNAVAILABLE
  if (editor.addPendingContextItems(contextItems)) {
    AgentPromptAddContextToTargetResult.ADDED_TO_THREAD_VIEW
  }
  else {
    AgentPromptAddContextToTargetResult.ALREADY_ADDED_TO_THREAD_VIEW
  }
}

private suspend fun collectOpenAgentThreadViewProjectPaths(includePendingOnly: Boolean): Set<String> {
  return collectOpenAgentThreadViewTabsSnapshotOnUi().projectPaths(includePendingOnly)
}

private data class AgentThreadViewRebindLaunchSpecKey(
  val projectPath: String,
  val projectDirectory: String?,
  val provider: AgentSessionProvider,
  val threadId: String,
)

private fun AgentThreadViewTabRebindTarget.toRebindLaunchSpecKey(): AgentThreadViewRebindLaunchSpecKey {
  return AgentThreadViewRebindLaunchSpecKey(
    projectPath = normalizeAgentWorkbenchPath(projectPath),
    projectDirectory = projectDirectory?.takeIf { it.isNotBlank() }?.let(::normalizeAgentWorkbenchPath),
    provider = provider,
    threadId = threadId,
  )
}

private suspend fun resolveRebindLaunchSpec(target: AgentThreadViewTabRebindTarget): AgentSessionTerminalLaunchSpec? {
  target.launchSpec?.let { return it }
  return try {
    AgentSessionLaunchPlanner.plan(
      intent = AgentSessionLaunchIntent(
        projectPath = normalizeAgentWorkbenchPath(target.projectPath),
        projectDirectory = target.projectDirectory,
        provider = target.provider,
        operation = AgentSessionLaunchOperation.RESUME,
        sessionId = target.threadId,
      ),
    ).launchSpec
  }
  catch (t: Throwable) {
    LOG.warn(
      "Failed to resolve threadView rebind launch spec for ${target.provider.value}:${target.projectPath}:${target.threadId}",
      t,
    )
    AgentSessionTerminalLaunchSpec(command = listOf(target.provider.value, "resume", target.threadId))
  }
}

private suspend fun resolveRebindLaunchSpecs(
  targets: Sequence<AgentThreadViewTabRebindTarget>,
): Map<AgentThreadViewRebindLaunchSpecKey, AgentSessionTerminalLaunchSpec?> {
  val launchSpecsByTarget = LinkedHashMap<AgentThreadViewRebindLaunchSpecKey, AgentSessionTerminalLaunchSpec?>()
  for (target in targets) {
    val key = target.toRebindLaunchSpecKey()
    if (key !in launchSpecsByTarget) {
      launchSpecsByTarget[key] = resolveRebindLaunchSpec(target)
    }
  }
  return launchSpecsByTarget
}

suspend fun rebindOpenPendingAgentThreadViewTabs(
  provider: AgentSessionProvider,
  requestsByProjectPath: Map<String, List<AgentThreadViewPendingTabRebindRequest>>,
): AgentThreadViewPendingTabRebindReport {
  if (requestsByProjectPath.isEmpty()) {
    return emptyPendingTabRebindReport()
  }

  val normalizedRequestsByPath = normalizePathToListMap(requestsByProjectPath)
  if (normalizedRequestsByPath.isEmpty()) {
    return emptyPendingTabRebindReport()
  }

  val launchSpecsByTarget = resolveRebindLaunchSpecs(
    normalizedRequestsByPath.values.asSequence().flatten()
      .filter { request -> request.hasConcreteTargetForProvider(provider) }
      .map { request -> request.target }
  )
  val report = withContext(Dispatchers.UiWithModelAccess) {
    val openTabsSnapshot = collectOpenAgentThreadViewTabsSnapshot()

    var reboundBindings = 0
    val changedFiles = LinkedHashSet<AgentThreadViewVirtualFile>()
    val outcomesByPath = LinkedHashMap<String, MutableList<AgentThreadViewPendingTabRebindOutcome>>()
    for ((normalizedPath, requests) in normalizedRequestsByPath) {
      val outcomes = outcomesByPath.computeIfAbsent(normalizedPath) { ArrayList(requests.size) }
      for (request in requests) {
        val pendingFile = openTabsSnapshot.findPendingFile(provider, normalizedPath, request.pendingTabKey)
        if (pendingFile == null) {
          outcomes.add(
            AgentThreadViewPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewPendingTabRebindStatus.PENDING_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val managers = openTabsSnapshot.managersFor(pendingFile)
        if (managers.isEmpty()) {
          outcomes.add(
            AgentThreadViewPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewPendingTabRebindStatus.PENDING_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        if (
          !isPendingThreadIdentityForProvider(pendingFile.threadIdentity, provider) ||
          pendingFile.threadIdentity != request.pendingThreadIdentity
        ) {
          outcomes.add(
            AgentThreadViewPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewPendingTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        if (!request.hasConcreteTargetForProvider(provider)) {
          outcomes.add(
            AgentThreadViewPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewPendingTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val launchSpec = launchSpecsByTarget[request.target.toRebindLaunchSpecKey()]
        if (launchSpec == null) {
          outcomes.add(
            AgentThreadViewPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewPendingTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val targetIdentityAlreadyOpen = openTabsSnapshot.isTopLevelConcreteThreadIdentityOpen(
          normalizedPath = normalizedPath,
          threadIdentity = request.target.threadIdentity,
        )
        if (targetIdentityAlreadyOpen) {
          val closedTabs = openTabsSnapshot.closeTopLevelConcreteTabs(
            normalizedPath = normalizedPath,
            provider = provider,
            threadIdentity = request.target.threadIdentity,
          )
          if (closedTabs == 0) {
            outcomes.add(
              AgentThreadViewPendingTabRebindOutcome(
                projectPath = normalizedPath,
                request = request,
                status = AgentThreadViewPendingTabRebindStatus.TARGET_ALREADY_OPEN,
                reboundFiles = 0,
              )
            )
            continue
          }
        }

        val targetPresentation = resolveAgentThreadViewConcreteThreadPresentation(
          projectPath = request.target.projectPath,
          provider = request.target.provider,
          threadId = request.target.threadId,
          fallbackTitle = request.target.threadTitle,
          fallbackActivityReport = request.target.threadActivityReport,
        )
        val changed = pendingFile.rebindPendingThread(
          threadIdentity = request.target.threadIdentity,
          threadId = request.target.threadId,
          threadTitle = targetPresentation.title,
          threadActivityReport = targetPresentation.activityReport,
        )
        if (!changed) {
          outcomes.add(
            AgentThreadViewPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewPendingTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        reboundBindings++
        changedFiles.add(pendingFile)
        openTabsSnapshot.recordConcreteThreadIdentityOpen(normalizedPath, managers, request.target.threadIdentity)
        outcomes.add(
          AgentThreadViewPendingTabRebindOutcome(
            projectPath = normalizedPath,
            request = request,
            status = AgentThreadViewPendingTabRebindStatus.REBOUND,
            reboundFiles = 1,
          )
        )
      }
    }

    var updatedPresentations = 0
    for (changedFile in changedFiles) {
      val managers = openTabsSnapshot.managersFor(changedFile)
      for (manager in managers) {
        manager.updateFilePresentation(changedFile)
        updatedPresentations++
      }
    }
    if (changedFiles.isNotEmpty()) {
      service<AgentThreadViewOpenTabsPresentationStateService>().refreshOpenTabs()
    }

    val requestedBindings = normalizedRequestsByPath.values.sumOf { it.size }
    AgentThreadViewPendingTabRebindReport(
      requestedBindings = requestedBindings,
      reboundBindings = reboundBindings,
      reboundFiles = changedFiles.size,
      updatedPresentations = updatedPresentations,
      outcomesByPath = outcomesByPath,
    )
  }
  LOG.debug {
    "rebindOpenPendingAgentThreadViewTabs requestedBindings=${report.requestedBindings}, reboundBindings=${report.reboundBindings}, " +
    "reboundFiles=${report.reboundFiles}, updatedPresentations=${report.updatedPresentations}, paths=${report.outcomesByPath.size}"
  }
  return report
}

suspend fun rebindOpenConcreteAgentThreadViewTabs(
  provider: AgentSessionProvider,
  requestsByProjectPath: Map<String, List<AgentThreadViewConcreteTabRebindRequest>>,
): AgentThreadViewConcreteTabRebindReport {
  if (requestsByProjectPath.isEmpty()) {
    return emptyConcreteTabRebindReport()
  }

  val normalizedRequestsByPath = normalizePathToListMap(requestsByProjectPath)
  if (normalizedRequestsByPath.isEmpty()) {
    return emptyConcreteTabRebindReport()
  }

  val launchSpecsByTarget = resolveRebindLaunchSpecs(
    normalizedRequestsByPath.values.asSequence().flatten().map { request -> request.target }
  )
  val report = withContext(Dispatchers.UI) {
    val openTabsSnapshot = collectOpenAgentThreadViewTabsSnapshot()

    var reboundBindings = 0
    val changedFiles = LinkedHashSet<AgentThreadViewVirtualFile>()
    val restartLaunchSpecsByFile = LinkedHashMap<AgentThreadViewVirtualFile, AgentSessionTerminalLaunchSpec>()
    val outcomesByPath = LinkedHashMap<String, MutableList<AgentThreadViewConcreteTabRebindOutcome>>()
    for ((normalizedPath, requests) in normalizedRequestsByPath) {
      val outcomes = outcomesByPath.computeIfAbsent(normalizedPath) { ArrayList(requests.size) }
      for (request in requests) {
        val concreteFile = openTabsSnapshot.findConcreteFile(provider, normalizedPath, request.tabKey)
        if (concreteFile == null) {
          outcomes.add(
            AgentThreadViewConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewConcreteTabRebindStatus.CONCRETE_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val managers = openTabsSnapshot.managersFor(concreteFile)
        if (managers.isEmpty()) {
          outcomes.add(
            AgentThreadViewConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewConcreteTabRebindStatus.CONCRETE_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        if (
          concreteFile.provider != provider ||
          concreteFile.isPendingThread ||
          concreteFile.subAgentId != null ||
          concreteFile.threadIdentity != request.currentThreadIdentity ||
          concreteFile.newThreadRebindRequestedAtMs != request.newThreadRebindRequestedAtMs
        ) {
          outcomes.add(
            AgentThreadViewConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewConcreteTabRebindStatus.INVALID_CONCRETE_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val targetIdentityAlreadyOpen = openTabsSnapshot.isConcreteThreadIdentityOpenInAnyManager(
          normalizedPath = normalizedPath,
          managers = managers,
          threadIdentity = request.target.threadIdentity,
        )
        if (targetIdentityAlreadyOpen) {
          outcomes.add(
            AgentThreadViewConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewConcreteTabRebindStatus.TARGET_ALREADY_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val launchSpec = launchSpecsByTarget[request.target.toRebindLaunchSpecKey()]
        if (launchSpec == null) {
          outcomes.add(
            AgentThreadViewConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewConcreteTabRebindStatus.INVALID_CONCRETE_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val previousIdentity = concreteFile.threadIdentity
        val targetPresentation = resolveAgentThreadViewConcreteThreadPresentation(
          projectPath = request.target.projectPath,
          provider = request.target.provider,
          threadId = request.target.threadId,
          fallbackTitle = request.target.threadTitle,
          fallbackActivityReport = request.target.threadActivityReport,
        )
        val changed = concreteFile.rebindConcreteThread(
          threadIdentity = request.target.threadIdentity,
          threadId = request.target.threadId,
          threadTitle = targetPresentation.title,
          threadActivityReport = targetPresentation.activityReport,
        )
        if (!changed) {
          outcomes.add(
            AgentThreadViewConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentThreadViewConcreteTabRebindStatus.INVALID_CONCRETE_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        reboundBindings++
        changedFiles.add(concreteFile)
        restartLaunchSpecsByFile[concreteFile] = launchSpec
        openTabsSnapshot.replaceConcreteThreadIdentity(
          normalizedPath = normalizedPath,
          managers = managers,
          previousIdentity = previousIdentity,
          threadIdentity = request.target.threadIdentity,
        )
        outcomes.add(
          AgentThreadViewConcreteTabRebindOutcome(
            projectPath = normalizedPath,
            request = request,
            status = AgentThreadViewConcreteTabRebindStatus.REBOUND,
            reboundFiles = 1,
          )
        )
      }
    }

    var updatedPresentations = 0
    for (changedFile in changedFiles) {
      val managers = openTabsSnapshot.managersFor(changedFile)
      for (manager in managers) {
        manager.updateFilePresentation(changedFile)
        updatedPresentations++
      }
      restartLaunchSpecsByFile[changedFile]?.let { launchSpec ->
        restartOpenEditors(managers = managers, file = changedFile, startupLaunchSpec = launchSpec)
      }
    }

    val requestedBindings = normalizedRequestsByPath.values.sumOf { it.size }
    AgentThreadViewConcreteTabRebindReport(
      requestedBindings = requestedBindings,
      reboundBindings = reboundBindings,
      reboundFiles = changedFiles.size,
      updatedPresentations = updatedPresentations,
      outcomesByPath = outcomesByPath,
    )
  }
  LOG.debug {
    "rebindOpenConcreteAgentThreadViewTabs requestedBindings=${report.requestedBindings}, reboundBindings=${report.reboundBindings}, " +
    "reboundFiles=${report.reboundFiles}, updatedPresentations=${report.updatedPresentations}, paths=${report.outcomesByPath.size}"
  }
  return report
}

fun clearOpenConcreteAgentThreadViewNewThreadRebindAnchors(
  provider: AgentSessionProvider,
  tabsByProjectPath: Map<String, List<AgentThreadViewConcreteTabSnapshot>>,
): Int {
  if (tabsByProjectPath.isEmpty()) {
    return 0
  }

  val normalizedTabsByPath = normalizePathToListMap(tabsByProjectPath)
  if (normalizedTabsByPath.isEmpty()) {
    return 0
  }

  val openTabsSnapshot = collectOpenAgentThreadViewTabsSnapshot()

  var cleared = 0
  for ((normalizedPath, tabs) in normalizedTabsByPath) {
    for ((_, tabKey, currentThreadIdentity, newThreadRebindRequestedAtMs) in tabs) {
      val concreteFile = openTabsSnapshot.findConcreteFile(provider, normalizedPath, tabKey) ?: continue
      if (
        concreteFile.threadIdentity != currentThreadIdentity ||
        concreteFile.newThreadRebindRequestedAtMs != newThreadRebindRequestedAtMs
      ) {
        continue
      }
      if (!concreteFile.updateNewThreadRebindRequestedAtMs(newThreadRebindRequestedAtMs = null)) {
        continue
      }
      cleared++
    }
  }
  return cleared
}

suspend fun collectSelectedThreadViewThreadIdentity(): Pair<AgentSessionProvider, String>? = withContext(Dispatchers.UI) {
  collectOpenAgentThreadViewTabsSnapshot().selectedThreadViewThreadIdentity
}

private fun <T> normalizePathToListMap(pathToValues: Map<String, List<T>>): LinkedHashMap<String, List<T>> {
  val normalizedPathToValues = LinkedHashMap<String, List<T>>()
  for ((projectPath, values) in pathToValues) {
    if (values.isEmpty()) {
      continue
    }
    normalizedPathToValues[normalizeAgentWorkbenchPath(projectPath)] = values
  }
  return normalizedPathToValues
}

private fun emptyPendingTabRebindReport(): AgentThreadViewPendingTabRebindReport {
  return AgentThreadViewPendingTabRebindReport(
    requestedBindings = 0,
    reboundBindings = 0,
    reboundFiles = 0,
    updatedPresentations = 0,
    outcomesByPath = emptyMap(),
  )
}

private fun emptyConcreteTabRebindReport(): AgentThreadViewConcreteTabRebindReport {
  return AgentThreadViewConcreteTabRebindReport(
    requestedBindings = 0,
    reboundBindings = 0,
    reboundFiles = 0,
    updatedPresentations = 0,
    outcomesByPath = emptyMap(),
  )
}

private fun findExistingThreadView(
  openFiles: Array<VirtualFile>,
  threadIdentity: String,
  subAgentId: String?,
): AgentThreadViewVirtualFile? {
  for (openFile in openFiles) {
    val threadViewFile = openFile as? AgentThreadViewVirtualFile ?: continue
    if (threadViewFile.matches(threadIdentity, subAgentId)) {
      return threadViewFile
    }
  }
  return null
}

private fun findExistingThreadViewByTabKey(
  openFiles: Array<VirtualFile>,
  tabKey: String,
): AgentThreadViewVirtualFile? {
  for (openFile in openFiles) {
    val threadViewFile = openFile as? AgentThreadViewVirtualFile ?: continue
    if (threadViewFile.tabKey == tabKey) {
      return threadViewFile
    }
  }
  return null
}

private fun flushPendingInitialMessageForOpenEditors(
  manager: FileEditorManagerEx,
  file: AgentThreadViewVirtualFile,
) {
  manager.getAllEditors(file)
    .filterIsInstance<AgentThreadViewFileEditor>()
    .forEach { editor ->
      editor.flushPendingInitialMessageIfInitialized()
    }
}

private fun focusOpenThreadViewEditorPreferredComponent(
  project: Project,
  manager: FileEditorManagerEx,
  file: AgentThreadViewVirtualFile,
) {
  val editor = manager.getAllEditors(file)
                 .filterIsInstance<AgentThreadViewFileEditor>()
                 .firstOrNull()
               ?: return
  val component = editor.preferredFocusedComponent
  IdeFocusManager.getInstance(project).requestFocusInProject(component, project)
}

private fun refreshOpenEditors(
  manager: FileEditorManagerEx,
  file: AgentThreadViewVirtualFile,
) {
  manager.getAllEditors(file)
    .filterIsInstance<AgentThreadViewFileEditor>()
    .forEach { editor ->
      editor.refreshForFileStateChange()
    }
}

private suspend fun restartOpenEditors(
  managers: Set<FileEditorManagerEx>,
  file: AgentThreadViewVirtualFile,
  startupLaunchSpec: AgentSessionTerminalLaunchSpec,
) {
  var replaceRetainedTerminal = true
  for (manager in managers) {
    manager.getAllEditors(file)
      .filterIsInstance<AgentThreadViewFileEditor>()
      .forEach { editor ->
        val replaced = editor.restartForFileStateChange(
          startupLaunchSpec = startupLaunchSpec,
          replaceRetainedTerminal = replaceRetainedTerminal,
        )
        if (replaced) {
          replaceRetainedTerminal = false
        }
      }
  }
}
