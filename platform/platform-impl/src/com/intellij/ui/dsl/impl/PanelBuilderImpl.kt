// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.CellBuilderBase
import com.intellij.ui.dsl.PanelBuilder
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

  override fun alignHorizontal(horizontalAlign: HorizontalAlign): CellBuilderBase<PanelBuilder> {
    super.alignHorizontal(horizontalAlign)
    return this
  }

  override fun alignVertical(verticalAlign: VerticalAlign): CellBuilderBase<PanelBuilder> {
    super.alignVertical(verticalAlign)
    return this
  }

  override fun comment(@NlsContexts.DetailedDescription comment: String, maxLineLength: Int): CellBuilderBase<PanelBuilder> {
    super.comment(comment, maxLineLength)
    return this
  }

  override fun rightUnrelatedGap(): CellBuilderBase<PanelBuilder> {
    super.rightUnrelatedGap()
    return this
  }

  fun build(panel: DialogPanel, grid: JBGrid) {
    val columnsCount = rows.maxOf { it.cells.size }
    val rowsGridBuilder = RowsGridBuilder(panel, grid = grid)

    for ((rowIndex, row) in rows.withIndex()) {
      for ((cellIndex, cell) in row.cells.withIndex()) {
        val width: Int
        var rightGap = cell.rightGap
        if (cellIndex == row.cells.size - 1) {
          width = columnsCount - cellIndex
          if (cell.rightGap > 0) {
            LOG.warn("Right gap is set for last cell and will be ignored: rightGap = $rightGap")
            rightGap = 0
          }
        }
        else {
          width = 1
        }

        // todo
        if (cell is CellBuilderImpl<*>) {
          val insets = cell.component.insets
          val verticalGap = getDefaultVerticalGap(cell.component)
          val gaps = Gaps(top = verticalGap, bottom = verticalGap, right = rightGap)
          val visualPaddings = Gaps(top = insets.top, left = insets.left, bottom = insets.bottom, right = insets.right)
          rowsGridBuilder.cell(cell.component, width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                               gaps = gaps,
                               visualPaddings = visualPaddings)
        }
      }

      rowsGridBuilder.row()
      createCommentRow(rowsGridBuilder, row, columnsCount)
    }
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

  /**
   * Appends comment (currently one comment for a row is supported, can be fixed later)
   */
  private fun createCommentRow(builder: RowsGridBuilder, row: RowBuilderImpl, columnsCount: Int) {
    for ((column, cell) in row.cells.withIndex()) {
      cell.comment?.let {
        val additionalHorizontalIndent = if (cell is CellBuilderImpl<*> && cell.viewComponent is JToggleButton)
          dialogPanelConfig.spacing.horizontalIndent
        else
          0
        createComment(builder, column, columnsCount, row.label != null, it, additionalHorizontalIndent)
        return
      }
    }
  }

  private fun createComment(builder: RowsGridBuilder,
                            column: Int,
                            columnsCount: Int,
                            labeledRow: Boolean,
                            comment: JComponent,
                            additionalHorizontalIndent: Int) {
    if (labeledRow && column == 1) {
      builder.cell(comment, width = columnsCount)
    }
    else {
      val gaps = Gaps(left = additionalHorizontalIndent, bottom = dialogPanelConfig.spacing.verticalCommentBottomGap)
      builder.skip(column)
      builder.cell(comment, columnsCount - column, gaps = gaps)
    }
    builder.row()
  }
}
