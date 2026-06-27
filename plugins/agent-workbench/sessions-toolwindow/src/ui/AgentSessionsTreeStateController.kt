// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.agent.workbench.chat.AgentChatOpenTabsPresentationState
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.model.AgentArchivedSessionsState
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.state.AgentSessionLaunchProfileStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionTreeUiStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewState
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModelDiff
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeRootPresentation
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.diffSessionTreeModels
import com.intellij.agent.workbench.sessions.toolwindow.tree.overlayPendingAgentChatTabs
import com.intellij.agent.workbench.sessions.toolwindow.tree.resolveSelectedSessionTreeId
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.serviceAsync
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities

internal class AgentSessionsTreeStateController(
  private val sessionsStateFlow: StateFlow<AgentSessionsState>,
  private val archivedSessionsStateFlow: StateFlow<AgentArchivedSessionsState>,
  private val threadViewStateFlow: StateFlow<AgentSessionThreadViewState>,
  private val selectedChatTabFlow: StateFlow<AgentChatTabSelection?>,
  private val openChatTabsPresentationStateFlow: StateFlow<AgentChatOpenTabsPresentationState>,
  private val ensureArchivedSessionsLoaded: () -> Unit,
  private val tree: Tree,
  private val getSessionTreeModel: () -> SessionTreeModel,
  private val setSessionTreeModel: (SessionTreeModel) -> Unit,
  private val onNewThreadProfileMenuChanged: () -> Unit,
  private val isCurrentProjectScopeEnabled: () -> Boolean,
  private val currentProjectPathProvider: () -> String?,
  private val onBeforeModelSwap: () -> Unit,
  private val invalidateTreeModel: (SessionTreeModelDiff) -> CompletableFuture<*>,
  private val expandNode: (SessionTreeId) -> Unit,
  private val selectNodes: (List<SessionTreeId>, () -> Boolean, Boolean, (List<SessionTreeId>) -> Unit) -> Unit,
  private val nowProvider: () -> Long = System::currentTimeMillis,
) {
  @Suppress("RAW_SCOPE_CREATION")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.UI)

  private var activeSessionsState: AgentSessionsState = AgentSessionsState()
  private var archivedSessionsState: AgentArchivedSessionsState = AgentArchivedSessionsState()
  private var threadViewState: AgentSessionThreadViewState = AgentSessionThreadViewState()
  private var selectedChatTab: AgentChatTabSelection? = null
  private var openChatTabsPresentationState: AgentChatOpenTabsPresentationState = AgentChatOpenTabsPresentationState.EMPTY
  private var treeUpdateSequence: Long = 0
  private var rebuildJob: Job? = null
  private var treeSelectionInitialized = false
  private var lastAppliedSelectedTreeIds: List<SessionTreeId> = emptyList()
  private var modelUpdatesVisible: Boolean = true
  private var pendingRebuildReason: SessionTreeRebuildReason? = null

  fun start() {
    scope.launch {
      sessionsStateFlow.collect { newState ->
        activeSessionsState = newState
        rebuildTree(SessionTreeRebuildReason.SESSION_STATE_CHANGED)
      }
    }

    scope.launch {
      archivedSessionsStateFlow.collect { newState ->
        archivedSessionsState = newState
        rebuildTree(SessionTreeRebuildReason.SESSION_STATE_CHANGED)
      }
    }

    scope.launch {
      threadViewStateFlow.collect { newState ->
        threadViewState = newState
        if (newState.mode == AgentSessionThreadViewMode.ARCHIVED) {
          ensureArchivedSessionsLoaded()
        }
        rebuildTree(SessionTreeRebuildReason.THREAD_VIEW_CHANGED)
      }
    }

    scope.launch {
      selectedChatTabFlow.collect { selection ->
        selectedChatTab = selection
        applyChatSelection(selection)
      }
    }

    scope.launch {
      openChatTabsPresentationStateFlow.collect { state ->
        openChatTabsPresentationState = state
        rebuildTree(SessionTreeRebuildReason.OPEN_CHAT_TABS_PRESENTATION_CHANGED)
      }
    }

    scope.launch {
      serviceAsync<AgentSessionLaunchProfileStateService>().launchProfileStateFlow.collect {
        onNewThreadProfileMenuChanged()
      }
    }
  }

  fun setModelUpdatesVisible(visible: Boolean) {
    if (modelUpdatesVisible == visible) return
    modelUpdatesVisible = visible

    if (!visible) {
      if (rebuildJob?.isActive == true) {
        pendingRebuildReason = coalesceSessionTreeRebuildReason(
          current = pendingRebuildReason,
          next = SessionTreeRebuildReason.SESSION_STATE_CHANGED,
        )
      }
      rebuildJob?.cancel()
      treeUpdateSequence++
      return
    }

    val pendingReason = pendingRebuildReason ?: return
    pendingRebuildReason = null
    rebuildTree(pendingReason)
  }

  fun displayedStateSnapshot(): AgentSessionsState {
    val state = when (threadViewState.mode) {
      AgentSessionThreadViewMode.ACTIVE -> overlayPendingAgentChatTabs(
        state = activeSessionsState,
        openTabsPresentationState = openChatTabsPresentationState,
      )
      AgentSessionThreadViewMode.ARCHIVED -> buildArchivedDisplayState(
        archivedState = archivedSessionsState,
        rangePreset = threadViewState.archivedRangePreset,
        nowMs = nowProvider(),
      )
    }
    return state.filterToCurrentProjectSessions(currentProjectPathForScope())
  }

  fun isSingleProjectPresentationEnabled(): Boolean {
    return currentProjectPathForScope() != null
  }

  fun projectScopeChanged() {
    rebuildTree(SessionTreeRebuildReason.PROJECT_SCOPE_CHANGED)
  }

  fun dispose() {
    rebuildJob?.cancel()
    scope.cancel("Agent sessions tree state controller disposed")
  }

  @TestOnly
  internal fun hasPendingModelUpdateForTest(): Boolean = pendingRebuildReason != null

  private fun rebuildTree(reason: SessionTreeRebuildReason) {
    if (!modelUpdatesVisible) {
      pendingRebuildReason = coalesceSessionTreeRebuildReason(
        current = pendingRebuildReason,
        next = reason,
      )
      return
    }

    rebuildJob?.cancel()
    val snapshotState = displayedStateSnapshot()
    val snapshotThreadViewState = threadViewState
    val snapshotSelectedChatTab = selectedChatTab
    val snapshotOpenTabsPresentationState = openChatTabsPresentationState
    val snapshotRootPresentation = if (isSingleProjectPresentationEnabled()) {
      SessionTreeRootPresentation.SINGLE_PROJECT_CONTENTS
    }
    else {
      SessionTreeRootPresentation.PROJECTS
    }
    val oldModel = getSessionTreeModel()
    val updateSequence = ++treeUpdateSequence
    rebuildJob = scope.launch {
      val (nextModel, treeModelDiff, selectedTreeId) = withContext(Default) {
        val model = buildSessionTreeModel(
          projects = snapshotState.projects,
          visibleClosedProjectCount = snapshotState.visibleClosedProjectCount,
          visibleThreadCounts = snapshotState.visibleThreadCounts,
          treeUiState = serviceAsync<AgentSessionTreeUiStateService>(),
          rootPresentation = snapshotRootPresentation,
          openTabsPresentationState = if (snapshotThreadViewState.mode == AgentSessionThreadViewMode.ACTIVE) {
            snapshotOpenTabsPresentationState
          }
          else {
            AgentChatOpenTabsPresentationState.EMPTY
          },
        )
        val diff = diffSessionTreeModels(oldModel, model)
        val selection = if (snapshotThreadViewState.mode == AgentSessionThreadViewMode.ACTIVE) {
          resolveSelectedSessionTreeId(snapshotState.projects, snapshotSelectedChatTab)
        }
        else {
          null
        }
        Triple(model, diff, selection)
      }
      if (treeUpdateSequence != updateSequence) return@launch

      val selectedTreeIdsBeforeModelSwap = selectedTreeIds()
      val expandedTreeIdsBeforeModelSwap = expandedTreeIds()
      val selectedTreeIds = sessionTreeSelectionTargetsAfterModelSwap(
        model = nextModel,
        reason = reason,
        previouslySelectedTreeIds = selectedTreeIdsBeforeModelSwap,
        selectedChatTreeId = selectedTreeId,
        selectionInitialized = treeSelectionInitialized,
        lastAppliedSelectedTreeIds = lastAppliedSelectedTreeIds,
      )
      val shouldApplySelection = shouldApplySelectionAfterModelSwap(
        treeModelDiff = treeModelDiff,
        selectedTreeIds = selectedTreeIds,
        selectedTreeIdsBeforeModelSwap = selectedTreeIdsBeforeModelSwap,
        treeSelectionInitialized = treeSelectionInitialized,
        lastAppliedSelectedTreeIds = lastAppliedSelectedTreeIds,
      )
      val shouldApplyExpansion = !treeModelDiff.hasOnlyContentChanges() || shouldApplySelection
      if (!treeModelDiff.isEmpty()) {
        onBeforeModelSwap()
      }
      setSessionTreeModel(nextModel)
      updateEmptyText()

      if (treeModelDiff.isEmpty()) {
        SwingUtilities.invokeLater {
          if (treeUpdateSequence != updateSequence) return@invokeLater
          if (shouldApplyExpansion) {
            applyDefaultExpansion(
              model = nextModel,
              previousModel = oldModel,
              rootChanged = false,
              previouslyExpandedTreeIds = expandedTreeIdsBeforeModelSwap,
              selectedTreeIds = selectedTreeIds,
            )
          }
          if (shouldApplySelection) {
            applySelection(
              selectedTreeIds = selectedTreeIds,
              updateSequence = updateSequence,
              scrollToVisible = reason != SessionTreeRebuildReason.SESSION_STATE_CHANGED,
            )
          }
        }
        return@launch
      }

      invalidateTreeModel(treeModelDiff).thenRun {
        SwingUtilities.invokeLater {
          if (treeUpdateSequence != updateSequence) return@invokeLater
          if (shouldApplyExpansion) {
            applyDefaultExpansion(
              model = nextModel,
              previousModel = oldModel,
              rootChanged = treeModelDiff.rootChanged,
              previouslyExpandedTreeIds = expandedTreeIdsBeforeModelSwap,
              selectedTreeIds = selectedTreeIds,
            )
          }
          if (shouldApplySelection) {
            applySelection(
              selectedTreeIds = selectedTreeIds,
              updateSequence = updateSequence,
              scrollToVisible = reason != SessionTreeRebuildReason.SESSION_STATE_CHANGED,
            )
          }
        }
      }
    }
  }

  private fun applyChatSelection(selection: AgentChatTabSelection?) {
    if (!modelUpdatesVisible) {
      pendingRebuildReason = coalesceSessionTreeRebuildReason(
        current = pendingRebuildReason,
        next = SessionTreeRebuildReason.CHAT_TAB_SELECTION_CHANGED,
      )
      return
    }

    val updateSequence = treeUpdateSequence
    val selectedTreeId = if (threadViewState.mode == AgentSessionThreadViewMode.ACTIVE) {
      resolveSelectedSessionTreeId(displayedStateSnapshot().projects, selection)
    }
    else {
      null
    }
    val selectedTreeIds = sessionTreeSelectionTargetsAfterModelSwap(
      model = getSessionTreeModel(),
      reason = SessionTreeRebuildReason.CHAT_TAB_SELECTION_CHANGED,
      previouslySelectedTreeIds = selectedTreeIds(),
      selectedChatTreeId = selectedTreeId,
      selectionInitialized = treeSelectionInitialized,
      lastAppliedSelectedTreeIds = lastAppliedSelectedTreeIds,
    )
    applySelection(selectedTreeIds = selectedTreeIds, updateSequence = updateSequence, scrollToVisible = true)
  }

  private fun updateEmptyText() {
    val displayedState = displayedStateSnapshot()
    val currentProjectScope = currentProjectPathForScope() != null
    val message = when (threadViewState.mode) {
      AgentSessionThreadViewMode.ACTIVE -> when {
        displayedState.projects.isEmpty() && displayedState.lastUpdatedAt == null -> AgentSessionsBundle.message("toolwindow.loading")
        displayedState.projects.isEmpty() && currentProjectScope -> AgentSessionsBundle.message("toolwindow.empty.current.project")
        displayedState.projects.isEmpty() -> AgentSessionsBundle.message("toolwindow.empty.global")
        else -> ""
      }
      AgentSessionThreadViewMode.ARCHIVED -> when {
        archivedSessionsState.projects.isEmpty() && archivedSessionsState.lastUpdatedAt == null -> AgentSessionsBundle.message("toolwindow.loading.archived")
        displayedState.projects.isEmpty() && currentProjectScope -> AgentSessionsBundle.message("toolwindow.empty.archived.current.project")
        displayedState.projects.isEmpty() -> AgentSessionsBundle.message("toolwindow.empty.archived")
        else -> ""
      }
    }
    tree.emptyText.text = message
  }

  private fun currentProjectPathForScope(): String? {
    if (!isCurrentProjectScopeEnabled()) return null
    return currentProjectPathProvider()
  }

  private fun applyDefaultExpansion(
    model: SessionTreeModel,
    previousModel: SessionTreeModel,
    rootChanged: Boolean,
    previouslyExpandedTreeIds: Set<SessionTreeId>,
    selectedTreeIds: Collection<SessionTreeId>,
  ) {
    sessionTreeExpansionTargetsAfterModelSwap(
      model = model,
      previousModel = previousModel,
      rootChanged = rootChanged,
      previouslyExpandedTreeIds = previouslyExpandedTreeIds,
      selectedTreeIds = selectedTreeIds,
    ).forEach { treeId ->
      expandNode(treeId)
    }
  }

  private fun selectedTreeIds(): List<SessionTreeId> {
    val selectionPaths = tree.selectionPaths ?: return emptyList()
    return selectionPaths.mapNotNull { path ->
      path.lastPathComponent?.let(::extractSessionTreeId)
    }.distinct()
  }

  private fun expandedTreeIds(): Set<SessionTreeId> {
    return TreeUtil.collectExpandedObjects(tree) { path ->
      path.lastPathComponent?.let(::extractSessionTreeId)
    }.filterNotNull().toSet()
  }

  private fun applySelection(selectedTreeIds: List<SessionTreeId>, updateSequence: Long, scrollToVisible: Boolean) {
    selectNodes(selectedTreeIds, { treeUpdateSequence == updateSequence }, scrollToVisible) { appliedSelectedTreeIds ->
      if (treeUpdateSequence == updateSequence) {
        treeSelectionInitialized = true
        lastAppliedSelectedTreeIds = appliedSelectedTreeIds
      }
    }
  }
}

