// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.diagnostic.thisLogger
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
internal class PanelBuilder(val rows: List<RowImpl>, private val dialogPanelConfig: DialogPanelConfig,
                            private val spacingConfiguration: SpacingConfiguration,
                            val panel: DialogPanel, val grid: Grid) {

  companion object {
    val log = thisLogger()
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
    val allRowsGaps = getRowsGaps(rows)
    for ((i, row) in rows.withIndex()) {
      if (!checkRow(row)) {
        continue
      }

      val rowGaps = allRowsGaps[i]
      rowsGridBuilder.setRowGaps(UnscaledGapsY(top = rowGaps.top))
      val subRowVerticalAlign = if (row.resizableRow) VerticalAlign.FILL else VerticalAlign.CENTER

      when (row.rowLayout) {
        RowLayout.INDEPENDENT -> {
          val subGridBuilder = rowsGridBuilder.subGridBuilder(width = maxColumnsCount,
            horizontalAlign = HorizontalAlign.FILL,
            verticalAlign = subRowVerticalAlign,
            gaps = UnscaledGaps(left = row.getIndent()))
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
        val gaps = UnscaledGaps(left = row.getIndent(), bottom = spacingConfiguration.verticalComponentGap)
        val horizontalAlign = if (it.maxLineLength == MAX_LINE_LENGTH_WORD_WRAP) HorizontalAlign.FILL else HorizontalAlign.LEFT
        rowsGridBuilder.cell(it, maxColumnsCount, gaps = gaps, horizontalAlign = horizontalAlign)
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

            labelCell(it, cell)
          }
        }

        i++
      }
    }
  }

  /**
   * According to https://plugins.jetbrains.com/docs/intellij/layout.html#checkboxes-and-radio-buttons
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
        val gaps = cell.customGaps ?: getComponentGaps(leftGap, rightGap, cell.component, spacingConfiguration)
        val commentRight = cell.commentRight

        if (commentRight == null) {
          builder.cell(cell.viewComponent, width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                       resizableColumn = cell.resizableColumn,
                       gaps = gaps, visualPaddings = prepareVisualPaddings(cell.viewComponent),
                       widthGroup = cell.widthGroup)
        } else {
          if (cell.verticalAlign == VerticalAlign.FILL) {
            log.error("Vertical align FILL is not supported for cells with right comment, commentRight = ${commentRight.userText}")
          }

          val subGridBuilder = builder.subGridBuilder(width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                                                      resizableColumn = cell.resizableColumn,
                                                      gaps = gaps)
          val isHorizontalFill = cell.horizontalAlign == HorizontalAlign.FILL
          subGridBuilder.cell(cell.viewComponent,
                              horizontalAlign = if (isHorizontalFill) HorizontalAlign.FILL else HorizontalAlign.LEFT,
                              verticalAlign = if (cell.verticalAlign == VerticalAlign.FILL) VerticalAlign.FILL else VerticalAlign.CENTER,
                              resizableColumn = isHorizontalFill,
                              visualPaddings = prepareVisualPaddings(cell.viewComponent),
                              widthGroup = cell.widthGroup)
          subGridBuilder.cell(commentRight, gaps = UnscaledGaps(left = spacingConfiguration.horizontalCommentGap))
        }
      }
      is PanelImpl -> {
        // todo visualPaddings
        val gaps = cell.customGaps ?: UnscaledGaps(left = leftGap, right = rightGap)
        val subGrid = builder.subGrid(width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                                      resizableColumn = cell.resizableColumn, gaps = gaps)

        val subBuilder = PanelBuilder(cell.rows, dialogPanelConfig, cell.spacingConfiguration, panel, subGrid)
        subBuilder.build()
      }
      is PlaceholderBaseImpl -> {
        val gaps = cell.customGaps ?: UnscaledGaps(left = leftGap, right = rightGap)
        if (cell.resizableColumn) {
          builder.addResizableColumn()
        }
        val constraints = builder.constraints(width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                                              gaps = gaps)
        cell.init(panel, constraints, spacingConfiguration)
      }
      // todo revert back to null after migrating to Kotlin 1.7.21, see KT-45474
      else -> {
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
      // Right gap is ignored for the last cell in a row
      return 0
    }

    if (rightGap != null) {
      return when (rightGap) {
        RightGap.SMALL -> spacingConfiguration.horizontalSmallGap
        RightGap.COLUMNS -> spacingConfiguration.horizontalColumnsGap
      }
    }

    return if (isLabelGap) spacingConfiguration.horizontalSmallGap else spacingConfiguration.horizontalDefaultGap
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
        GeneratedComponentData(label, UnscaledGaps(top = spacingConfiguration.verticalComponentGap, left = left), HorizontalAlign.LEFT, index)
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
        val horizontalAlign = if (comment.maxLineLength == MAX_LINE_LENGTH_WORD_WRAP) HorizontalAlign.FILL else HorizontalAlign.LEFT
        GeneratedComponentData(comment, UnscaledGaps(left = left, bottom = spacingConfiguration.verticalComponentGap), horizontalAlign, index)
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
      builder.cell(data.component, nextColumn - data.column, horizontalAlign = data.horizontalAlign, verticalAlign = verticalAlign,
                   baselineAlign = false, gaps = data.gaps)

    }
    builder.row()
  }

  private fun getAdditionalHorizontalIndent(cell: CellBaseImpl<*>?): Int {
    return if (cell is CellImpl<*> && cell.viewComponent is JToggleButton)
      spacingConfiguration.horizontalToggleButtonIndent
    else
      0
  }

  private fun getRowsGaps(rows: List<RowImpl>): List<UnscaledGapsY> {
    val result = mutableListOf<UnscaledGapsY>()

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

  private fun getRowGaps(row: RowImpl, first: Boolean, last: Boolean): UnscaledGapsY {
    row.customGaps?.let {
      return it
    }

    val top = when (row.topGap) {
      TopGap.NONE -> 0
      TopGap.SMALL -> spacingConfiguration.verticalSmallGap
      TopGap.MEDIUM -> spacingConfiguration.verticalMediumGap
      null -> if (first) 0 else row.internalGaps.top
    }

    val bottom = when (row.bottomGap) {
      BottomGap.NONE -> 0
      BottomGap.SMALL -> spacingConfiguration.verticalSmallGap
      BottomGap.MEDIUM -> spacingConfiguration.verticalMediumGap
      null -> if (last) 0 else row.internalGaps.bottom
    }

    return if (top > 0 || bottom > 0) UnscaledGapsY(top = top, bottom = bottom) else UnscaledGapsY.EMPTY
  }
}

private data class GeneratedComponentData(val component: JComponent,
                                          val gaps: UnscaledGaps,
                                          val horizontalAlign: HorizontalAlign,
                                          val column: Int)
