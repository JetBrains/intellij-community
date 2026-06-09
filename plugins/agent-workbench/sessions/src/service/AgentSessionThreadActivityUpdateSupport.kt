// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate

internal data class ResolvedAgentSessionThreadActivityUpdate(
  @JvmField val activityReport: AgentThreadActivityReport,
  @JvmField val updatedAt: Long,
)

internal fun resolveAgentSessionThreadActivityUpdate(
  thread: AgentSessionThread,
  activityUpdate: AgentSessionThreadActivityUpdate,
): ResolvedAgentSessionThreadActivityUpdate {
  val hintedActivity = activityUpdate.rowActivity ?: thread.activity
  val hintedSummaryActivity = when {
    activityUpdate.hasChromeActivity -> activityUpdate.chromeActivity
    thread.summaryActivity == null -> null
    activityUpdate.rowActivity != null -> hintedActivity
    else -> thread.summaryActivity
  }
  return ResolvedAgentSessionThreadActivityUpdate(
    activityReport = AgentThreadActivityReport(rowActivity = hintedActivity, chromeActivity = hintedSummaryActivity),
    updatedAt = activityUpdate.updatedAt?.let { updatedAt -> maxOf(thread.updatedAt, updatedAt) } ?: thread.updatedAt,
  )
}
