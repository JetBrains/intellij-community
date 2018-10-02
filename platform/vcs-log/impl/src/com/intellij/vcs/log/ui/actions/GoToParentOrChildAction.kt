// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.AbstractVcsLogUi

open class GoToParentOrChildAction(val parent: Boolean) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val ui = e.getData(VcsLogDataKeys.VCS_LOG_UI)
    if (ui == null || ui !is AbstractVcsLogUi) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = getRowsToJump(ui).isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    VcsLogUsageTriggerCollector.triggerUsage(e)

    val ui = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI) as AbstractVcsLogUi
    val rows = getRowsToJump(ui)
    ui.jumpToRow(rows.first())
  }

  private fun getRowsToJump(ui: AbstractVcsLogUi): List<Int> {
    val selectedRows = ui.table.selectedRows
    if (selectedRows.size != 1) return emptyList()
    return ui.dataPack.visibleGraph.getRowInfo(selectedRows.single()).getAdjacentRows(parent)
  }
}

class GoToParentRowAction : GoToParentOrChildAction(true)

class GoToChildRowAction : GoToParentOrChildAction(false)