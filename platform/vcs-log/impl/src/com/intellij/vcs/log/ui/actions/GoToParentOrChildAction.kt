// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToRow
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector.PARENT_COMMIT
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import com.intellij.vcs.log.ui.table.VcsLogCommitListModel
import com.intellij.vcs.log.ui.table.getCachedCommitMetadata
import com.intellij.vcs.log.ui.table.getCommitId
import java.awt.Component
import java.awt.event.KeyEvent

open class GoToParentOrChildAction(val parent: Boolean) : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val ui = e.getData(VcsLogDataKeys.VCS_LOG_UI) as? VcsLogUiEx
    if (ui == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true

    val table = ui.table
    if (e.inputEvent is KeyEvent && table is Component) {
      e.presentation.isEnabled = table.isFocusOwner
    }
    else {
      e.presentation.isEnabled = getRowsToJump(ui).isNotEmpty()
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    triggerUsage(e)

    val ui = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI) as VcsLogUiEx
    val rows = getRowsToJump(ui)

    if (rows.isEmpty()) {
      // can happen if the action was invoked by shortcut
      return
    }

    if (rows.size == 1) {
      ui.jumpToRow(rows.single(), false, true)
    }
    else {
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        if (parent) VcsLogBundle.message("action.go.to.select.parent.to.navigate")
        else VcsLogBundle.message("action.go.to.select.child.to.navigate"),
        createGroup(ui, rows), e.dataContext,
        JBPopupFactory.ActionSelectionAid.NUMBERING, false)
      popup.showInBestPositionFor(e.dataContext)
    }
  }

  private fun createGroup(ui: VcsLogUiEx, rows: List<Int>): ActionGroup {
    val actions = rows.mapTo(mutableListOf()) { row ->
      val text = ui.table.listModel.getActionText(row)
      object : DumbAwareAction(text, VcsLogBundle.message("action.go.to.navigate.to", text), null) {
        override fun actionPerformed(e: AnActionEvent) {
          triggerUsage(e)
          ui.jumpToRow(row, false, true)
        }
      }
    }
    return DefaultActionGroup(actions)
  }

  private fun DumbAwareAction.triggerUsage(e: AnActionEvent) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this) { data -> data.add(PARENT_COMMIT.with(parent)) }
  }

  @NlsActions.ActionText
  private fun VcsLogCommitListModel.getActionText(row: Int): String {
    val commitMetadata = getCachedCommitMetadata(row)
    if (commitMetadata != null) {
      val time: Long = commitMetadata.authorTime
      val commitMessage = "\"" + StringUtil.shortenTextWithEllipsis(commitMetadata.subject,
                                                                    40, 0,
                                                                    "...") + "\""
      return VcsLogBundle.message("action.go.to.select.hash.subject.author.date.time",
                                  commitMetadata.id.toShortString(),
                                  commitMessage,
                                  CommitPresentationUtil.getAuthorPresentation(commitMetadata),
                                  DateFormatUtil.formatDate(time),
                                  DateFormatUtil.formatTime(time))
    }
    return getCommitId(row)?.hash?.toShortString() ?: ""
  }

  private fun getRowsToJump(ui: VcsLogUiEx): List<Int> {
    val selectedRows = ui.table.selection.rows
    if (selectedRows.size != 1) return emptyList()
    return ui.dataPack.visibleGraph.getRowInfo(selectedRows.single()).getAdjacentRows(parent).sorted()
  }
}

class GoToParentRowAction : GoToParentOrChildAction(true)

class GoToChildRowAction : GoToParentOrChildAction(false)