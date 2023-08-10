// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.openapi.vcs.FilePath
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogColorManager
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JTable
import javax.swing.SwingConstants

class NewUiRootCellRenderer(properties: VcsLogUiProperties,
                            colorManager: VcsLogColorManager
) : RootCellRenderer(properties, colorManager) {
  private var stripePart: RootStripePart = RootStripePart.SINGLE

  init {
    setTextAlign(SwingConstants.LEFT)
  }

  override fun paintBackground(g: Graphics2D, x: Int, width: Int, height: Int) {
    g.color = myBorderColor
    g.fillRect(x, 0, width, height)
    val config = GraphicsUtil.setupAAPainting(g)

    val y = 0
    val x = LEFT_RIGHT_GAP
    val arc = ARC
    val stripeWidth = if (isNarrow) NARROW_STRIPE_WIDTH else width - 2 * LEFT_RIGHT_GAP

    g.color = myColor
    when (stripePart) {
      RootStripePart.START -> g.fillRoundRect(x, y, stripeWidth, height + arc, arc, arc)
      RootStripePart.MIDDLE -> g.fillRect(x, y, stripeWidth, height)
      RootStripePart.END -> g.fillRoundRect(x, y - arc, stripeWidth, height + BOTTOM_GAP, arc, arc)
      RootStripePart.SINGLE -> g.fillRoundRect(x, y, stripeWidth, height - BOTTOM_GAP, arc, arc)
    }
    config.restore()
  }

  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val renderer = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

    stripePart = getRootPart(value as FilePath?, table, row, column)

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
    private val LEFT_RIGHT_GAP
      get() = scale(2)

    private val BOTTOM_GAP
      get() = scale(2)

    private val ARC
      get() = scale(4)

    private val NARROW_STRIPE_WIDTH
      get() = scale(4)

    private fun getRootPart(current: FilePath?, table: JTable, row: Int, column: Int): RootStripePart {
      if (current == null) return RootStripePart.SINGLE
      val prev = if (row > 0) table.getValueAt(row - 1, column) else null
      val next = if (row <= table.rowCount - 2) table.getValueAt(row + 1, column) else null
      val isPrevSame = prev != null && prev == current
      val isNextSame = next != null && next == current

      return when {
        isPrevSame && isNextSame -> RootStripePart.MIDDLE
        !isPrevSame && !isNextSame -> RootStripePart.SINGLE
        isPrevSame -> RootStripePart.END
        else -> RootStripePart.START
      }
    }

    private enum class RootStripePart {
      START,
      MIDDLE,
      END,
      SINGLE
    }
  }
}

