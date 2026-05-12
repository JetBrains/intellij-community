// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.tree.threadDisplayTitle
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SystemNotifications

private const val AGENT_SESSIONS_SYSTEM_NOTIFICATION_NAME: String = "Agent Workbench Sessions"

internal data class AgentSessionsSystemNotification(
  @JvmField val bucket: AgentSessionsActivityBucket,
  @JvmField val title: @NlsContexts.SystemNotificationTitle String,
  @JvmField val text: @NlsContexts.SystemNotificationText String,
)

internal class AgentSessionsSystemNotificationTracker {
  private var hasLoadedBaseline: Boolean = false
  private var bucketsByThreadKey: Map<AgentSessionsSystemNotificationThreadKey, AgentSessionsActivityBucket> = emptyMap()

  fun collectNotifications(
    summary: AgentSessionsActivitySummary,
    isLoadedState: Boolean,
  ): List<AgentSessionsSystemNotification> {
    val nextRows = summary.systemNotificationRows()
    val nextBucketsByThreadKey = nextRows.associate { bucketedRow -> bucketedRow.threadKey to bucketedRow.bucket }
    if (!hasLoadedBaseline) {
      if (isLoadedState) {
        hasLoadedBaseline = true
        bucketsByThreadKey = nextBucketsByThreadKey
      }
      return emptyList()
    }

    val notifications = nextRows.mapNotNull { bucketedRow ->
      if (!bucketedRow.bucket.isSystemNotificationBucket()) {
        return@mapNotNull null
      }
      if (bucketsByThreadKey[bucketedRow.threadKey] == bucketedRow.bucket) {
        return@mapNotNull null
      }
      bucketedRow.toSystemNotification()
    }
    bucketsByThreadKey = nextBucketsByThreadKey
    return notifications
  }
}

internal fun showAgentSessionsSystemNotification(notification: AgentSessionsSystemNotification) {
  SystemNotifications.getInstance().notify(
    AGENT_SESSIONS_SYSTEM_NOTIFICATION_NAME,
    notification.title,
    notification.text,
  )
}

private data class AgentSessionsSystemNotificationThreadKey(
  @JvmField val path: String,
  @JvmField val provider: String,
  @JvmField val threadId: String,
)

private data class AgentSessionsSystemNotificationBucketedRow(
  @JvmField val bucket: AgentSessionsActivityBucket,
  @JvmField val row: AgentSessionsActivityThreadRow,
) {
  val threadKey: AgentSessionsSystemNotificationThreadKey = AgentSessionsSystemNotificationThreadKey(
    path = row.path,
    provider = row.thread.provider.value,
    threadId = row.thread.id,
  )
}

private fun AgentSessionsActivitySummary.systemNotificationRows(): List<AgentSessionsSystemNotificationBucketedRow> {
  return buildList {
    attentionRows.forEach { row -> add(AgentSessionsSystemNotificationBucketedRow(AgentSessionsActivityBucket.ATTENTION, row)) }
    runningRows.forEach { row -> add(AgentSessionsSystemNotificationBucketedRow(AgentSessionsActivityBucket.RUNNING, row)) }
    doneRows.forEach { row -> add(AgentSessionsSystemNotificationBucketedRow(AgentSessionsActivityBucket.DONE, row)) }
  }
}

private fun AgentSessionsActivityBucket.isSystemNotificationBucket(): Boolean {
  return this == AgentSessionsActivityBucket.ATTENTION || this == AgentSessionsActivityBucket.DONE
}

private fun AgentSessionsSystemNotificationBucketedRow.toSystemNotification(): AgentSessionsSystemNotification {
  val displayTitle = threadDisplayTitle(threadId = row.thread.id, title = row.thread.title)
  return when (bucket) {
    AgentSessionsActivityBucket.ATTENTION -> AgentSessionsSystemNotification(
      bucket = bucket,
      title = AgentSessionsBundle.message("toolwindow.system.notification.attention.title"),
      text = AgentSessionsBundle.message("toolwindow.system.notification.attention.text", displayTitle, row.locationLabel),
    )

    AgentSessionsActivityBucket.DONE -> AgentSessionsSystemNotification(
      bucket = bucket,
      title = AgentSessionsBundle.message("toolwindow.system.notification.done.title"),
      text = AgentSessionsBundle.message("toolwindow.system.notification.done.text", displayTitle, row.locationLabel),
    )

    AgentSessionsActivityBucket.RUNNING -> error("Running threads do not create system notifications")
  }
}
