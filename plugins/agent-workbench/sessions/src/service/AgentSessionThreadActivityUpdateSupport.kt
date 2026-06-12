// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.normalizeAgentSessionTitle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadPresentationUpdate
import com.intellij.agent.workbench.sessions.core.providers.mergeAgentSessionThreadPresentationUpdates
import com.intellij.agent.workbench.sessions.core.providers.toPresentationUpdate

internal data class ResolvedAgentThreadActivityReportUpdate(
  @JvmField val activityReport: AgentThreadActivityReport,
  @JvmField val updatedAt: Long,
)

internal fun resolveAgentThreadActivityReportUpdate(
  thread: AgentSessionThread,
  activityUpdate: AgentSessionThreadActivityUpdate,
): ResolvedAgentThreadActivityReportUpdate {
  val updateTimestamp = activityUpdate.updatedAt
  if (updateTimestamp != null && updateTimestamp < thread.updatedAt) {
    return ResolvedAgentThreadActivityReportUpdate(
      activityReport = thread.activityReport,
      updatedAt = thread.updatedAt,
    )
  }
  return ResolvedAgentThreadActivityReportUpdate(
    activityReport = if (activityUpdate.updatesChromeActivity) {
      activityUpdate.activityReport
    }
    else {
      AgentThreadActivityReport(
        rowActivity = activityUpdate.activityReport.rowActivity,
        chromeActivity = thread.activityReport.chromeActivity,
      )
    },
    updatedAt = updateTimestamp?.let { updatedAt -> maxOf(thread.updatedAt, updatedAt) } ?: thread.updatedAt,
  )
}

internal data class ResolvedAgentThreadPresentationUpdate(
  @JvmField val title: String,
  @JvmField val activityReport: AgentThreadActivityReport,
  @JvmField val updatedAt: Long,
)

internal fun resolveAgentThreadPresentationUpdate(
  thread: AgentSessionThread,
  presentationUpdate: AgentSessionThreadPresentationUpdate,
): ResolvedAgentThreadPresentationUpdate {
  val resolvedActivityUpdate = presentationUpdate.activityReport?.let { activityReport ->
    resolveAgentThreadActivityReportUpdate(
      thread = thread,
      activityUpdate = AgentSessionThreadActivityUpdate(
        activityReport = activityReport,
        updatesChromeActivity = presentationUpdate.updatesChromeActivity,
        updatedAt = presentationUpdate.updatedAt,
      ),
    )
  }
  return ResolvedAgentThreadPresentationUpdate(
    title = normalizeAgentSessionTitle(presentationUpdate.title) ?: thread.title,
    activityReport = resolvedActivityUpdate?.activityReport ?: thread.activityReport,
    updatedAt = maxOf(thread.updatedAt, presentationUpdate.updatedAt ?: thread.updatedAt, resolvedActivityUpdate?.updatedAt ?: thread.updatedAt),
  )
}

internal fun AgentSessionRefreshHints.resolvePresentationUpdatesByThreadId(): Map<String, AgentSessionThreadPresentationUpdate> {
  if (activityUpdatesByThreadId.isEmpty()) {
    return presentationUpdatesByThreadId
  }
  val activityPresentationUpdates = activityUpdatesByThreadId.mapValues { (_, update) -> update.toPresentationUpdate() }
  if (presentationUpdatesByThreadId.isEmpty()) {
    return activityPresentationUpdates
  }
  val merged = LinkedHashMap<String, AgentSessionThreadPresentationUpdate>(activityPresentationUpdates.size + presentationUpdatesByThreadId.size)
  val threadIds = LinkedHashSet<String>(activityPresentationUpdates.size + presentationUpdatesByThreadId.size)
  threadIds.addAll(activityPresentationUpdates.keys)
  threadIds.addAll(presentationUpdatesByThreadId.keys)
  for (threadId in threadIds) {
    val activityPresentationUpdate = activityPresentationUpdates[threadId]
    val presentationUpdate = presentationUpdatesByThreadId[threadId]
    merged[threadId] = when {
      activityPresentationUpdate == null -> checkNotNull(presentationUpdate)
      presentationUpdate == null -> activityPresentationUpdate
      else -> mergeAgentSessionThreadPresentationUpdates(activityPresentationUpdate, presentationUpdate)
    }
  }
  return merged
}
