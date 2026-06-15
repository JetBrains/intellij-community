// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityBucket
import com.intellij.agent.workbench.common.chromeBucket
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.statusColor
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentation
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.toolwindow.icons.AgentWorkbenchSessionsToolwindowIcons
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.IconManager
import java.awt.Color
import javax.swing.Icon

internal const val AGENT_SESSIONS_CHROME_ACTIVITY_FRESHNESS_MILLIS: Long = 3L * 24 * 60 * 60 * 1000

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
}

internal enum class AgentSessionsStripeBadge(
  private val activity: AgentThreadActivity,
) {
  ATTENTION(AgentThreadActivity.NEEDS_INPUT),
  DONE(AgentThreadActivity.UNREAD),
  ;

  fun color(): Color = requireNotNull(activity.statusColor()) {
    "Stripe badge activity $activity must define a status color"
  }
}

internal data class AgentSessionsActivitySummary(
  @JvmField val attentionRows: List<AgentSessionsActivityThreadRow>,
  @JvmField val runningRows: List<AgentSessionsActivityThreadRow>,
  @JvmField val doneRows: List<AgentSessionsActivityThreadRow>,
) {
  fun rowsFor(bucket: AgentSessionsActivityBucket): List<AgentSessionsActivityThreadRow> {
    return when (bucket) {
      AgentSessionsActivityBucket.ATTENTION -> attentionRows
      AgentSessionsActivityBucket.RUNNING -> runningRows
      AgentSessionsActivityBucket.DONE -> doneRows
    }
  }

  fun stripeBadge(): AgentSessionsStripeBadge? {
    return when {
      attentionRows.isNotEmpty() -> AgentSessionsStripeBadge.ATTENTION
      doneRows.isNotEmpty() -> AgentSessionsStripeBadge.DONE
      else -> null
    }
  }

  companion object {
    val EMPTY: AgentSessionsActivitySummary = AgentSessionsActivitySummary(
      attentionRows = emptyList(),
      runningRows = emptyList(),
      doneRows = emptyList(),
    )
  }
}

internal fun agentSessionsActivityIcon(summary: AgentSessionsActivitySummary): Icon {
  return agentSessionsActivityIcon(summary.stripeBadge())
}

internal fun agentSessionsActivityIcon(badge: AgentSessionsStripeBadge?): Icon {
  val emptyIcon = AgentWorkbenchSessionsToolwindowIcons.Alien
  return when (badge) {
    AgentSessionsStripeBadge.ATTENTION -> IconManager.getInstance().withIconBadge(emptyIcon, AgentSessionsStripeBadge.ATTENTION.color())
    AgentSessionsStripeBadge.DONE -> IconManager.getInstance().withIconBadge(emptyIcon, AgentSessionsStripeBadge.DONE.color())
    null -> emptyIcon
  }
}

internal fun buildAgentSessionsActivitySummary(
  state: AgentSessionsState,
  presentationsByKey: Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation> = emptyMap(),
): AgentSessionsActivitySummary {
  val rows = collectAgentSessionsActivityThreadRows(state, presentationsByKey)
  return AgentSessionsActivitySummary(
    attentionRows = rows.filter { it.thread.activityReport.chromeBucket() == AgentThreadActivityBucket.ATTENTION }.sortedByRecentActivity(),
    runningRows = rows.filter { it.thread.activityReport.chromeBucket() == AgentThreadActivityBucket.RUNNING }.sortedByRecentActivity(),
    doneRows = rows.filter { it.thread.activityReport.chromeBucket() == AgentThreadActivityBucket.DONE }.sortedByRecentActivity(),
  )
}

internal fun freshAgentSessionsActivitySummary(
  summary: AgentSessionsActivitySummary,
  nowMillis: Long,
  freshnessMillis: Long = AGENT_SESSIONS_CHROME_ACTIVITY_FRESHNESS_MILLIS,
): AgentSessionsActivitySummary {
  return AgentSessionsActivitySummary(
    attentionRows = summary.attentionRows.filterFreshActivityRows(nowMillis, freshnessMillis),
    runningRows = summary.runningRows.filterFreshActivityRows(nowMillis, freshnessMillis),
    doneRows = summary.doneRows.filterFreshActivityRows(nowMillis, freshnessMillis),
  )
}

private fun collectAgentSessionsActivityThreadRows(
  state: AgentSessionsState,
  presentationsByKey: Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>,
): List<AgentSessionsActivityThreadRow> {
  return buildList {
    state.projects.forEach { project ->
      addProjectThreadRows(project, presentationsByKey)
      project.worktrees.forEach { worktree ->
        addWorktreeThreadRows(project, worktree, presentationsByKey)
      }
    }
  }
}

private fun MutableList<AgentSessionsActivityThreadRow>.addProjectThreadRows(
  project: AgentProjectSessions,
  presentationsByKey: Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>,
) {
  project.threads.forEach { thread ->
    if (!isAgentSessionNewSessionId(thread.id)) {
      add(
        AgentSessionsActivityThreadRow(
          path = project.path,
          projectName = project.name,
          worktreeName = null,
          thread = thread.overlayPresentation(path = project.path, presentationsByKey = presentationsByKey),
        )
      )
    }
  }
}

private fun MutableList<AgentSessionsActivityThreadRow>.addWorktreeThreadRows(
  project: AgentProjectSessions,
  worktree: AgentWorktree,
  presentationsByKey: Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>,
) {
  worktree.threads.forEach { thread ->
    if (!isAgentSessionNewSessionId(thread.id)) {
      add(
        AgentSessionsActivityThreadRow(
          path = worktree.path,
          projectName = project.name,
          worktreeName = worktree.name,
          thread = thread.overlayPresentation(path = worktree.path, presentationsByKey = presentationsByKey),
        )
      )
    }
  }
}

private fun AgentSessionThread.overlayPresentation(
  path: String,
  presentationsByKey: Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>,
): AgentSessionThread {
  val key = AgentSessionThreadPresentationKey.create(projectPath = path, provider = provider, threadId = id) ?: return this
  val presentation = presentationsByKey[key] ?: return this
  return copy(
    title = presentation.title.takeIf { it.isNotBlank() } ?: title,
    updatedAt = presentation.updatedAt ?: updatedAt,
    activityReport = presentation.activityReport,
  )
}

private fun List<AgentSessionsActivityThreadRow>.sortedByRecentActivity(): List<AgentSessionsActivityThreadRow> {
  return sortedWith(
    compareByDescending<AgentSessionsActivityThreadRow> { it.thread.updatedAt }
      .thenBy { it.projectName }
      .thenBy { it.worktreeName ?: "" }
      .thenBy { it.thread.id }
  )
}

private fun List<AgentSessionsActivityThreadRow>.filterFreshActivityRows(
  nowMillis: Long,
  freshnessMillis: Long,
): List<AgentSessionsActivityThreadRow> {
  return filter { row -> row.thread.updatedAt >= nowMillis - freshnessMillis }
}
