// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-editor.spec.md

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

private class AgentChatEditorServiceLog

private val LOG = logger<AgentChatEditorServiceLog>()

private data class AgentChatScopedRefreshSignal(
  val provider: AgentSessionProvider,
  @JvmField val updateEvent: AgentSessionSourceUpdateEvent,
)

private object AgentChatScopedRefreshSignalBus {
  private val signalFlow = MutableSharedFlow<AgentChatScopedRefreshSignal>(extraBufferCapacity = 64)

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
      AgentChatScopedRefreshSignal(
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

data class AgentChatTabRebindTarget(
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

data class AgentChatPendingTabSnapshot(
  @JvmField val projectPath: String,
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val pendingCreatedAtMs: Long?,
  @JvmField val pendingFirstInputAtMs: Long?,
  @JvmField val pendingLaunchMode: String?,
  @JvmField val pinnedEditorTab: Boolean = false,
)

data class AgentChatConcreteTabSnapshot(
  @JvmField val projectPath: String,
  @JvmField val tabKey: String,
  @JvmField val currentThreadIdentity: String,
  @JvmField val newThreadRebindRequestedAtMs: Long,
)

data class AgentChatPendingTabRebindRequest(
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val target: AgentChatTabRebindTarget,
)

private fun AgentChatPendingTabRebindRequest.hasConcreteTargetForProvider(provider: AgentSessionProvider): Boolean {
  val targetCoordinates = resolveAgentChatThreadCoordinates(target.threadIdentity) ?: return false
  // Pending tabs may only rebind to discovered concrete identities; `new-*` targets are synthetic
  // placeholders used for projection and must be rejected here even if a caller passes one in.
  return target.provider == provider &&
         targetCoordinates.provider == provider &&
         !targetCoordinates.isPending &&
         target.threadIdentity != pendingThreadIdentity
}

data class AgentChatConcreteTabRebindRequest(
  @JvmField val tabKey: String,
  @JvmField val currentThreadIdentity: String,
  @JvmField val newThreadRebindRequestedAtMs: Long,
  @JvmField val target: AgentChatTabRebindTarget,
)

enum class AgentChatPendingTabRebindStatus {
  REBOUND,
  PENDING_TAB_NOT_OPEN,
  INVALID_PENDING_TAB,
  TARGET_ALREADY_OPEN,
}

data class AgentChatPendingTabRebindOutcome(
  @JvmField val projectPath: String,
  @JvmField val request: AgentChatPendingTabRebindRequest,
  @JvmField val status: AgentChatPendingTabRebindStatus,
  @JvmField val reboundFiles: Int,
)

data class AgentChatPendingTabRebindReport(
  @JvmField val requestedBindings: Int,
  @JvmField val reboundBindings: Int,
  @JvmField val reboundFiles: Int,
  @JvmField val updatedPresentations: Int,
  @JvmField val outcomesByPath: Map<String, List<AgentChatPendingTabRebindOutcome>>,
)

enum class AgentChatConcreteTabRebindStatus {
  REBOUND,
  CONCRETE_TAB_NOT_OPEN,
  INVALID_CONCRETE_TAB,
  TARGET_ALREADY_OPEN,
}

data class AgentChatConcreteTabRebindOutcome(
  @JvmField val projectPath: String,
  @JvmField val request: AgentChatConcreteTabRebindRequest,
  @JvmField val status: AgentChatConcreteTabRebindStatus,
  @JvmField val reboundFiles: Int,
)

data class AgentChatConcreteTabRebindReport(
  @JvmField val requestedBindings: Int,
  @JvmField val reboundBindings: Int,
  @JvmField val reboundFiles: Int,
  @JvmField val updatedPresentations: Int,
  @JvmField val outcomesByPath: Map<String, List<AgentChatConcreteTabRebindOutcome>>,
)

suspend fun openChat(
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
  newSessionProvider: AgentSessionProvider? = null,
  newSessionLaunchMode: AgentSessionLaunchMode? = null,
  initialMessageDispatchPlan: AgentInitialPromptDeliveryPlan = AgentInitialPromptDeliveryPlan.EMPTY,
  generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  persistSnapshot: Boolean = true,
  deferredStartState: AgentChatDeferredStartState? = null,
  deferredStartContent: AgentChatDeferredStartContent? = null,
  startupLaunchSpec: AgentSessionTerminalLaunchSpec? = null,
): VirtualFile {
  val manager = FileEditorManagerEx.getInstanceExAsync(project)

  val tabKey = AgentChatTabKey.fromIdentity(
    AgentChatTabIdentity(
      projectHash = project.locationHash,
      projectPath = projectPath,
      projectDirectory = projectDirectory,
      threadIdentity = threadIdentity,
      subAgentId = subAgentId,
    )
  )
  val existing = findExistingChatByTabKey(manager.openFiles, tabKey.value)
                 ?: findExistingChat(manager.openFiles, threadIdentity, subAgentId)
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
    ) ?: pendingProviderForThreadIdentity(threadIdentity)?.let { provider ->
      AgentChatStartupIntent.NewSession(provider = provider,
                                        launchMode = parseAgentChatLaunchMode(pendingLaunchMode),
                                        launchProfileId = effectiveLaunchProfileId,
                                        launchTargetId = effectiveLaunchTargetId)
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
  val snapshot = AgentChatTabSnapshot.create(
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
    generationSettings = effectiveGenerationSettings,
    newThreadRebindRequestedAtMs = existing?.newThreadRebindRequestedAtMs,
    initialPromptRecord = snapshotInitialPromptRecord,
    terminalPromptDispatch = snapshotTerminalPromptDispatch,
  )
  LOG.debug {
    "openChat(project=${project.name}, path=$projectPath, identity=$threadIdentity, " +
    "subAgentId=$subAgentId, existing=${existing != null}, title=$threadTitle)"
  }
  val file = existing ?: agentChatVirtualFileSystem().getOrCreateFile(snapshot)
  if (deferredStartContent != null) {
    file.replaceDeferredStartContent(deferredStartContent)
  }
  if (existing != null) {
    val oldLaunchMode = existing.launchMode
    existing.updateRestoreOnRestart(persistSnapshot)
    if (!persistSnapshot) {
      existing.updateStartupIntent(null)
    }
    existing.updateFromResolution(AgentChatTabResolution.Resolved(snapshot))
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
      "openChat existing tab update(identity=$threadIdentity, subAgentId=$subAgentId): " +
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
      "openChat created new tab(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name}, activity=$threadActivity)"
    }
  }
  if (ApplicationManager.getApplication().isUnitTestMode) {
    // TestEditorManagerImpl uses FileEditorProvider.KEY for non-text editors and otherwise falls back to doOpenTextEditor.
    file.putUserData(FileEditorProvider.KEY, AgentChatFileEditorProvider())
  }
  val focusDeferredStartContent = deferredStartContent != null
  withContext(Dispatchers.UiWithModelAccess) {
    manager.openFile(file = file, options = FileEditorOpenOptions(requestFocus = true, reuseOpen = true))
    if (existing != null && hasExplicitInitialPromptDelivery && !file.initialMessageSent) {
      flushPendingInitialMessageForOpenEditors(manager = manager, file = file)
    }
  }
  LOG.debug {
    "openChat openFile completed(identity=$threadIdentity, subAgentId=$subAgentId, fileName=${file.name}, activity=$threadActivity)"
  }

  val pendingProvider = pendingProviderForThreadIdentity(threadIdentity)
  if (pendingProvider != null) {
    project.service<AgentChatPendingEditorLifecycleService>()
    service<AgentChatOpenTabsPresentationStateService>().refreshOpenTabs()
    if (AgentSessionProviders.find(pendingProvider)?.emitsScopedRefreshSignals == true) {
      notifyAgentChatScopedRefresh(provider = pendingProvider, projectPath = projectPath)
    }
  }

  if (focusDeferredStartContent) {
    withContext(Dispatchers.UiWithModelAccess) {
      focusOpenChatEditorPreferredComponent(project = project, manager = manager, file = file)
    }
    withContext(Dispatchers.EDT) {
      yield()
      focusOpenChatEditorPreferredComponent(project = project, manager = manager, file = file)
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
): AgentChatStartupIntent.NewSession? {
  val resolvedProvider = provider ?: return null
  return AgentChatStartupIntent.NewSession(
    provider = resolvedProvider,
    launchMode = launchMode ?: AgentSessionLaunchMode.STANDARD,
    launchProfileId = launchProfileId,
    launchTargetId = launchTargetId,
  )
}

fun persistAgentChatTabMetadata(file: VirtualFile) {
  if (file !is AgentChatVirtualFile) return
  file.updateRestoreOnRestart(true)
  // Agent Chat restore metadata is serialized from FileEditor.getState() with the workspace editor state.
}

suspend fun refreshOpenAgentChatFile(project: Project, file: VirtualFile) {
  val chatFile = file as? AgentChatVirtualFile ?: return
  val manager = FileEditorManagerEx.getInstanceExAsync(project)
  withContext(Dispatchers.EDT) {
    manager.updateFilePresentation(chatFile)
    refreshOpenEditors(manager = manager, file = chatFile)
  }
}

suspend fun updateAgentChatDeferredStartState(
  project: Project,
  file: VirtualFile,
  deferredStartState: AgentChatDeferredStartState?,
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
  generationSettings: AgentPromptGenerationSettings? = null,
  persistSnapshot: Boolean = false,
  forgetPersistedSnapshot: Boolean = false,
) {
  val chatFile = file as? AgentChatVirtualFile ?: return
  startupLaunchSpecOverride?.let { launchSpec ->
    chatFile.setStartupLaunchSpecOverride(
      launchSpec = launchSpec,
      suppressInitialMessageDispatch = initialMessageDispatchPlan?.startupLaunchSpecOverride != null,
    )
  }
  chatFile.updateDeferredStartState(deferredStartState)
  if (threadIdentity != null && threadId != null) {
    chatFile.rebindPendingThread(
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle ?: chatFile.threadTitle,
      threadActivity = threadActivity ?: chatFile.threadActivity,
    )
  }
  else {
    threadActivity?.let {
      chatFile.updateBootstrapThreadActivity(it)
    }
  }
  if (pendingCreatedAtMs != null || pendingLaunchMode != null) {
    chatFile.updatePendingMetadata(
      pendingCreatedAtMs = pendingCreatedAtMs,
      pendingFirstInputAtMs = chatFile.pendingFirstInputAtMs,
      pendingLaunchMode = pendingLaunchMode,
    )
  }
  launchProfileId?.let {
    chatFile.updateLaunchProfileId(it)
  }
  launchTargetId?.let {
    chatFile.updateLaunchTargetId(it)
  }
  generationSettings?.let {
    chatFile.updateGenerationSettings(it)
  }
  initialMessageDispatchPlan?.let { dispatchPlan ->
    chatFile.updateInitialPromptDelivery(
      promptRecord = dispatchPlan.promptRecord,
      terminalDispatch = dispatchPlan.terminalDispatch,
    )
  }
  if (persistSnapshot) {
    chatFile.updateRestoreOnRestart(true)
    if (deferredStartState?.phase == AgentChatDeferredStartPhase.READY_TO_START) {
      chatFile.updateStartupIntent(
        buildNewSessionStartupIntent(
          provider = newSessionProvider,
          launchMode = newSessionLaunchMode,
          launchProfileId = chatFile.launchProfileId,
          launchTargetId = chatFile.launchTargetId,
        ) ?: resolveAgentChatNewSessionStartupIntent(chatFile)
      )
    }
    persistAgentChatTabMetadata(chatFile)
  }
  else if (forgetPersistedSnapshot) {
    chatFile.updateRestoreOnRestart(false)
    chatFile.updateStartupIntent(null)
    forgetAgentChatTabMetadata(chatFile.tabKey)
  }
  refreshOpenAgentChatFile(project = project, file = chatFile)
}

suspend fun collectOpenPendingAgentChatProjectPaths(): Set<String> {
  return collectOpenAgentChatProjectPaths(includePendingOnly = true)
}

fun notifyAgentChatScopedRefresh(
  provider: AgentSessionProvider,
  projectPath: String,
  threadId: String? = null,
  activityReport: AgentThreadActivityReport? = null,
) {
  AgentChatScopedRefreshSignalBus.signal(provider, projectPath, threadId, activityReport)
}

fun notifyAgentChatScopedRefresh(
  provider: AgentSessionProvider,
  projectPath: String,
  threadId: String?,
  threadTitle: String?,
  activityReport: AgentThreadActivityReport?,
) {
  AgentChatScopedRefreshSignalBus.signal(provider, projectPath, threadId, threadTitle, activityReport)
}

fun notifyAgentChatScopedRefresh(
  provider: AgentSessionProvider,
  updateEvent: AgentSessionSourceUpdateEvent,
) {
  AgentChatScopedRefreshSignalBus.signal(provider, updateEvent)
}

fun agentChatScopedRefreshSignals(provider: AgentSessionProvider): Flow<AgentSessionSourceUpdateEvent> {
  return AgentChatScopedRefreshSignalBus.signals(provider)
}

suspend fun collectOpenPendingAgentChatTabsByPath(
  provider: AgentSessionProvider,
): Map<String, List<AgentChatPendingTabSnapshot>> {
  return collectOpenAgentChatTabsSnapshotOnUi().pendingTabsByPath(provider)
}

suspend fun collectOpenConcreteAgentChatTabsAwaitingNewThreadRebindByPath(
  provider: AgentSessionProvider,
): Map<String, List<AgentChatConcreteTabSnapshot>> {
  return collectOpenAgentChatTabsSnapshotOnUi().concreteTabsAwaitingNewThreadRebindByPath(provider)
}

suspend fun collectOpenConcreteAgentChatThreadIdentitiesByPath(): Map<String, Set<String>> {
  return collectOpenAgentChatTabsSnapshotOnUi().concreteThreadIdentitiesByPath()
}

suspend fun addContextToOpenTopLevelAgentChat(
  projectPath: String,
  provider: AgentSessionProvider,
  threadId: String,
  contextItems: List<AgentPromptContextItem>,
): AgentPromptAddContextToTargetResult = withContext(Dispatchers.UiWithModelAccess) {
  val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
  if (contextItems.isEmpty()) {
    return@withContext AgentPromptAddContextToTargetResult.UNAVAILABLE
  }
  val openEntry = collectOpenAgentChatTabsSnapshot().findOpenTopLevelConcreteEntry(
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
  val editor = manager.getAllEditors(openEntry.file).filterIsInstance<AgentChatFileEditor>().firstOrNull()
               ?: return@withContext AgentPromptAddContextToTargetResult.UNAVAILABLE
  if (editor.addPendingContextItems(contextItems)) {
    AgentPromptAddContextToTargetResult.ADDED_TO_CHAT
  }
  else {
    AgentPromptAddContextToTargetResult.ALREADY_ADDED_TO_CHAT
  }
}

private suspend fun collectOpenAgentChatProjectPaths(includePendingOnly: Boolean): Set<String> {
  return collectOpenAgentChatTabsSnapshotOnUi().projectPaths(includePendingOnly)
}

private data class AgentChatRebindLaunchSpecKey(
  val projectPath: String,
  val projectDirectory: String?,
  val provider: AgentSessionProvider,
  val threadId: String,
)

private fun AgentChatTabRebindTarget.toRebindLaunchSpecKey(): AgentChatRebindLaunchSpecKey {
  return AgentChatRebindLaunchSpecKey(
    projectPath = normalizeAgentWorkbenchPath(projectPath),
    projectDirectory = projectDirectory?.takeIf { it.isNotBlank() }?.let(::normalizeAgentWorkbenchPath),
    provider = provider,
    threadId = threadId,
  )
}

private suspend fun resolveRebindLaunchSpec(target: AgentChatTabRebindTarget): AgentSessionTerminalLaunchSpec? {
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
      "Failed to resolve chat rebind launch spec for ${target.provider.value}:${target.projectPath}:${target.threadId}",
      t,
    )
    AgentSessionTerminalLaunchSpec(command = listOf(target.provider.value, "resume", target.threadId))
  }
}

private suspend fun resolveRebindLaunchSpecs(
  targets: Sequence<AgentChatTabRebindTarget>,
): Map<AgentChatRebindLaunchSpecKey, AgentSessionTerminalLaunchSpec?> {
  val launchSpecsByTarget = LinkedHashMap<AgentChatRebindLaunchSpecKey, AgentSessionTerminalLaunchSpec?>()
  for (target in targets) {
    val key = target.toRebindLaunchSpecKey()
    if (key !in launchSpecsByTarget) {
      launchSpecsByTarget[key] = resolveRebindLaunchSpec(target)
    }
  }
  return launchSpecsByTarget
}

suspend fun rebindOpenPendingAgentChatTabs(
  provider: AgentSessionProvider,
  requestsByProjectPath: Map<String, List<AgentChatPendingTabRebindRequest>>,
): AgentChatPendingTabRebindReport {
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
    val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()

    var reboundBindings = 0
    val changedFiles = LinkedHashSet<AgentChatVirtualFile>()
    val outcomesByPath = LinkedHashMap<String, MutableList<AgentChatPendingTabRebindOutcome>>()
    for ((normalizedPath, requests) in normalizedRequestsByPath) {
      val outcomes = outcomesByPath.computeIfAbsent(normalizedPath) { ArrayList(requests.size) }
      for (request in requests) {
        val pendingFile = openTabsSnapshot.findPendingFile(provider, normalizedPath, request.pendingTabKey)
        if (pendingFile == null) {
          outcomes.add(
            AgentChatPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingTabRebindStatus.PENDING_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val managers = openTabsSnapshot.managersFor(pendingFile)
        if (managers.isEmpty()) {
          outcomes.add(
            AgentChatPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingTabRebindStatus.PENDING_TAB_NOT_OPEN,
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
            AgentChatPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        if (!request.hasConcreteTargetForProvider(provider)) {
          outcomes.add(
            AgentChatPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val launchSpec = launchSpecsByTarget[request.target.toRebindLaunchSpecKey()]
        if (launchSpec == null) {
          outcomes.add(
            AgentChatPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingTabRebindStatus.INVALID_PENDING_TAB,
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
              AgentChatPendingTabRebindOutcome(
                projectPath = normalizedPath,
                request = request,
                status = AgentChatPendingTabRebindStatus.TARGET_ALREADY_OPEN,
                reboundFiles = 0,
              )
            )
            continue
          }
        }

        val targetPresentation = resolveAgentChatConcreteThreadPresentation(
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
            AgentChatPendingTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatPendingTabRebindStatus.INVALID_PENDING_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        reboundBindings++
        changedFiles.add(pendingFile)
        openTabsSnapshot.recordConcreteThreadIdentityOpen(normalizedPath, managers, request.target.threadIdentity)
        outcomes.add(
          AgentChatPendingTabRebindOutcome(
            projectPath = normalizedPath,
            request = request,
            status = AgentChatPendingTabRebindStatus.REBOUND,
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
      service<AgentChatOpenTabsPresentationStateService>().refreshOpenTabs()
    }

    val requestedBindings = normalizedRequestsByPath.values.sumOf { it.size }
    AgentChatPendingTabRebindReport(
      requestedBindings = requestedBindings,
      reboundBindings = reboundBindings,
      reboundFiles = changedFiles.size,
      updatedPresentations = updatedPresentations,
      outcomesByPath = outcomesByPath,
    )
  }
  LOG.debug {
    "rebindOpenPendingAgentChatTabs requestedBindings=${report.requestedBindings}, reboundBindings=${report.reboundBindings}, " +
    "reboundFiles=${report.reboundFiles}, updatedPresentations=${report.updatedPresentations}, paths=${report.outcomesByPath.size}"
  }
  return report
}

suspend fun rebindOpenConcreteAgentChatTabs(
  provider: AgentSessionProvider,
  requestsByProjectPath: Map<String, List<AgentChatConcreteTabRebindRequest>>,
): AgentChatConcreteTabRebindReport {
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
    val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()

    var reboundBindings = 0
    val changedFiles = LinkedHashSet<AgentChatVirtualFile>()
    val restartLaunchSpecsByFile = LinkedHashMap<AgentChatVirtualFile, AgentSessionTerminalLaunchSpec>()
    val outcomesByPath = LinkedHashMap<String, MutableList<AgentChatConcreteTabRebindOutcome>>()
    for ((normalizedPath, requests) in normalizedRequestsByPath) {
      val outcomes = outcomesByPath.computeIfAbsent(normalizedPath) { ArrayList(requests.size) }
      for (request in requests) {
        val concreteFile = openTabsSnapshot.findConcreteFile(provider, normalizedPath, request.tabKey)
        if (concreteFile == null) {
          outcomes.add(
            AgentChatConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteTabRebindStatus.CONCRETE_TAB_NOT_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val managers = openTabsSnapshot.managersFor(concreteFile)
        if (managers.isEmpty()) {
          outcomes.add(
            AgentChatConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteTabRebindStatus.CONCRETE_TAB_NOT_OPEN,
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
            AgentChatConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteTabRebindStatus.INVALID_CONCRETE_TAB,
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
            AgentChatConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteTabRebindStatus.TARGET_ALREADY_OPEN,
              reboundFiles = 0,
            )
          )
          continue
        }

        val launchSpec = launchSpecsByTarget[request.target.toRebindLaunchSpecKey()]
        if (launchSpec == null) {
          outcomes.add(
            AgentChatConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteTabRebindStatus.INVALID_CONCRETE_TAB,
              reboundFiles = 0,
            )
          )
          continue
        }

        val previousIdentity = concreteFile.threadIdentity
        val targetPresentation = resolveAgentChatConcreteThreadPresentation(
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
            AgentChatConcreteTabRebindOutcome(
              projectPath = normalizedPath,
              request = request,
              status = AgentChatConcreteTabRebindStatus.INVALID_CONCRETE_TAB,
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
          AgentChatConcreteTabRebindOutcome(
            projectPath = normalizedPath,
            request = request,
            status = AgentChatConcreteTabRebindStatus.REBOUND,
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
    AgentChatConcreteTabRebindReport(
      requestedBindings = requestedBindings,
      reboundBindings = reboundBindings,
      reboundFiles = changedFiles.size,
      updatedPresentations = updatedPresentations,
      outcomesByPath = outcomesByPath,
    )
  }
  LOG.debug {
    "rebindOpenConcreteAgentChatTabs requestedBindings=${report.requestedBindings}, reboundBindings=${report.reboundBindings}, " +
    "reboundFiles=${report.reboundFiles}, updatedPresentations=${report.updatedPresentations}, paths=${report.outcomesByPath.size}"
  }
  return report
}

fun clearOpenConcreteAgentChatNewThreadRebindAnchors(
  provider: AgentSessionProvider,
  tabsByProjectPath: Map<String, List<AgentChatConcreteTabSnapshot>>,
): Int {
  if (tabsByProjectPath.isEmpty()) {
    return 0
  }

  val normalizedTabsByPath = normalizePathToListMap(tabsByProjectPath)
  if (normalizedTabsByPath.isEmpty()) {
    return 0
  }

  val openTabsSnapshot = collectOpenAgentChatTabsSnapshot()

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

suspend fun collectSelectedChatThreadIdentity(): Pair<AgentSessionProvider, String>? = withContext(Dispatchers.UI) {
  collectOpenAgentChatTabsSnapshot().selectedChatThreadIdentity
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

private fun emptyPendingTabRebindReport(): AgentChatPendingTabRebindReport {
  return AgentChatPendingTabRebindReport(
    requestedBindings = 0,
    reboundBindings = 0,
    reboundFiles = 0,
    updatedPresentations = 0,
    outcomesByPath = emptyMap(),
  )
}

private fun emptyConcreteTabRebindReport(): AgentChatConcreteTabRebindReport {
  return AgentChatConcreteTabRebindReport(
    requestedBindings = 0,
    reboundBindings = 0,
    reboundFiles = 0,
    updatedPresentations = 0,
    outcomesByPath = emptyMap(),
  )
}

private fun findExistingChat(
  openFiles: Array<VirtualFile>,
  threadIdentity: String,
  subAgentId: String?,
): AgentChatVirtualFile? {
  for (openFile in openFiles) {
    val chatFile = openFile as? AgentChatVirtualFile ?: continue
    if (chatFile.matches(threadIdentity, subAgentId)) {
      return chatFile
    }
  }
  return null
}

private fun findExistingChatByTabKey(
  openFiles: Array<VirtualFile>,
  tabKey: String,
): AgentChatVirtualFile? {
  for (openFile in openFiles) {
    val chatFile = openFile as? AgentChatVirtualFile ?: continue
    if (chatFile.tabKey == tabKey) {
      return chatFile
    }
  }
  return null
}

private fun flushPendingInitialMessageForOpenEditors(
  manager: FileEditorManagerEx,
  file: AgentChatVirtualFile,
) {
  manager.getAllEditors(file)
    .filterIsInstance<AgentChatFileEditor>()
    .forEach { editor ->
      editor.flushPendingInitialMessageIfInitialized()
    }
}

private fun focusOpenChatEditorPreferredComponent(
  project: Project,
  manager: FileEditorManagerEx,
  file: AgentChatVirtualFile,
) {
  val editor = manager.getAllEditors(file)
                 .filterIsInstance<AgentChatFileEditor>()
                 .firstOrNull()
               ?: return
  val component = editor.preferredFocusedComponent
  IdeFocusManager.getInstance(project).requestFocusInProject(component, project)
}

private fun refreshOpenEditors(
  manager: FileEditorManagerEx,
  file: AgentChatVirtualFile,
) {
  manager.getAllEditors(file)
    .filterIsInstance<AgentChatFileEditor>()
    .forEach { editor ->
      editor.refreshForFileStateChange()
    }
}

private suspend fun restartOpenEditors(
  managers: Set<FileEditorManagerEx>,
  file: AgentChatVirtualFile,
  startupLaunchSpec: AgentSessionTerminalLaunchSpec,
) {
  var replaceRetainedTerminal = true
  for (manager in managers) {
    manager.getAllEditors(file)
      .filterIsInstance<AgentChatFileEditor>()
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
