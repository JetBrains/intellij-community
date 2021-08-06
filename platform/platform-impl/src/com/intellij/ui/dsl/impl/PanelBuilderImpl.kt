// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.CellBuilderBase
import com.intellij.ui.dsl.PanelBuilder
import com.intellij.ui.dsl.RIGHT_GAP_UNASSIGNED
import com.intellij.ui.dsl.RowBuilder
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.JBGrid
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.*
import javax.swing.text.JTextComponent

@ApiStatus.Experimental
internal class PanelBuilderImpl(private val dialogPanelConfig: DialogPanelConfig) : CellBuilderBaseImpl<PanelBuilder>(
  dialogPanelConfig), PanelBuilder {

  companion object {
    private val LOG = Logger.getInstance(PanelBuilderImpl::class.java)

    private val DEFAULT_VERTICAL_GAP_COMPONENTS = setOf(JTextComponent::class, AbstractButton::class, JSpinner::class, JLabel::class)
  }

  private val rows = mutableListOf<RowBuilderImpl>()

  override fun row(@Nls label: String, init: RowBuilder.() -> Unit): RowBuilder {
    return row(JLabel(label), init)
  }

  override fun row(label: JLabel?, init: RowBuilder.() -> Unit): RowBuilder {
    val result = RowBuilderImpl(dialogPanelConfig, label)
    result.init()
    rows.add(result)
    return result
  }

  override fun alignHorizontal(horizontalAlign: HorizontalAlign): PanelBuilder {
    super.alignHorizontal(horizontalAlign)
    return this
  }

  override fun alignVertical(verticalAlign: VerticalAlign): PanelBuilder {
    super.alignVertical(verticalAlign)
    return this
  }

  override fun comment(@NlsContexts.DetailedDescription comment: String, maxLineLength: Int): PanelBuilder {
    super.comment(comment, maxLineLength)
    return this
  }

  override fun rightLabelGap(): PanelBuilder {
    super.rightLabelGap()
    return this
  }

  fun build(panel: DialogPanel, grid: JBGrid) {
    val maxColumnsCount = rows.filterNot { it.independent }.maxOf { it.cells.size }
    val rowsGridBuilder = RowsGridBuilder(panel, grid = grid)

    for (row in rows) {
      if (row.independent) {
        val subGrid = rowsGridBuilder.subGrid(width = maxColumnsCount, horizontalAlign = HorizontalAlign.FILL,
                                              verticalAlign = VerticalAlign.FILL)
        buildRowAndComment(row, row.cells.size, panel, RowsGridBuilder(panel, subGrid))
        rowsGridBuilder.row()
      }
      else {
        buildRowAndComment(row, maxColumnsCount, panel, rowsGridBuilder)
      }
    }
  }

  private fun buildRowAndComment(row: RowBuilderImpl, maxColumnsCount: Int, panel: DialogPanel, builder: RowsGridBuilder) {
    for ((cellIndex, cell) in row.cells.withIndex()) {
      val lastCell = cellIndex == row.cells.size - 1
      val rightGap = getRightGap(cell, lastCell, row.label != null && cellIndex == 0)
      val width = if (lastCell) maxColumnsCount - cellIndex else 1

      when (cell) {
        is CellBuilderImpl<*> -> {
          val insets = cell.component.insets
          val visualPaddings = Gaps(top = insets.top, left = insets.left, bottom = insets.bottom, right = insets.right)
          val verticalGap = getDefaultVerticalGap(cell.component)
          val gaps = Gaps(top = verticalGap, bottom = verticalGap, right = rightGap)
          builder.cell(cell.component, width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                       gaps = gaps, visualPaddings = visualPaddings)
        }
        is PanelBuilderImpl -> {
          // todo visualPaddings
          val gaps = Gaps(right = rightGap)
          val subGrid = builder.subGrid(width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                                        gaps = gaps)

          cell.build(panel, subGrid)
        }
        else -> throw IllegalArgumentException()
      }
    }

    builder.row()
    createCommentRow(row, maxColumnsCount, builder)
  }

  /**
   * Returns default top and bottom gap for [component]
   */
  private fun getDefaultVerticalGap(component: JComponent): Int {
    return if (DEFAULT_VERTICAL_GAP_COMPONENTS.any { clazz ->
        clazz.isInstance(component)
      }) dialogPanelConfig.spacing.verticalComponentGap
    else 0
  }

  private fun getRightGap(cell: CellBuilderBase<*>, lastCell: Boolean, rowLabel: Boolean): Int {
    val rightGap = cell.rightGap
    if (lastCell) {
      if (rightGap != RIGHT_GAP_UNASSIGNED) {
        LOG.warn("Right gap is set for last cell and will be ignored: rightGap = $rightGap")
      }
      return 0
    }

    if (rightGap != RIGHT_GAP_UNASSIGNED) {
      return rightGap
    }

    return if (rowLabel) dialogPanelConfig.spacing.horizontalLabelGap else dialogPanelConfig.spacing.horizontalDefaultGap
  }


  /**
   * Appends comment (currently one comment for a row is supported, can be fixed later)
   */
  private fun createCommentRow(row: RowBuilderImpl, maxColumnsCount: Int, builder: RowsGridBuilder) {
    for ((column, cell) in row.cells.withIndex()) {
      cell.comment?.let { comment ->
        val additionalHorizontalIndent = if (cell is CellBuilderImpl<*> && cell.viewComponent is JToggleButton)
          dialogPanelConfig.spacing.horizontalIndent
        else
          0

        if (row.label != null && column == 1) {
          builder.cell(comment, width = maxColumnsCount)
        }
        else {
          val gaps = Gaps(left = additionalHorizontalIndent, bottom = dialogPanelConfig.spacing.verticalCommentBottomGap)
          builder.skip(column)
          builder.cell(comment, maxColumnsCount - column, gaps = gaps)
        }
        builder.row()
        return
      }
    }
  }
}