internal enum class SessionTreeRebuildReason {
  SESSION_STATE_CHANGED,
  CHAT_TAB_SELECTION_CHANGED,
  OPEN_CHAT_TABS_PRESENTATION_CHANGED,
  THREAD_VIEW_CHANGED,
  PROJECT_SCOPE_CHANGED,
}

internal fun SessionTreeModelDiff.isEmpty(): Boolean {
  return !rootChanged && structureChangedIds.isEmpty() && contentChangedIds.isEmpty()
}

internal fun SessionTreeModelDiff.hasOnlyContentChanges(): Boolean {
  return !rootChanged && structureChangedIds.isEmpty() && contentChangedIds.isNotEmpty()
}

internal fun coalesceSessionTreeRebuildReason(
  current: SessionTreeRebuildReason?,
  next: SessionTreeRebuildReason,
): SessionTreeRebuildReason {
  if (current == null) return next
  if (current == SessionTreeRebuildReason.CHAT_TAB_SELECTION_CHANGED ||
      next == SessionTreeRebuildReason.CHAT_TAB_SELECTION_CHANGED) {
    return SessionTreeRebuildReason.CHAT_TAB_SELECTION_CHANGED
  }
  if (current == SessionTreeRebuildReason.THREAD_VIEW_CHANGED ||
      next == SessionTreeRebuildReason.THREAD_VIEW_CHANGED) {
    return SessionTreeRebuildReason.THREAD_VIEW_CHANGED
  }
  if (current == SessionTreeRebuildReason.PROJECT_SCOPE_CHANGED ||
      next == SessionTreeRebuildReason.PROJECT_SCOPE_CHANGED) {
    return SessionTreeRebuildReason.PROJECT_SCOPE_CHANGED
  }
  if (current == SessionTreeRebuildReason.OPEN_CHAT_TABS_PRESENTATION_CHANGED ||
      next == SessionTreeRebuildReason.OPEN_CHAT_TABS_PRESENTATION_CHANGED) {
    return SessionTreeRebuildReason.OPEN_CHAT_TABS_PRESENTATION_CHANGED
  }
  return SessionTreeRebuildReason.SESSION_STATE_CHANGED
}

