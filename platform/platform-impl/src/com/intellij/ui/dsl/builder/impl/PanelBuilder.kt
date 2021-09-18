// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import org.jetbrains.annotations.ApiStatus
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.math.min

/**
 * Throws exception instead of logging warning. Useful while forms building to avoid layout mistakes
 */
private const val FAIL_ON_WARN = false

private val DEFAULT_VERTICAL_GAP_COMPONENTS = setOf(
  AbstractButton::class,
  JComboBox::class,
  JLabel::class,
  JSpinner::class,
  JTextComponent::class,
  SeparatorComponent::class,
  TextFieldWithBrowseButton::class,
  TitledSeparator::class
)

@ApiStatus.Internal
internal class PanelBuilder(val rows: List<RowImpl>, val dialogPanelConfig: DialogPanelConfig, val panel: DialogPanel, val grid: Grid) {

  private companion object {
    private val LOG = Logger.getInstance(PanelBuilder::class.java)
  }

  fun build() {
    if (rows.isEmpty()) {
      return
    }

    preprocess()

    val maxColumnsCount = getMaxColumnsCount()
    val rowsGridBuilder = RowsGridBuilder(panel, grid = grid)
      .defaultVerticalAlign(VerticalAlign.CENTER)
      .defaultBaselineAlign(true)

    for (row in rows) {
      if (!checkRow(row)) {
        continue
      }

      val rowGaps = getRowGaps(row)
      rowsGridBuilder.setRowGaps(VerticalGaps(top = rowGaps.top))

      when (row.rowLayout) {
        RowLayout.INDEPENDENT -> {
          val subGridBuilder = rowsGridBuilder.subGridBuilder(width = maxColumnsCount, horizontalAlign = HorizontalAlign.FILL,
            gaps = Gaps(left = row.getIndent()))
          val cells = row.cells
          buildRow(cells, row.label != null, 0, cells.size, panel, subGridBuilder)
          subGridBuilder.row()

          buildCommentRow(cells, 0, cells.size, subGridBuilder)
          setLastColumnResizable(subGridBuilder)
          rowsGridBuilder.row()
        }

        RowLayout.LABEL_ALIGNED -> {
          buildCell(row.cells[0], true, row.getIndent(), row.cells.size == 1, 1, panel, rowsGridBuilder)

          if (row.cells.size > 1) {
            val subGridBuilder = rowsGridBuilder.subGridBuilder(width = maxColumnsCount - 1, horizontalAlign = HorizontalAlign.FILL)
            val cells = row.cells.subList(1, row.cells.size)
            buildRow(cells, false, 0, cells.size, panel, subGridBuilder)
            setLastColumnResizable(subGridBuilder)
          }
          rowsGridBuilder.row()

          val commentedCellIndex = getCommentedCellIndex(row.cells)
          when {
            commentedCellIndex in 0..1 -> {
              buildCommentRow(row.cells, row.getIndent(), maxColumnsCount, rowsGridBuilder)
            }
            commentedCellIndex > 1 -> {
              // Always put comment for cells with index more than 1 at second cell because it's hard to implement
              // more correct behaviour now. Can be fixed later
              buildCommentRow(listOf(row.cells[0], row.cells[commentedCellIndex]), 0, maxColumnsCount, rowsGridBuilder)
            }
          }
        }

        RowLayout.PARENT_GRID -> {
          buildRow(row.cells, row.label != null, row.getIndent(), maxColumnsCount, panel, rowsGridBuilder)
          rowsGridBuilder.row()

          buildCommentRow(row.cells, row.getIndent(), maxColumnsCount, rowsGridBuilder)
        }
      }

      row.rowComment?.let {
        val gaps = Gaps(left = row.getIndent(), bottom = dialogPanelConfig.spacing.commentBottomGap)
        rowsGridBuilder.cell(it, maxColumnsCount, gaps = gaps)
        rowsGridBuilder.row()
      }

      val rowsGaps = rowsGridBuilder.grid.rowsGaps
      rowsGaps[rowsGaps.size - 2] = rowsGaps[rowsGaps.size - 2].copy(bottom = rowGaps.bottom)
    }

    setLastColumnResizable(rowsGridBuilder)
    checkNoDoubleRowGaps(grid)
  }

