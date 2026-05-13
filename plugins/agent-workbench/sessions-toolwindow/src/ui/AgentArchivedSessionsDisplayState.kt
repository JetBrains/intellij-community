// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentArchivedSessionsState
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionArchivedRangePreset
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal fun buildArchivedDisplayState(
  archivedState: AgentArchivedSessionsState,
  rangePreset: AgentSessionArchivedRangePreset,
  nowMs: Long,
  zoneId: ZoneId = ZoneId.systemDefault(),
): AgentSessionsState {
  val matchesRange = archivedThreadRangePredicate(rangePreset, nowMs, zoneId)
  return AgentSessionsState(
    projects = archivedState.projects.mapNotNull { project -> project.filteredForArchivedRange(matchesRange) },
    lastUpdatedAt = archivedState.lastUpdatedAt,
    visibleClosedProjectCount = archivedState.visibleClosedProjectCount,
    visibleThreadCounts = archivedState.visibleThreadCounts,
  )
}

private fun AgentProjectSessions.filteredForArchivedRange(
  matchesRange: (AgentSessionThread) -> Boolean,
): AgentProjectSessions? {
  val filteredThreads = threads.filter(matchesRange)
  val filteredWorktrees = worktrees.mapNotNull { worktree -> worktree.filteredForArchivedRange(matchesRange) }
  val hasProjectContent = filteredThreads.isNotEmpty() || isLoading || errorMessage != null || providerWarnings.isNotEmpty()
  if (!hasProjectContent && filteredWorktrees.isEmpty()) {
    return null
  }
  return copy(
    threads = filteredThreads,
    worktrees = filteredWorktrees,
  )
}

private fun AgentWorktree.filteredForArchivedRange(
  matchesRange: (AgentSessionThread) -> Boolean,
): AgentWorktree? {
  val filteredThreads = threads.filter(matchesRange)
  if (filteredThreads.isEmpty() && !isLoading && errorMessage == null && providerWarnings.isEmpty()) {
    return null
  }
  return copy(threads = filteredThreads)
}

private fun archivedThreadRangePredicate(
  rangePreset: AgentSessionArchivedRangePreset,
  nowMs: Long,
  zoneId: ZoneId,
): (AgentSessionThread) -> Boolean {
  val cutoffMs = when (rangePreset) {
    AgentSessionArchivedRangePreset.ALL -> return { true }
    AgentSessionArchivedRangePreset.TODAY -> {
      Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
    AgentSessionArchivedRangePreset.LAST_7_DAYS -> nowMs - Duration.ofDays(7).toMillis()
    AgentSessionArchivedRangePreset.LAST_30_DAYS -> nowMs - Duration.ofDays(30).toMillis()
  }
  return { thread -> thread.updatedAt > 0L && thread.updatedAt >= cutoffMs }
}