internal fun shouldApplySelectionAfterModelSwap(
  treeModelDiff: SessionTreeModelDiff,
  selectedTreeIds: List<SessionTreeId>,
  selectedTreeIdsBeforeModelSwap: List<SessionTreeId>,
  treeSelectionInitialized: Boolean,
  lastAppliedSelectedTreeIds: List<SessionTreeId>,
): Boolean {
  if (!treeSelectionInitialized) return true
  if (selectedTreeIds != lastAppliedSelectedTreeIds) return true
  if (selectedTreeIds != selectedTreeIdsBeforeModelSwap) return true
  return !treeModelDiff.hasOnlyContentChanges() && !treeModelDiff.isEmpty()
}

internal fun sessionTreeSelectionTargetsAfterModelSwap(
  model: SessionTreeModel,
  reason: SessionTreeRebuildReason,
  previouslySelectedTreeIds: List<SessionTreeId>,
  selectedChatTreeId: SessionTreeId?,
  selectionInitialized: Boolean = true,
  lastAppliedSelectedTreeIds: List<SessionTreeId> = previouslySelectedTreeIds,
): List<SessionTreeId> {
  val preservedSelection = previouslySelectedTreeIds.filter { treeId -> treeId in model.entriesById }
  val activeChatSelection = selectedChatTreeId?.takeIf { treeId -> treeId in model.entriesById }?.let(::listOf).orEmpty()
  val previouslyAppliedSelectionCleared =
    selectionInitialized && previouslySelectedTreeIds.isEmpty() && lastAppliedSelectedTreeIds.isNotEmpty()
  return when (reason) {
    SessionTreeRebuildReason.SESSION_STATE_CHANGED,
    SessionTreeRebuildReason.OPEN_CHAT_TABS_PRESENTATION_CHANGED,
    SessionTreeRebuildReason.THREAD_VIEW_CHANGED,
    SessionTreeRebuildReason.PROJECT_SCOPE_CHANGED,
      -> {
      when {
        preservedSelection.isNotEmpty() -> preservedSelection
        previouslyAppliedSelectionCleared -> emptyList()
        !selectionInitialized ||
        lastAppliedSelectedTreeIds.isEmpty() ||
        previouslySelectedTreeIds.isNotEmpty() -> activeChatSelection
        else -> emptyList()
      }
    }
    SessionTreeRebuildReason.CHAT_TAB_SELECTION_CHANGED -> {
      activeChatSelection.ifEmpty { preservedSelection }
    }
  }
}

