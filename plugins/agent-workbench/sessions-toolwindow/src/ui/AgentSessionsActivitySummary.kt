// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.openapi.util.NlsSafe

internal const val MAX_IDLE_POPUP_ROWS: Int = 5

internal data class AgentSessionsActivityThreadRow(
  @JvmField val path: String,
  @JvmField val projectName: @NlsSafe String,
  @JvmField val worktreeName: @NlsSafe String?,
  @JvmField val thread: AgentSessionThread,
) {
  val locationLabel: @NlsSafe String
    get() = worktreeName?.let { "$projectName / $it" } ?: projectName
}

internal enum class AgentSessionsActivityBucket {
  ATTENTION,
  RUNNING,
  DONE,
  IDLE,
}

internal data class AgentSessionsActivitySummary(
  @JvmField val attentionRows: List<AgentSessionsActivityThreadRow>,
  @JvmField val runningRows: List<AgentSessionsActivityThreadRow>,
  @JvmField val doneRows: List<AgentSessionsActivityThreadRow>,
  @JvmField val idleRows: List<AgentSessionsActivityThreadRow>,
) {
  fun rowsFor(bucket: AgentSessionsActivityBucket): List<AgentSessionsActivityThreadRow> {
    return when (bucket) {
      AgentSessionsActivityBucket.ATTENTION -> attentionRows
      AgentSessionsActivityBucket.RUNNING -> runningRows
      AgentSessionsActivityBucket.DONE -> doneRows
      AgentSessionsActivityBucket.IDLE -> idleRows
    }
  }

  companion object {
    val EMPTY: AgentSessionsActivitySummary = AgentSessionsActivitySummary(
      attentionRows = emptyList(),
      runningRows = emptyList(),
      doneRows = emptyList(),
      idleRows = emptyList(),
    )
  }
}

internal fun buildAgentSessionsActivitySummary(
  state: AgentSessionsState,
  maxIdleRows: Int = MAX_IDLE_POPUP_ROWS,
): AgentSessionsActivitySummary {
  val rows = collectAgentSessionsActivityThreadRows(state)
  return AgentSessionsActivitySummary(
    attentionRows = rows.filter { it.thread.activity.isAttentionActivity() }.sortedByRecentActivity(),
    runningRows = rows.filter { it.thread.activity == AgentThreadActivity.PROCESSING }.sortedByRecentActivity(),
    doneRows = rows.filter { it.thread.activity == AgentThreadActivity.UNREAD }.sortedByRecentActivity(),
    idleRows = rows.filter { it.thread.activity == AgentThreadActivity.READY }.sortedByRecentActivity().take(maxIdleRows.coerceAtLeast(0)),
  )
}

private fun collectAgentSessionsActivityThreadRows(state: AgentSessionsState): List<AgentSessionsActivityThreadRow> {
  return buildList {
    state.projects.forEach { project ->
      addProjectThreadRows(project)
      project.worktrees.forEach { worktree ->
        addWorktreeThreadRows(project, worktree)
      }
    }
  }
}

private fun MutableList<AgentSessionsActivityThreadRow>.addProjectThreadRows(project: AgentProjectSessions) {
  project.threads.forEach { thread ->
    if (!isAgentSessionNewSessionId(thread.id)) {
      add(
        AgentSessionsActivityThreadRow(
          path = project.path,
          projectName = project.name,
          worktreeName = null,
          thread = thread,
        )
      )
    }
  }
}

private fun MutableList<AgentSessionsActivityThreadRow>.addWorktreeThreadRows(project: AgentProjectSessions, worktree: AgentWorktree) {
  worktree.threads.forEach { thread ->
    if (!isAgentSessionNewSessionId(thread.id)) {
      add(
        AgentSessionsActivityThreadRow(
          path = worktree.path,
          projectName = project.name,
          worktreeName = worktree.name,
          thread = thread,
        )
      )
    }
  }
}

private fun List<AgentSessionsActivityThreadRow>.sortedByRecentActivity(): List<AgentSessionsActivityThreadRow> {
  return sortedWith(
    compareByDescending<AgentSessionsActivityThreadRow> { it.thread.updatedAt }
      .thenBy { it.projectName }
      .thenBy { it.worktreeName ?: "" }
      .thenBy { it.thread.id }
  )
}

private fun AgentThreadActivity.isAttentionActivity(): Boolean {
  return this == AgentThreadActivity.NEEDS_INPUT || this == AgentThreadActivity.REVIEWING
}
