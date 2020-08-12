// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table

import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer.BorderlessTableCellState
import javax.swing.JTable

class VcsLogStringCellRenderer(private val withSpeedSearchHighlighting: Boolean = false) : ColoredTableCellRenderer() {
  init {
    cellState = BorderlessTableCellState()
  }

  override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    if (value == null || table !is VcsLogGraphTable) {
      return
    }
    //noinspection HardCodedStringLiteral
    append(value.toString(), table.applyHighlighters(this, row, column, hasFocus, selected))
    if (withSpeedSearchHighlighting) {
      SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, selected)
    }
  }
}