internal fun sessionTreeExpansionTargetsAfterModelSwap(
  model: SessionTreeModel,
  previousModel: SessionTreeModel,
  rootChanged: Boolean,
  previouslyExpandedTreeIds: Set<SessionTreeId>,
  selectedTreeIds: Collection<SessionTreeId>,
): List<SessionTreeId> {
  val result = LinkedHashSet<SessionTreeId>()
  previouslyExpandedTreeIds.forEach { treeId ->
    if (treeId in model.entriesById) {
      result += treeId
    }
  }

  if (SessionTreeId.Pinned in model.entriesById && SessionTreeId.Pinned !in previousModel.entriesById) {
    result += SessionTreeId.Pinned
  }

  val previousRootProjects = previousModel.rootIds.filterIsInstance<SessionTreeId.Project>().toSet()
  model.autoOpenProjects.forEach { projectId ->
    if ((!rootChanged || projectId !in previousRootProjects) && projectId in model.entriesById) {
      result += projectId
    }
  }

  selectedTreeIds.forEach { treeId ->
    val parentChain = mutableListOf<SessionTreeId>()
    var parentId = model.entriesById[treeId]?.parentId
    while (parentId != null) {
      parentChain += parentId
      parentId = model.entriesById[parentId]?.parentId
    }
    result += parentChain.asReversed()
  }

  return result.toList()
}
