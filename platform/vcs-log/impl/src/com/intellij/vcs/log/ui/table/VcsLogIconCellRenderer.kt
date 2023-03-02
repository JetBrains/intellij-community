// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer.VcsLogTableCellState
import javax.swing.JTable

private val PADDING: JBInsets = JBUI.insets(0, 7, 0, 13)

abstract class VcsLogIconCellRenderer : ColoredTableCellRenderer(), VcsLogCellRenderer {
  init {
    cellState = VcsLogTableCellState()
    myBorder = Borders.empty()
    ipad = PADDING
    iconTextGap = 0

    isTransparentIconBackground = true
  }

  final override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    table as VcsLogGraphTable
    table.applyHighlighters(this, row, column, hasFocus, selected)

    customize(table, value, selected, hasFocus, row, column)
  }

  protected abstract fun customize(table: VcsLogGraphTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int)

  override fun getPreferredWidth(): VcsLogCellRenderer.PreferredWidth {
    return VcsLogCellRenderer.PreferredWidth.Fixed { EmptyIcon.ICON_16.iconWidth + PADDING.width() }
  }
}