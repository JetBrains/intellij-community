// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.statusMessageKey
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.agentSessionThreadStatusIcon
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.toolwindow.tree.formatRelativeTimeShort
import com.intellij.agent.workbench.sessions.tree.threadDisplayTitle
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import org.jetbrains.annotations.Nls
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

private const val MAX_IDLE_POPUP_ROWS = 5
private const val MAX_POPUP_ROW_TITLE_LENGTH = 60

internal data class AgentSessionsActivityThreadRow(
  @JvmField val path: String,
  @JvmField val projectName: @NlsSafe String,
  @JvmField val worktreeName: @NlsSafe String?,
  @JvmField val thread: AgentSessionThread,
) {
  val locationLabel: @NlsSafe String
    get() = worktreeName?.let { "$projectName / $it" } ?: projectName
}

internal enum class AgentSessionsActivitySectionKind(
  @JvmField val titleKey: String,
) {
  NEEDS_ATTENTION("toolwindow.activity.header.section.needs.attention"),
  RUNNING("toolwindow.activity.header.section.running"),
  DONE("toolwindow.activity.header.section.done"),
  IDLE("toolwindow.activity.header.section.idle"),
}

internal data class AgentSessionsActivitySection(
  @JvmField val kind: AgentSessionsActivitySectionKind,
  @JvmField val rows: List<AgentSessionsActivityThreadRow>,
)

internal data class AgentSessionsActivitySummary(
  @JvmField val attentionRows: List<AgentSessionsActivityThreadRow>,
  @JvmField val runningRows: List<AgentSessionsActivityThreadRow>,
  @JvmField val doneRows: List<AgentSessionsActivityThreadRow>,
  @JvmField val idleRows: List<AgentSessionsActivityThreadRow>,
) {
  val hasKnownThreads: Boolean
    get() = attentionRows.isNotEmpty() || runningRows.isNotEmpty() || doneRows.isNotEmpty() || idleRows.isNotEmpty()

  val hasPrimaryActivity: Boolean
    get() = attentionRows.isNotEmpty() || runningRows.isNotEmpty()

  fun popupSections(): List<AgentSessionsActivitySection> {
    return buildList {
      if (attentionRows.isNotEmpty()) add(AgentSessionsActivitySection(AgentSessionsActivitySectionKind.NEEDS_ATTENTION, attentionRows))
      if (runningRows.isNotEmpty()) add(AgentSessionsActivitySection(AgentSessionsActivitySectionKind.RUNNING, runningRows))
      if (doneRows.isNotEmpty()) add(AgentSessionsActivitySection(AgentSessionsActivitySectionKind.DONE, doneRows))
      if (idleRows.isNotEmpty()) add(AgentSessionsActivitySection(AgentSessionsActivitySectionKind.IDLE, idleRows))
    }
  }

  companion object {
    val EMPTY: AgentSessionsActivitySummary = AgentSessionsActivitySummary(
      attentionRows = emptyList(),
      runningRows = emptyList(),
      doneRows = emptyList(),
      idleRows = emptyList(),
    )
  }
}

internal fun buildAgentSessionsActivitySummary(
  state: AgentSessionsState,
  maxIdleRows: Int = MAX_IDLE_POPUP_ROWS,
): AgentSessionsActivitySummary {
  val rows = collectAgentSessionsActivityThreadRows(state)
  return AgentSessionsActivitySummary(
    attentionRows = rows.filter { it.thread.activity.isAttentionActivity() }.sortedByRecentActivity(),
    runningRows = rows.filter { it.thread.activity == AgentThreadActivity.PROCESSING }.sortedByRecentActivity(),
    doneRows = rows.filter { it.thread.activity == AgentThreadActivity.UNREAD }.sortedByRecentActivity(),
    idleRows = rows.filter { it.thread.activity == AgentThreadActivity.READY }.sortedByRecentActivity().take(maxIdleRows.coerceAtLeast(0)),
  )
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
  val statusLabel = AgentSessionsBundle.message(row.thread.activity.statusMessageKey())
  val timeLabel = row.thread.updatedAt.takeIf { it > 0 }?.let { timestamp ->
    formatRelativeTimeShort(timestamp, now)
  } ?: AgentSessionsBundle.message("toolwindow.time.unknown")
  return AgentSessionsBundle.message(
    "toolwindow.activity.header.popup.row",
    title,
    statusLabel,
    row.locationLabel,
    timeLabel,
  )
}

internal fun buildAgentSessionsActivityPopupActionGroup(
  summary: AgentSessionsActivitySummary,
  now: Long,
  openThread: (AgentSessionsActivityThreadRow) -> Unit,
): DefaultActionGroup {
  return DefaultActionGroup().apply {
    summary.popupSections().forEachIndexed { index, section ->
      if (index > 0) addSeparator()
      add(Separator.create(AgentSessionsBundle.message(section.kind.titleKey)))
      section.rows.forEach { row ->
        add(AgentSessionsActivityOpenThreadAction(row, now, openThread))
      }
    }
  }
}

