// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.ui

import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.service.AgentSessionsService
import com.intellij.agent.workbench.sessions.state.AgentSessionsTreeUiStateService
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.tree.SessionTreeModelDiff
import com.intellij.agent.workbench.sessions.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.tree.diffSessionTreeModels
import com.intellij.agent.workbench.sessions.tree.parentNodesForSelection
import com.intellij.agent.workbench.sessions.tree.resolveSelectedSessionTreeId
import com.intellij.openapi.application.EDT
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities

internal class AgentSessionsTreeStateController(
  private val service: AgentSessionsService,
  private val chatSelectionService: AgentChatTabSelectionService,
  private val treeUiStateService: AgentSessionsTreeUiStateService,
  private val tree: Tree,
  private val getSessionTreeModel: () -> SessionTreeModel,
  private val setSessionTreeModel: (SessionTreeModel) -> Unit,
  private val onLastUsedProviderChanged: (AgentSessionProvider?) -> Unit,
  private val onBeforeModelSwap: () -> Unit,
  private val invalidateTreeModel: (SessionTreeModelDiff) -> CompletableFuture<*>,
  private val expandNode: (SessionTreeId) -> Unit,
  private val selectNode: (SessionTreeId) -> Unit,
) {
  @Suppress("RAW_SCOPE_CREATION")
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)

  private var sessionsState: AgentSessionsState = AgentSessionsState()
  private var selectedChatTab: AgentChatTabSelection? = null
  private var treeUpdateSequence: Long = 0
  private var rebuildJob: Job? = null

  fun start() {
    scope.launch {
      service.state.collect { newState ->
        sessionsState = newState
        rebuildTree()
      }
    }

    scope.launch {
      chatSelectionService.selectedChatTab.collect { selection ->
        selectedChatTab = selection
        rebuildTree()
      }
    }

    scope.launch {
      treeUiStateService.lastUsedProviderFlow.collect { provider ->
        onLastUsedProviderChanged(provider)
      }
    }
  }

  fun dispose() {
    rebuildJob?.cancel()
    scope.cancel("Agent sessions tree state controller disposed")
  }

  private fun rebuildTree() {
    rebuildJob?.cancel()
    val snapshotState = sessionsState
    val snapshotSelectedChatTab = selectedChatTab
    val oldModel = getSessionTreeModel()
    val updateSequence = ++treeUpdateSequence
    rebuildJob = scope.launch {
      val (nextModel, treeModelDiff, selectedTreeId) = withContext(Default) {
        val model = buildSessionTreeModel(
          projects = snapshotState.projects,
          visibleClosedProjectCount = snapshotState.visibleClosedProjectCount,
          visibleThreadCounts = snapshotState.visibleThreadCounts,
          treeUiState = treeUiStateService,
        )
        val diff = diffSessionTreeModels(oldModel, model)
        val selection = resolveSelectedSessionTreeId(snapshotState.projects, snapshotSelectedChatTab)
        Triple(model, diff, selection)
      }
      if (treeUpdateSequence != updateSequence) return@launch

      onBeforeModelSwap()
      setSessionTreeModel(nextModel)
      updateEmptyText()

      invalidateTreeModel(treeModelDiff).thenRun {
        SwingUtilities.invokeLater {
          if (treeUpdateSequence != updateSequence) return@invokeLater
          applyDefaultExpansion(nextModel, selectedTreeId)
          applySelection(nextModel, selectedTreeId)
        }
      }
    }
  }

  private fun updateEmptyText() {
    val message = when {
      sessionsState.projects.isEmpty() && sessionsState.lastUpdatedAt == null -> AgentSessionsBundle.message("toolwindow.loading")
      sessionsState.projects.isEmpty() -> AgentSessionsBundle.message("toolwindow.empty.global")
      else -> ""
    }
    tree.emptyText.text = message
  }

  private fun applyDefaultExpansion(model: SessionTreeModel, selectedTreeId: SessionTreeId?) {
    val expandedProjects = TreeUtil.collectExpandedObjects(tree) { path ->
      path.lastPathComponent?.let(::extractSessionTreeId) as? SessionTreeId.Project
    }.toSet()

    model.autoOpenProjects.forEach { projectId ->
      if (projectId !in expandedProjects) {
        expandNode(projectId)
      }
    }

    selectedTreeId?.let { treeId ->
      parentNodesForSelection(treeId).forEach { parentId ->
        if (parentId in model.entriesById) {
          expandNode(parentId)
        }
      }
    }
  }

  private fun applySelection(model: SessionTreeModel, selectedTreeId: SessionTreeId?) {
    if (selectedTreeId == null || selectedTreeId !in model.entriesById) {
      tree.clearSelection()
      return
    }
    selectNode(selectedTreeId)
  }
}
