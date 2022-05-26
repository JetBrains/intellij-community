// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import org.jetbrains.annotations.ApiStatus
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JRadioButton
import javax.swing.JToggleButton
import kotlin.math.min

@ApiStatus.Internal
internal class PanelBuilder(val rows: List<RowImpl>, val dialogPanelConfig: DialogPanelConfig, val panel: DialogPanel, val grid: Grid) {

  fun build() {
    if (rows.isEmpty()) {
      return
    }

    preprocess()

    val maxColumnsCount = getMaxColumnsCount()
    val rowsGridBuilder = RowsGridBuilder(panel, grid = grid)
      .defaultVerticalAlign(VerticalAlign.CENTER)
      .defaultBaselineAlign(true)
    val allRowsGaps = getRowsGaps(rows)
    for ((i, row) in rows.withIndex()) {
      if (!checkRow(row)) {
        continue
      }

      val rowGaps = allRowsGaps[i]
      rowsGridBuilder.setRowGaps(VerticalGaps(top = rowGaps.top))
      val subRowVerticalAlign = if (row.resizableRow) VerticalAlign.FILL else VerticalAlign.CENTER

      when (row.rowLayout) {
        RowLayout.INDEPENDENT -> {
          val subGridBuilder = rowsGridBuilder.subGridBuilder(width = maxColumnsCount,
            horizontalAlign = HorizontalAlign.FILL,
            verticalAlign = subRowVerticalAlign,
            gaps = Gaps(left = row.getIndent()))
          val cells = row.cells

          buildLabelRow(cells, 0, cells.size, row.rowLayout, subGridBuilder)

          subGridBuilder.resizableRow()
          buildRow(cells, 0, cells.size, panel, subGridBuilder)
          subGridBuilder.row()

          buildCommentRow(cells, 0, cells.size, row.rowLayout, subGridBuilder)
          setLastColumnResizable(subGridBuilder)
          if (row.resizableRow) {
            rowsGridBuilder.resizableRow()
          }
          rowsGridBuilder.row()
        }

        RowLayout.LABEL_ALIGNED -> {
          buildLabelRow(row.cells, row.getIndent(), maxColumnsCount, row.rowLayout, rowsGridBuilder)

          buildCell(row.cells[0], isLabelGap(row.cells.getOrNull(1)), row.getIndent(), row.cells.size == 1, 1, panel, rowsGridBuilder)

          if (row.cells.size > 1) {
            val subGridBuilder = rowsGridBuilder.subGridBuilder(width = maxColumnsCount - 1,
              horizontalAlign = HorizontalAlign.FILL,
              verticalAlign = subRowVerticalAlign)
              .resizableRow()
            val cells = row.cells.subList(1, row.cells.size)
            buildRow(cells, 0, cells.size, panel, subGridBuilder)
            setLastColumnResizable(subGridBuilder)
          }
          if (row.resizableRow) {
            rowsGridBuilder.resizableRow()
          }
          rowsGridBuilder.row()

          buildCommentRow(row.cells, row.getIndent(), maxColumnsCount, row.rowLayout, rowsGridBuilder)
        }

        RowLayout.PARENT_GRID -> {
          buildLabelRow(row.cells, row.getIndent(), maxColumnsCount, row.rowLayout, rowsGridBuilder)

          buildRow(row.cells, row.getIndent(), maxColumnsCount, panel, rowsGridBuilder)
          if (row.resizableRow) {
            rowsGridBuilder.resizableRow()
          }
          rowsGridBuilder.row()

          buildCommentRow(row.cells, row.getIndent(), maxColumnsCount, row.rowLayout, rowsGridBuilder)
        }
      }

      row.rowComment?.let {
        val gaps = Gaps(left = row.getIndent(), bottom = dialogPanelConfig.spacing.verticalComponentGap)
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
            if (cell.labelPosition == LabelPosition.LEFT) {
              val labelCell = CellImpl(dialogPanelConfig, it, row)
              row.cells.add(i, labelCell)
              i++
            }

            if (isAllowedLabel(cell)) {
              labelCell(it, cell)
            }
            else {
              warn("Unsupported labeled component: ${cell.component.javaClass.name}")
            }
          }
        }

        i++
      }
    }
  }

  /**
   * According to https://jetbrains.design/intellij/principles/layout/#checkboxes-and-radio-buttons
   * space between label and CheckBox/RadioButton should be increased
   */
  private fun isLabelGap(cellAfterLabel: CellBaseImpl<*>?): Boolean {
    val component = (cellAfterLabel as? CellImpl<*>)?.component
    return !(component is JCheckBox || component is JRadioButton)
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
                       firstCellIndent: Int,
                       maxColumnsCount: Int,
                       panel: DialogPanel,
                       builder: RowsGridBuilder) {
    for ((cellIndex, cell) in cells.withIndex()) {
      val lastCell = cellIndex == cells.size - 1
      val width = if (lastCell) maxColumnsCount - cellIndex else 1
      val leftGap = if (cellIndex == 0) firstCellIndent else 0
      val isLabel = cell is CellImpl<*> && (cell.component.getClientProperty(DslComponentProperty.ROW_LABEL) == true ||
                                            cell.component.getClientProperty(DslComponentPropertyInternal.CELL_LABEL) == true)

      buildCell(cell, isLabel && isLabelGap(cells.getOrNull(cellIndex + 1)), leftGap, lastCell, width, panel, builder)
    }
  }

  private fun buildCell(cell: CellBaseImpl<*>?, isLabelGap: Boolean, leftGap: Int, lastCell: Boolean, width: Int,
                        panel: DialogPanel, builder: RowsGridBuilder) {
    val rightGap = getRightGap(cell, lastCell, isLabelGap)

    when (cell) {
      is CellImpl<*> -> {
        val gaps = cell.customGaps ?: getComponentGaps(leftGap, rightGap, cell.component, dialogPanelConfig.spacing)
        builder.cell(cell.viewComponent, width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
          resizableColumn = cell.resizableColumn,
          gaps = gaps, visualPaddings = getVisualPaddings(cell.viewComponent.origin),
          widthGroup = cell.widthGroup)
      }
      is PanelImpl -> {
        // todo visualPaddings
        val gaps = cell.customGaps ?: Gaps(left = leftGap, right = rightGap)
        val subGrid = builder.subGrid(width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                                      resizableColumn = cell.resizableColumn, gaps = gaps)

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
      is PlaceholderBaseImpl -> {
        val gaps = cell.customGaps ?: Gaps(left = leftGap, right = rightGap)
        if (cell.resizableColumn) {
          builder.addResizableColumn()
        }
        val constraints = builder.constraints(width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                                              gaps = gaps)
        cell.init(panel, constraints, dialogPanelConfig.spacing)
      }
      null -> {
        builder.skip(1)
      }
    }
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

  private fun getRightGap(cell: CellBaseImpl<*>?, lastCell: Boolean, isLabelGap: Boolean): Int {
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

    return if (isLabelGap) dialogPanelConfig.spacing.horizontalSmallGap else dialogPanelConfig.spacing.horizontalDefaultGap
  }


  /**
   * Appends row with cell labels, which are marked as [LabelPosition.TOP]
   */
  private fun buildLabelRow(cells: List<CellBaseImpl<*>?>,
                            firstCellIndent: Int,
                            maxColumnsCount: Int,
                            layout: RowLayout,
                            builder: RowsGridBuilder) {
    val columnsAndLabels = cells.mapIndexedNotNull { index, cell ->
      val cellImpl = cell as? CellImpl<*>
      val label = cellImpl?.label
      if (label == null || cellImpl.labelPosition != LabelPosition.TOP ||
          (layout == RowLayout.LABEL_ALIGNED && index > 1)) {
        null
      }
      else {
        val left = if (index == 0) firstCellIndent else 0
        GeneratedComponentData(label, Gaps(top = getDefaultVerticalGap(label, dialogPanelConfig.spacing), left = left), index)
      }
    }

    buildRow(columnsAndLabels, maxColumnsCount, VerticalAlign.BOTTOM, builder)
  }

  /**
   * Appends row with cell comments
   */
  private fun buildCommentRow(cells: List<CellBaseImpl<*>?>,
                              firstCellIndent: Int,
                              maxColumnsCount: Int,
                              layout: RowLayout,
                              builder: RowsGridBuilder) {
    var columnsAndComments = cells.mapIndexedNotNull { index, cell ->
      val cellImpl = cell as? CellImpl<*>
      val comment = cellImpl?.comment
      if (comment == null) {
        null
      }
      else {
        val left = getAdditionalHorizontalIndent(cell) + (if (index == 0) firstCellIndent else 0)
        GeneratedComponentData(comment, Gaps(left = left, bottom = dialogPanelConfig.spacing.verticalComponentGap), index)
      }
    }

    // LABEL_ALIGNED: Always put comment for cells with index more than 1 at second cell because it's hard to implement
    // more correct behaviour now. Can be fixed later
    if (layout == RowLayout.LABEL_ALIGNED) {
      val index = columnsAndComments.indexOfFirst { it.column >= 1 }
      if (index >= 0) {
        val mutableColumnsAndComments = columnsAndComments.subList(0, index + 1).toMutableList()
        val lastData = mutableColumnsAndComments[index]
        if (lastData.column > 1) {
          mutableColumnsAndComments[index] = lastData.copy(column = 1, gaps = lastData.gaps.copy(left = 0))
        }
        columnsAndComments = mutableColumnsAndComments
      }
    }

    buildRow(columnsAndComments, maxColumnsCount, VerticalAlign.TOP, builder)
  }

  /**
   * Builds row with provided components from [columnsAndComponents]
   */
  private fun buildRow(columnsAndComponents: List<GeneratedComponentData>,
                       maxColumnsCount: Int,
                       verticalAlign: VerticalAlign,
                       builder: RowsGridBuilder) {
    if (columnsAndComponents.isEmpty()) {
      return
    }

    builder.skip(columnsAndComponents[0].column)

    for ((i, data) in columnsAndComponents.withIndex()) {
      val nextColumn = if (i + 1 < columnsAndComponents.size) columnsAndComponents[i + 1].column else maxColumnsCount
      builder.cell(data.component, nextColumn - data.column, verticalAlign = verticalAlign, baselineAlign = false, gaps = data.gaps)

    }
    builder.row()
  }

  private fun getAdditionalHorizontalIndent(cell: CellBaseImpl<*>?): Int {
    return if (cell is CellImpl<*> && cell.viewComponent is JToggleButton)
      dialogPanelConfig.spacing.horizontalToggleButtonIndent
    else
      0
  }

  private fun getRowsGaps(rows: List<RowImpl>): List<VerticalGaps> {
    val result = mutableListOf<VerticalGaps>()

    for ((i, row) in rows.withIndex()) {
      val rowGaps = getRowGaps(row, i == 0, i == rows.size - 1)
      result.add(rowGaps)

      // Only greatest gap of top and bottom gaps is used between two rows (or top gap if equal)
      if (i > 0) {
        val previousRowGaps = result[i - 1]
        if (previousRowGaps.bottom != 0 && rowGaps.top != 0) {
          if (previousRowGaps.bottom > rowGaps.top) {
            result[i] = rowGaps.copy(top = 0)
          }
          else {
            result[i - 1] = previousRowGaps.copy(bottom = 0)
          }
        }
      }
    }

    return result
  }

  private fun getRowGaps(row: RowImpl, first: Boolean, last: Boolean): VerticalGaps {
    val top = when (row.topGap) {
      TopGap.NONE -> 0
      TopGap.SMALL -> dialogPanelConfig.spacing.verticalSmallGap
      TopGap.MEDIUM -> dialogPanelConfig.spacing.verticalMediumGap
      null -> if (first) 0 else row.internalTopGap
    }

    val bottom = when (row.bottomGap) {
      BottomGap.NONE -> 0
      BottomGap.SMALL -> dialogPanelConfig.spacing.verticalSmallGap
      BottomGap.MEDIUM -> dialogPanelConfig.spacing.verticalMediumGap
      null -> if (last) 0 else row.internalBottomGap
    }

    return if (top > 0 || bottom > 0) VerticalGaps(top = top, bottom = bottom) else VerticalGaps.EMPTY
  }
}

private data class GeneratedComponentData(val component: JComponent, val gaps: Gaps, val column: Int)