internal class AgentSessionsActivityHeader(
  private val nowProvider: () -> Long,
  private val openThread: (AgentSessionsActivityThreadRow) -> Unit,
) : JPanel(BorderLayout()) {
  private var summary: AgentSessionsActivitySummary = AgentSessionsActivitySummary.EMPTY

  private val attentionButton = createCounterButton().apply {
    name = "agentSessionsActivityNeedsAttention"
    addActionListener { showActivityPopup(this) }
  }
  private val runningButton = createCounterButton().apply {
    name = "agentSessionsActivityRunning"
    addActionListener { showActivityPopup(this) }
  }
  private val doneButton = createCounterButton().apply {
    name = "agentSessionsActivityDone"
    addActionListener { showActivityPopup(this) }
  }
  private val idleButton = createCounterButton().apply {
    name = "agentSessionsActivityIdle"
    addActionListener { showActivityPopup(this) }
  }

  init {
    name = "agentSessionsActivityHeader"
    isOpaque = false
    isVisible = false
    border = JBUI.Borders.empty(4, 8, 2, 8)
    add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      isOpaque = false
      add(attentionButton)
      add(runningButton)
      add(doneButton)
      add(idleButton)
    }, BorderLayout.CENTER)
  }

  fun update(state: AgentSessionsState) {
    updateSummary(buildAgentSessionsActivitySummary(state))
  }

  internal fun updateSummary(summary: AgentSessionsActivitySummary) {
    this.summary = summary
    attentionButton.text = AgentSessionsBundle.message("toolwindow.activity.header.needs.attention", summary.attentionRows.size)
    runningButton.text = AgentSessionsBundle.message("toolwindow.activity.header.running", summary.runningRows.size)
    doneButton.text = AgentSessionsBundle.message("toolwindow.activity.header.done", summary.doneRows.size)
    doneButton.isVisible = summary.doneRows.isNotEmpty()
    idleButton.text = AgentSessionsBundle.message("toolwindow.activity.header.idle", summary.idleRows.size)
    idleButton.isVisible = summary.idleRows.isNotEmpty()
    isVisible = summary.hasKnownThreads
    revalidate()
    repaint()
  }

  private fun showActivityPopup(anchor: JComponent) {
    val currentSummary = summary
    if (currentSummary.popupSections().isEmpty()) return
    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        AgentSessionsBundle.message("toolwindow.activity.header.popup.title"),
        buildAgentSessionsActivityPopupActionGroup(currentSummary, nowProvider(), openThread),
        DataManager.getInstance().getDataContext(this),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
      .showUnderneathOf(anchor)
  }
}

private class AgentSessionsActivityOpenThreadAction(
  private val row: AgentSessionsActivityThreadRow,
  now: Long,
  private val openThread: (AgentSessionsActivityThreadRow) -> Unit,
) : DumbAwareAction(
  agentSessionsActivityPopupRowText(row, now),
  null,
  agentSessionThreadStatusIcon(row.thread.provider, row.thread.activity),
) {
  override fun actionPerformed(e: AnActionEvent) {
    openThread(row)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private fun createCounterButton(): JButton {
  return JButton().apply {
    isFocusable = false
    isBorderPainted = false
    isContentAreaFilled = false
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    border = JBUI.Borders.empty(1, 0)
    font = JBUI.Fonts.smallFont()
  }
}

private fun collectAgentSessionsActivityThreadRows(state: AgentSessionsState): List<AgentSessionsActivityThreadRow> {
  return buildList {
    state.projects.forEach { project ->
      addProjectThreadRows(project)
      project.worktrees.forEach { worktree ->
        addWorktreeThreadRows(project, worktree)
      }
    }
  }
}

private fun MutableList<AgentSessionsActivityThreadRow>.addProjectThreadRows(project: AgentProjectSessions) {
  project.threads.forEach { thread ->
    if (!isAgentSessionNewSessionId(thread.id)) {
      add(
        AgentSessionsActivityThreadRow(
          path = project.path,
          projectName = project.name,
          worktreeName = null,
          thread = thread,
        )
      )
    }
  }
}

private fun MutableList<AgentSessionsActivityThreadRow>.addWorktreeThreadRows(project: AgentProjectSessions, worktree: AgentWorktree) {
  worktree.threads.forEach { thread ->
    if (!isAgentSessionNewSessionId(thread.id)) {
      add(
        AgentSessionsActivityThreadRow(
          path = worktree.path,
          projectName = project.name,
          worktreeName = worktree.name,
          thread = thread,
        )
      )
    }
  }
}

private fun List<AgentSessionsActivityThreadRow>.sortedByRecentActivity(): List<AgentSessionsActivityThreadRow> {
  return sortedWith(
    compareByDescending<AgentSessionsActivityThreadRow> { it.thread.updatedAt }
      .thenBy { it.projectName }
      .thenBy { it.worktreeName ?: "" }
      .thenBy { it.thread.id }
  )
}

private fun AgentThreadActivity.isAttentionActivity(): Boolean {
  return this == AgentThreadActivity.NEEDS_INPUT || this == AgentThreadActivity.REVIEWING
}
