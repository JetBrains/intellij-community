// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.common.statusColor
import com.intellij.agent.workbench.common.statusMessageKey
import com.intellij.agent.workbench.sessions.AgentSessionCostPresentationSettings
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.formatRelativeTimeShort
import com.intellij.agent.workbench.sessions.tree.threadDisplayTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import java.awt.Color
import java.awt.FontMetrics
import java.math.RoundingMode

private val SESSION_TREE_TIME_LABEL_SAMPLES = listOf("59m", "23h", "7d", "4w", "11mo", "9y")

internal const val SESSION_TREE_MORE_ROW_FRAGMENT_TAG = "agent.sessions.tree.more.row"

internal data class SessionTreeThreadRowPresentation(
  @JvmField val statusColor: Color?,
  @JvmField val title: @NlsSafe String,
  @JvmField val timeLabel: @NlsSafe String,
  @JvmField val statusLabel: @NlsSafe String,
  @JvmField val costLabel: @NlsSafe String?,
  @JvmField val branchMismatchMessage: @NlsSafe String?,
  @JvmField val accessibleStatusText: @NlsSafe String?,
)

internal fun buildSessionTreeThreadRowPresentation(
  treeNode: SessionTreeNode.Thread,
  now: Long,
): SessionTreeThreadRowPresentation {
  val activityColor = treeNode.thread.activity.statusColor()
  val timeLabel = treeNode.thread.updatedAt.takeIf { it > 0 }?.let { timestamp ->
    formatRelativeTimeShort(timestamp, now)
  } ?: AgentSessionsBundle.message("toolwindow.time.unknown")
  val statusLabel = threadActivityDisplayName(treeNode.thread.activity)
  val costLabel = treeNode.thread.cost.takeIf { AgentSessionCostPresentationSettings.isEnabled() }?.toDisplayLabel()
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
    add(statusLabel)
    add(timeLabel)
    costLabel?.let(::add)
  }.joinToString(separator = ", ")
  return SessionTreeThreadRowPresentation(
    statusColor = activityColor,
    title = threadDisplayTitle(threadId = treeNode.thread.id, title = treeNode.thread.title),
    timeLabel = timeLabel,
    statusLabel = statusLabel,
    costLabel = costLabel,
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
  return AgentSessionsBundle.message(activity.statusMessageKey())
}

private fun AgentSessionCost.toDisplayLabel(): @NlsSafe String? {
  val amount = amountUsd?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: return null
  return when (kind) {
    AgentSessionCostKind.EXACT -> "$$amount"
    AgentSessionCostKind.ESTIMATED -> "~$$amount"
    AgentSessionCostKind.UNAVAILABLE -> null
  }
}

internal fun computeSessionTreeSharedTimeColumnWidth(fontMetrics: FontMetrics): Int {
  val nowLabel = AgentSessionsBundle.message("toolwindow.time.now")
  val unknownLabel = AgentSessionsBundle.message("toolwindow.time.unknown")
  return (SESSION_TREE_TIME_LABEL_SAMPLES + nowLabel + unknownLabel).maxOf(fontMetrics::stringWidth)
}