  /**
   * Preprocesses rows/cells and adds necessary rows/cells
   * 1. Labels, see [Cell.label]
   */
  private fun preprocess() {
    for (row in rows) {
      var i = 0
      while (i < row.cells.size) {
        val cell = row.cells[i]
        if (cell is CellImpl<*>) {
          cell.label?.let {
            val labelCell = CellImpl(dialogPanelConfig, it, row)
              .gap(RightGap.SMALL)
            row.cells.add(i, labelCell)

            if (isAllowedLabel(cell)) {
              labelCell(it, cell)
            } else {
              warn("Unsupported labeled component: ${cell.component.javaClass.simpleName}")
            }
            i++
          }
        }

        i++
      }
    }
  }

  private fun setLastColumnResizable(builder: RowsGridBuilder) {
    if (builder.resizableColumns.isEmpty() && builder.columnsCount > 0) {
      builder.resizableColumns.add(builder.columnsCount - 1)
    }
  }

  private fun checkRow(row: RowImpl): Boolean {
    if (row.cells.isEmpty()) {
      warn("Row should not be empty")
      return false
    }

    return true
  }

  private fun checkNoDoubleRowGaps(grid: Grid) {
    val gaps = grid.rowsGaps
    for (i in gaps.indices) {
      if (i > 0 && gaps[i - 1].bottom > 0 && gaps[i].top > 0) {
        warn("There is double gap between two near rows")
      }
    }
  }

  private fun buildRow(cells: List<CellBaseImpl<*>?>,
                       firstCellLabel: Boolean,
                       firstCellIndent: Int,
                       maxColumnsCount: Int,
                       panel: DialogPanel,
                       builder: RowsGridBuilder) {
    for ((cellIndex, cell) in cells.withIndex()) {
      val lastCell = cellIndex == cells.size - 1
      val width = if (lastCell) maxColumnsCount - cellIndex else 1
      val leftGap = if (cellIndex == 0) firstCellIndent else 0
      val label = (cell as? CellImpl<*>)?.component as? JLabel
      if (label != null && cell.rightGap == RightGap.SMALL && cellIndex < cells.size - 1 &&
          isAllowedLabel(cells[cellIndex + 1]) && (cells[cellIndex + 1] as? CellImpl<*>)?.label == null) {
        warn("Panel.row(label) or Cell.label should be used for labeled components, label = ${label.text}")
      }

      buildCell(cell, firstCellLabel && cellIndex == 0, leftGap, lastCell, width, panel, builder)
    }
  }

  private fun buildCell(cell: CellBaseImpl<*>?, rowLabel: Boolean, leftGap: Int, lastCell: Boolean, width: Int,
                        panel: DialogPanel, builder: RowsGridBuilder) {
    val rightGap = getRightGap(cell, lastCell, rowLabel)

    when (cell) {
      is CellImpl<*> -> {
        val insets = cell.component.origin.insets
        val visualPaddings = Gaps(top = insets.top, left = insets.left, bottom = insets.bottom, right = insets.right)
        val gaps = cell.customGaps ?: getComponentGaps(leftGap, rightGap, cell.component)
        builder.cell(cell.component, width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
          resizableColumn = cell.resizableColumn,
          gaps = gaps, visualPaddings = visualPaddings)
      }
      is PanelImpl -> {
        // todo visualPaddings
        val gaps = cell.customGaps ?: Gaps(left = leftGap, right = rightGap)
        val subGrid = builder.subGrid(width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
          gaps = gaps)

        val prevSpacingConfiguration = dialogPanelConfig.spacing
        cell.spacingConfiguration?.let {
          dialogPanelConfig.spacing = it
        }
        try {
          val subBuilder = PanelBuilder(cell.rows, dialogPanelConfig, panel, subGrid)
          subBuilder.build()
        }
        finally {
          dialogPanelConfig.spacing = prevSpacingConfiguration
        }
      }
      null -> {
        builder.skip(1)
      }
    }
  }

