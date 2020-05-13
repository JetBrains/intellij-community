// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui

import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredRenderer
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.Ellipse2D
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

open class CommitIconTableCellRenderer(
  private val graphColorSupplier: () -> JBColor,
  private val defaultCellHeight: Int = 22,
  private val graphLineWidth: Float = 1.5f
) : SimpleColoredRenderer(), TableCellRenderer {
  protected open val nodeWidth
    get() = JBUI.scale(8)
  protected val nodeCenterX
    get() = nodeWidth
  protected val nodeCenterY
    get() = defaultCellHeight / 2

  private var rowIndex = 0
  private var isLastRow = false
  protected var rowHeight = 0

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    drawCommitIcon(g as Graphics2D)
  }

  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    clear()
    setPaintFocusBorder(false)
    val editingRow = table?.editingRow == row
    acquireState(table, isSelected && !editingRow, hasFocus && !editingRow, row, column)
    cellState.updateRenderer(this)
    border = null
    this.rowIndex = row
    if (table != null) {
      this.isLastRow = row == table.rowCount - 1
      this.rowHeight = table.getRowHeight(row)
    }
    customizeRenderer(table, value, isSelected, hasFocus, row, column)
    return this
  }

  open fun customizeRenderer(table: JTable?,
                                 value: Any?,
                                 isSelected: Boolean,
                                 hasFocus: Boolean,
                                 row: Int,
                                 column: Int) {}

  protected open fun drawCommitIcon(g: Graphics2D) {
    val tableRowHeight = rowHeight
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    drawNode(g)

    if (rowIndex > 0) {
      drawEdge(g, tableRowHeight, false)
    }
    if (!isLastRow) {
      drawEdge(g, tableRowHeight, true)
    }
  }

  protected open fun drawNode(g: Graphics2D) {
    drawCircle(g, nodeCenterX, nodeCenterY)
  }

  protected open fun drawCircle(g: Graphics2D,
                                x0: Int,
                                y0: Int,
                                circleRadius: Int = nodeWidth / 2,
                                circleColor: Color = graphColorSupplier()) {
    val circle = Ellipse2D.Double(
      x0 - circleRadius + 0.5,
      y0 - circleRadius + 0.5,
      2.0 * circleRadius,
      2.0 * circleRadius
    )
    g.color = circleColor
    g.fill(circle)
  }

  protected open fun drawEdge(g: Graphics2D, tableRowHeight: Int, isDownEdge: Boolean) {
    val y1 = nodeCenterY
    val y2 = if (isDownEdge) tableRowHeight else 0
    val x = nodeCenterX
    g.color = graphColorSupplier()
    g.stroke = BasicStroke(graphLineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)
    g.drawLine(x, y1, x, y2)
  }
}
