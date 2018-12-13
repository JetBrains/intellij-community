// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.AbstractVcsLogUi
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import java.awt.event.KeyEvent

open class GoToParentOrChildAction(val parent: Boolean) : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val ui = e.getData(VcsLogDataKeys.VCS_LOG_UI)
    if (ui == null || ui !is AbstractVcsLogUi) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = ui.table.isFocusOwner && (e.inputEvent is KeyEvent || getRowsToJump(ui).isNotEmpty())
  }

  override fun actionPerformed(e: AnActionEvent) {
    VcsLogUsageTriggerCollector.triggerUsage(e)

    val ui = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI) as AbstractVcsLogUi
    val rows = getRowsToJump(ui)

    if (rows.isEmpty()) {
      // can happen if the action was invoked by shortcut
      return
    }

    if (rows.size == 1) {
      ui.jumpToRow(rows.single())
    }
    else {
      val popup = JBPopupFactory.getInstance().createActionGroupPopup("Select ${if (parent) "Parent" else "Child"} to Navigate",
                                                                      createGroup(ui, rows), e.dataContext,
                                                                      JBPopupFactory.ActionSelectionAid.NUMBERING, false)
      popup.showInBestPositionFor(e.dataContext)
    }
  }

  private fun createGroup(ui: AbstractVcsLogUi, rows: List<Int>): ActionGroup {
    val actions = rows.mapTo(mutableListOf()) { row ->
      val text = getActionText(ui.table.model.getCommitMetadata(row))
      object : DumbAwareAction(text, "Navigate to $text", null) {
        override fun actionPerformed(e: AnActionEvent) {
          VcsLogUsageTriggerCollector.triggerUsage(e, "Go to ${if (parent) "Parent" else "Child"} Commit.Select from Popup")
          ui.jumpToRow(row)
        }
      }
    }
    return DefaultActionGroup(actions)
  }

  private fun getActionText(commitMetadata: VcsCommitMetadata): String {
    var text = commitMetadata.id.toShortString()
    if (commitMetadata !is LoadingDetails) {
      text += " " + CommitPresentationUtil.getShortSummary(commitMetadata, false, 40)
    }
    return text
  }

  private fun getRowsToJump(ui: AbstractVcsLogUi): List<Int> {
    val selectedRows = ui.table.selectedRows
    if (selectedRows.size != 1) return emptyList()
    return ui.dataPack.visibleGraph.getRowInfo(selectedRows.single()).getAdjacentRows(parent).sorted()
  }
}

class GoToParentRowAction : GoToParentOrChildAction(true)

class GoToChildRowAction : GoToParentOrChildAction(false)