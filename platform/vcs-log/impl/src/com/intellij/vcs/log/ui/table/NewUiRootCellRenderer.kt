// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.platform.vcs.impl.shared.ui.RepositoryColorStripe
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColorStripeSegment
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.render.RootCell
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JTable
import javax.swing.SwingConstants

internal class NewUiRootCellRenderer(properties: VcsLogUiProperties, colorManager: VcsLogColorManager) : RootCellRenderer(properties, colorManager) {
  private var stripePart: RepositoryColorStripeSegment = RepositoryColorStripeSegment.SINGLE

  init {
    setTextAlign(SwingConstants.LEFT)
  }

  override fun paintBackground(g: Graphics2D, x: Int, width: Int, height: Int) {
    g.color = myBorderColor
    g.fillRect(x, 0, width, height)

    if (isNarrow) {
      RepositoryColorStripe.paintSegment(g, myColor, height, stripePart)
    }
    else {
      RepositoryColorStripe.paintSegment(g, myColor, height, stripePart, width = width - 2 * RepositoryColorStripe.LEFT_GAP)
    }
  }

  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val renderer = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

    stripePart = getRootPart(value as RootCell?, table, row, column)

    myBorderColor = if (isNarrow) {
      (table as VcsLogGraphTable).getStyle(row, column, hasFocus, false, false).background!!
    }
    else {
      VcsLogGraphTable.getTableBackground()
    }

    return renderer
  }

  override fun getRootNameInsets(): Insets = JBUI.insets(0, 4)

  companion object {
    private fun getRootPart(current: RootCell?, table: JTable, row: Int, column: Int): RepositoryColorStripeSegment {
      if (current == null) return RepositoryColorStripeSegment.SINGLE
      val prev = if (row > 0) table.getValueAt(row - 1, column) else null
      val next = if (row < table.rowCount - 1) table.getValueAt(row + 1, column) else null
      return RepositoryColorStripe.resolveSegment(prev != null && prev == current, next != null && next == current)
    }
  }
}

