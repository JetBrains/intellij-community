// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec plugins/ij-air/spec/sessions/agent-sessions-tree.spec.md

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityBucket
import com.intellij.platform.ai.agent.core.chromeBucket
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.common.statusColor
import com.intellij.platform.ai.agent.common.withAgentThreadActivityBadge
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentation
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.toolwindow.icons.AgentWorkbenchSessionsToolwindowIcons
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.openapi.util.NlsSafe
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
  @JvmField val activity: AgentThreadActivity,
) {
  ATTENTION(AgentThreadActivity.NEEDS_INPUT),
  RUNNING(AgentThreadActivity.PROCESSING),
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
      runningRows.isNotEmpty() -> AgentSessionsStripeBadge.RUNNING
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
  return badge?.let { withAgentThreadActivityBadge(emptyIcon, it.activity) } ?: emptyIcon
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
  // Activity summary buckets use the persisted thread activity; shared presentation only refreshes row titles here.
  return copy(
    title = presentation.title.takeIf { it.isNotBlank() } ?: title,
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
