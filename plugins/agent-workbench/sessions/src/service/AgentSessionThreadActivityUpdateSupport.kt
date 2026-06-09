// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate

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