  private fun getComponentGaps(left: Int, right: Int, component: JComponent): Gaps {
    val top = getDefaultVerticalGap(component)
    var bottom = top
    if (component is JLabel && component.getClientProperty(DSL_LABEL_NO_BOTTOM_GAP_PROPERTY) == true) {
      bottom = 0
    }
    return Gaps(top = top, left = left, bottom = bottom, right = right)
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

  private fun getMaxColumnsCount(): Int {
    return rows.maxOf {
      when (it.rowLayout) {
        RowLayout.INDEPENDENT -> 1
        RowLayout.LABEL_ALIGNED -> min(2, it.cells.size)
        RowLayout.PARENT_GRID -> it.cells.size
      }
    }
  }

  private fun getRightGap(cell: CellBaseImpl<*>?, lastCell: Boolean, rowLabel: Boolean): Int {
    if (cell == null) {
      return 0
    }

    val rightGap = cell.rightGap
    if (lastCell) {
      if (rightGap != null) {
        warn("Right gap is set for last cell and will be ignored: rightGap = $rightGap")
      }
      return 0
    }

    if (rightGap != null) {
      return when (rightGap) {
        RightGap.SMALL -> dialogPanelConfig.spacing.horizontalSmallGap
        RightGap.COLUMNS -> dialogPanelConfig.spacing.horizontalColumnsGap
      }
    }

    return if (rowLabel) dialogPanelConfig.spacing.horizontalSmallGap else dialogPanelConfig.spacing.horizontalDefaultGap
  }


  /**
   * Appends comment (currently one comment for a row is supported, can be fixed later)
   */
  private fun buildCommentRow(cells: List<CellBaseImpl<*>?>,
                              firstCellIndent: Int,
                              maxColumnsCount: Int,
                              builder: RowsGridBuilder) {
    val commentedCellIndex = getCommentedCellIndex(cells)
    if (commentedCellIndex < 0) {
      return
    }

    val cell = cells[commentedCellIndex]
    val leftIndent = getAdditionalHorizontalIndent(cell) +
                     if (commentedCellIndex == 0) firstCellIndent else 0
    val gaps = Gaps(left = leftIndent, bottom = dialogPanelConfig.spacing.commentBottomGap)
    builder.skip(commentedCellIndex)
    builder.cell((cell as? CellImpl<*>)!!.comment!!, maxColumnsCount - commentedCellIndex, gaps = gaps)
    builder.row()
    return
  }

  private fun getAdditionalHorizontalIndent(cell: CellBaseImpl<*>?): Int {
    return if (cell is CellImpl<*> && cell.viewComponent is JToggleButton)
      dialogPanelConfig.spacing.horizontalToggleButtonIndent
    else
      0
  }

  private fun getCommentedCellIndex(cells: List<CellBaseImpl<*>?>): Int {
    return cells.indexOfFirst { (it as? CellImpl<*>)?.comment != null }
  }

  private fun getRowGaps(row: RowImpl): VerticalGaps {
    row.customRowGaps?.let {
      return it
    }

    val top = when (row.topGap) {
      TopGap.SMALL -> dialogPanelConfig.spacing.verticalSmallGap
      TopGap.MEDIUM -> dialogPanelConfig.spacing.verticalMediumGap
      null -> row.internalTopGap
    }

    val bottom = when (row.bottomGap) {
      BottomGap.SMALL -> dialogPanelConfig.spacing.verticalSmallGap
      BottomGap.MEDIUM -> dialogPanelConfig.spacing.verticalMediumGap
      null -> row.internalBottomGap
    }

    return if (top > 0 || bottom > 0) VerticalGaps(top = top, bottom = bottom) else VerticalGaps.EMPTY
  }

  private fun warn(message: String) {
    if (FAIL_ON_WARN) {
      throw UiDslException(message)
    }
    else {
      LOG.warn(message)
    }
  }
}
