// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.withAgentThreadActivityBadge
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.agentSessionThreadStatusIcon
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.toolwindow.tree.formatRelativeTimeShort
import com.intellij.agent.workbench.sessions.tree.threadDisplayTitle
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.Nls
import javax.swing.Icon

private const val MAX_POPUP_ROW_TITLE_LENGTH: Int = 60

internal fun createAgentSessionsTitleActions(): List<AgentSessionsActivityCounterAction> {
  return listOf(
    AgentSessionsActivityCounterAction(AgentSessionsActivityBucket.ATTENTION),
    AgentSessionsActivityCounterAction(AgentSessionsActivityBucket.RUNNING),
    AgentSessionsActivityCounterAction(AgentSessionsActivityBucket.DONE),
    AgentSessionsActivityCounterAction(AgentSessionsActivityBucket.IDLE),
  )
}

internal class AgentSessionsActivityCounterAction(
  private val bucket: AgentSessionsActivityBucket,
) : DumbAwareAction() {

  private val staticIcon: Icon = bucket.staticIcon()
  private val activeIcon: Icon = bucket.activeIcon(staticIcon)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val rows = rowsFor(e.project)
    val presentation = e.presentation
    presentation.text = rows.size.toString()
    presentation.description = AgentSessionsBundle.message(bucket.tooltipKey)
    presentation.icon = if (rows.isNotEmpty()) activeIcon else staticIcon
    presentation.isEnabled = rows.isNotEmpty()
    presentation.isVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val component = e.inputEvent?.component ?: return
    val rows = rowsFor(project)
    if (rows.isEmpty()) return
    val now = System.currentTimeMillis()
    val group = DefaultActionGroup().apply {
      rows.forEach { row -> add(AgentSessionsActivityOpenThreadAction(project, row, now)) }
    }
    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        AgentSessionsBundle.message(bucket.popupTitleKey),
        group,
        DataManager.getInstance().getDataContext(component),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
      .showUnderneathOf(component)
  }

  private fun rowsFor(project: Project?): List<AgentSessionsActivityThreadRow> {
    val service = project?.service<AgentSessionsActivityService>() ?: return emptyList()
    return service.latestSummary().rowsFor(bucket)
  }
}

private class AgentSessionsActivityOpenThreadAction(
  private val project: Project,
  private val row: AgentSessionsActivityThreadRow,
  now: Long,
) : DumbAwareAction(
  agentSessionsActivityPopupRowText(row, now),
  null,
  agentSessionThreadStatusIcon(row.thread.provider, row.thread.activity),
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    service<AgentSessionLaunchService>().openChatThread(
      path = row.path,
      thread = row.thread,
      entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      currentProject = project,
    )
  }
}

internal fun agentSessionsActivityPopupRowText(
  row: AgentSessionsActivityThreadRow,
  now: Long,
): @Nls String {
  val title = StringUtil.shortenTextWithEllipsis(
    threadDisplayTitle(threadId = row.thread.id, title = row.thread.title),
    MAX_POPUP_ROW_TITLE_LENGTH,
    0,
    true,
  )
  val timeLabel = row.thread.updatedAt.takeIf { it > 0 }?.let { timestamp ->
    formatRelativeTimeShort(timestamp, now)
  } ?: AgentSessionsBundle.message("toolwindow.time.unknown")
  return AgentSessionsBundle.message("toolwindow.activity.popup.row", title, row.locationLabel, timeLabel)
}

private val AgentSessionsActivityBucket.tooltipKey: String
  get() = when (this) {
    AgentSessionsActivityBucket.ATTENTION -> "toolwindow.activity.action.attention.tooltip"
    AgentSessionsActivityBucket.RUNNING -> "toolwindow.activity.action.running.tooltip"
    AgentSessionsActivityBucket.DONE -> "toolwindow.activity.action.done.tooltip"
    AgentSessionsActivityBucket.IDLE -> "toolwindow.activity.action.idle.tooltip"
  }

private val AgentSessionsActivityBucket.popupTitleKey: String
  get() = when (this) {
    AgentSessionsActivityBucket.ATTENTION -> "toolwindow.activity.popup.attention.title"
    AgentSessionsActivityBucket.RUNNING -> "toolwindow.activity.popup.running.title"
    AgentSessionsActivityBucket.DONE -> "toolwindow.activity.popup.done.title"
    AgentSessionsActivityBucket.IDLE -> "toolwindow.activity.popup.idle.title"
  }

private fun AgentSessionsActivityBucket.staticIcon(): Icon {
  return when (this) {
    AgentSessionsActivityBucket.ATTENTION -> withAgentThreadActivityBadge(EmptyIcon.ICON_16, AgentThreadActivity.NEEDS_INPUT)
    AgentSessionsActivityBucket.RUNNING -> withAgentThreadActivityBadge(EmptyIcon.ICON_16, AgentThreadActivity.PROCESSING)
    AgentSessionsActivityBucket.DONE -> withAgentThreadActivityBadge(EmptyIcon.ICON_16, AgentThreadActivity.UNREAD)
    AgentSessionsActivityBucket.IDLE -> EmptyIcon.ICON_16
  }
}

private fun AgentSessionsActivityBucket.activeIcon(staticIcon: Icon): Icon {
  return if (this == AgentSessionsActivityBucket.RUNNING) AnimatedIcon.Default() else staticIcon
}
