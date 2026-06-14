// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.state.AgentSessionTreeUiStateService
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.buildSessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.sessionTreeNodeSearchText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.ui.treeStructure.Tree
import java.beans.PropertyChangeEvent

private const val MAX_DEFERRED_REFRESH_ATTEMPTS: Int = 16

internal fun installSessionTreeSpeedSearchReveal(
  tree: Tree,
  modelProvider: () -> SessionTreeModel,
  stateProvider: () -> AgentSessionsState,
  ensureProjectVisible: (String) -> Unit,
  ensureThreadVisible: (String, AgentSessionProvider, String) -> Unit,
) {
  val speedSearch = SpeedSearchSupply.getSupply(tree, true) ?: return
  if (speedSearch is SpeedSearchBase<*>) {
    speedSearch.comparator = SessionTreeStrictSubstringComparator()
  }
  speedSearch.addChangeListener { event ->
    revealHiddenMatches(
      event = event,
      modelProvider = modelProvider,
      stateProvider = stateProvider,
      ensureProjectVisible = ensureProjectVisible,
      ensureThreadVisible = ensureThreadVisible,
      speedSearch = speedSearch,
    )
  }
}

internal class SessionTreeStrictSubstringComparator : SpeedSearchComparator(false, false) {
  override fun matchingFragments(pattern: String, text: String): Iterable<TextRange>? {
    myRecentSearchText = pattern
    if (pattern.isEmpty()) return null
    var startIndex = 0
    while (startIndex <= text.length - pattern.length) {
      val index = text.indexOf(pattern, startIndex, ignoreCase = true)
      if (index < 0) return null
      if (isWordStart(text, index)) {
        return listOf(TextRange(index, index + pattern.length))
      }
      startIndex = index + 1
    }
    return null
  }

  private fun isWordStart(text: String, index: Int): Boolean {
    if (index == 0) return true
    val prev = text[index - 1]
    if (!prev.isLetterOrDigit()) return true
    val curr = text[index]
    return prev.isLowerCase() && curr.isUpperCase()
  }
}

private fun revealHiddenMatches(
  event: PropertyChangeEvent,
  modelProvider: () -> SessionTreeModel,
  stateProvider: () -> AgentSessionsState,
  ensureProjectVisible: (String) -> Unit,
  ensureThreadVisible: (String, AgentSessionProvider, String) -> Unit,
  speedSearch: SpeedSearchSupply,
) {
  val pattern = (event.newValue as? String)?.trim().orEmpty()
  if (pattern.isEmpty()) return

  val hiddenIds = collectHiddenMatchingIds(pattern, modelProvider, stateProvider)
  if (hiddenIds.isEmpty()) return

  hiddenIds.firstOrNull()?.let { id -> ensureVisible(ensureProjectVisible, ensureThreadVisible, id) }

  refreshSelectionWhenAvailable(speedSearch, pattern, attempt = 0)
}

private fun collectHiddenMatchingIds(
  pattern: String,
  modelProvider: () -> SessionTreeModel,
  stateProvider: () -> AgentSessionsState,
): Set<SessionTreeId> {
  val visibleIds = modelProvider().entriesById.keys
  val state = stateProvider()
  val fullModel = buildSessionTreeModel(
    projects = state.projects,
    visibleClosedProjectCount = Int.MAX_VALUE,
    visibleThreadCounts = fullVisibleThreadCounts(state.projects),
    treeUiState = service<AgentSessionTreeUiStateService>(),
  )
  val comparator = SessionTreeStrictSubstringComparator()
  val matches = LinkedHashSet<SessionTreeId>()
  fullModel.entriesById.forEach { (id, entry) ->
    if (id in visibleIds) return@forEach
    val text = sessionTreeNodeSearchText(entry.node)
    if (text.isNotEmpty() && comparator.matchingFragments(pattern, text) != null) {
      matches.add(id)
    }
  }
  return matches
}

private fun fullVisibleThreadCounts(projects: List<AgentProjectSessions>): Map<String, Int> {
  val counts = LinkedHashMap<String, Int>()
  projects.forEach { project ->
    counts[project.path] = project.threads.size
    project.worktrees.forEach { worktree ->
      counts[worktree.path] = worktree.threads.size
    }
  }
  return counts
}

private fun ensureVisible(
  ensureProjectVisible: (String) -> Unit,
  ensureThreadVisible: (String, AgentSessionProvider, String) -> Unit,
  id: SessionTreeId,
) {
  when (id) {
    is SessionTreeId.Project -> ensureProjectVisible(id.path)
    is SessionTreeId.Worktree -> ensureProjectVisible(id.projectPath)
    is SessionTreeId.Thread -> {
      ensureProjectVisible(id.projectPath)
      ensureThreadVisible(id.projectPath, id.provider, id.threadId)
    }
    is SessionTreeId.SubAgent -> {
      ensureProjectVisible(id.projectPath)
      ensureThreadVisible(id.projectPath, id.provider, id.threadId)
    }
    is SessionTreeId.WorktreeThread -> {
      ensureProjectVisible(id.projectPath)
      ensureThreadVisible(id.worktreePath, id.provider, id.threadId)
    }
    is SessionTreeId.WorktreeSubAgent -> {
      ensureProjectVisible(id.projectPath)
      ensureThreadVisible(id.worktreePath, id.provider, id.threadId)
    }
    is SessionTreeId.Warning,
    is SessionTreeId.Error,
    is SessionTreeId.Empty,
    is SessionTreeId.MoreProjects,
    is SessionTreeId.MoreThreads,
    is SessionTreeId.WorktreeWarning,
    is SessionTreeId.WorktreeMoreThreads,
    is SessionTreeId.WorktreeError,
      -> Unit
  }
}

private fun refreshSelectionWhenAvailable(
  speedSearch: SpeedSearchSupply,
  pattern: String,
  attempt: Int,
) {
  if (speedSearch.enteredPrefix?.trim() != pattern) return
  speedSearch.findAndSelectElement(pattern)
  if (attempt >= MAX_DEFERRED_REFRESH_ATTEMPTS) return
  ApplicationManager.getApplication().invokeLater {
    refreshSelectionWhenAvailable(speedSearch, pattern, attempt + 1)
  }
}
