// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.tree.formatRelativeTimeShort
import com.intellij.agent.workbench.sessions.tree.threadDisplayTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.FontMetrics

internal const val SESSION_TREE_THREAD_PROVIDER_ICON_SIZE = 12
private val SESSION_TREE_TIME_LABEL_SAMPLES = listOf("59m", "23h", "7d", "4w", "11mo", "9y")

internal const val SESSION_TREE_MORE_ROW_FRAGMENT_TAG = "agent.sessions.tree.more.row"

internal data class SessionTreeThreadRowPresentation(
  @JvmField val statusColor: Color,
  @JvmField val title: @NlsSafe String,
  @JvmField val timeLabel: @NlsSafe String,
  @JvmField val statusLabel: @NlsSafe String,
  @JvmField val branchMismatchMessage: @NlsSafe String?,
  val accessibleStatusText: @NlsSafe String?,
)

internal fun buildSessionTreeThreadRowPresentation(
  treeNode: SessionTreeNode.Thread,
  now: Long,
): SessionTreeThreadRowPresentation {
  val activityColor = JBColor(Color(treeNode.thread.activity.argb, true), Color(treeNode.thread.activity.argb, true))
  val timeLabel = treeNode.thread.updatedAt.takeIf { it > 0 }?.let { timestamp ->
    formatRelativeTimeShort(timestamp, now)
  } ?: AgentSessionsBundle.message("toolwindow.time.unknown")
  val statusLabel = threadActivityDisplayName(treeNode.thread.activity)
  val originBranch = treeNode.thread.originBranch
  val parentBranch = treeNode.parentWorktreeBranch
  val branchMismatchMessage = if (originBranch != null && parentBranch != null && originBranch != parentBranch) {
    AgentSessionsBundle.message("toolwindow.thread.branch.mismatch", originBranch)
  }
  else {
    null
  }
  val providerName = providerDisplayName(treeNode.thread.provider)
  val accessibleStatusText = buildList {
    add(providerName)
    if (treeNode.thread.activity != AgentThreadActivity.READY) {
      add(statusLabel)
    }
    add(timeLabel)
  }.joinToString(separator = ", ")
  return SessionTreeThreadRowPresentation(
    statusColor = activityColor,
    title = threadDisplayTitle(treeNode.thread),
    timeLabel = timeLabel,
    statusLabel = statusLabel,
    branchMismatchMessage = branchMismatchMessage,
    accessibleStatusText = accessibleStatusText,
  )
}

internal fun buildSessionTreeThreadTooltipHtml(
  treeNode: SessionTreeNode.Thread,
  now: Long,
  maxWidthPx: Int? = null,
): @NlsSafe String {
  val presentation = buildSessionTreeThreadRowPresentation(treeNode = treeNode, now = now)
  val title = StringUtil.escapeXmlEntities(presentation.title)
  val updatedText = StringUtil.escapeXmlEntities(AgentSessionsBundle.message("toolwindow.updated", presentation.timeLabel))
  val statusText = StringUtil.escapeXmlEntities(AgentSessionsBundle.message("toolwindow.thread.status", presentation.statusLabel))

  val escapedWidth = maxWidthPx?.coerceAtLeast(1)
  val bodyWidthStyle = escapedWidth?.let { " style='width:${it}px;'" } ?: ""
  val bodyOpen = "<body$bodyWidthStyle>"
  val bodyClose = "</body>"

  return "<html>$bodyOpen$title<br>$updatedText<br>$statusText$bodyClose</html>"
}

private fun threadActivityDisplayName(activity: AgentThreadActivity): @NlsSafe String {
  return when (activity) {
    AgentThreadActivity.READY -> AgentSessionsBundle.message("toolwindow.thread.status.ready")
    AgentThreadActivity.PROCESSING -> AgentSessionsBundle.message("toolwindow.thread.status.in.progress")
    AgentThreadActivity.REVIEWING -> AgentSessionsBundle.message("toolwindow.thread.status.needs.review")
    AgentThreadActivity.UNREAD -> AgentSessionsBundle.message("toolwindow.thread.status.needs.input")
  }
}

internal fun computeSessionTreeSharedTimeColumnWidth(fontMetrics: FontMetrics): Int {
  val nowLabel = AgentSessionsBundle.message("toolwindow.time.now")
  val unknownLabel = AgentSessionsBundle.message("toolwindow.time.unknown")
  return (SESSION_TREE_TIME_LABEL_SAMPLES + nowLabel + unknownLabel).maxOf(fontMetrics::stringWidth)
}
