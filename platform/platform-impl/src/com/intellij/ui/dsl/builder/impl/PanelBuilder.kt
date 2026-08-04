// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.gridLayout.Grid
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JRadioButton
import javax.swing.JToggleButton
import kotlin.math.min

/**
 * Turns a form - see [GridFormRow] - into the cells of a grid, through [rowsGridBuilder].
 *
 * The rules are the same whatever described the form, so a page written in `panel { }` and a page written in
 * Compose are laid out alike. [buildGridForm] is how either of them gets here.
 */
internal class PanelBuilder(val rows: List<GridFormRow>,
                            private val spacingConfiguration: SpacingConfiguration,
                            private val rowsGridBuilder: RowsGridBuilder) {

  companion object {
    val log = thisLogger()
  }

  fun build() {
    if (rows.isEmpty()) {
      return
    }

    val maxColumnsCount = getMaxColumnsCount()
    rowsGridBuilder.defaultVerticalAlign(VerticalAlign.CENTER).defaultBaselineAlign(true)
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
            gaps = UnscaledGaps(left = row.indent))
          val cells = row.cells

          buildLabelRow(cells, 0, cells.size, row.rowLayout, subGridBuilder)

          subGridBuilder.resizableRow()
          buildRow(cells, 0, cells.size, subGridBuilder)
          subGridBuilder.row()

          buildCommentRow(cells, 0, cells.size, row.rowLayout, subGridBuilder)
          setLastColumnResizable(subGridBuilder)
          if (row.resizableRow) {
            rowsGridBuilder.resizableRow()
          }
          rowsGridBuilder.row()
        }

        RowLayout.LABEL_ALIGNED -> {
          buildLabelRow(row.cells, row.indent, maxColumnsCount, row.rowLayout, rowsGridBuilder)

          buildCell(row.cells[0], isLabelGap(row.cells.getOrNull(1)), row.indent, row.cells.size == 1, 1, rowsGridBuilder)

          if (row.cells.size > 1) {
            val subGridBuilder = rowsGridBuilder.subGridBuilder(width = maxColumnsCount - 1,
              horizontalAlign = HorizontalAlign.FILL,
              verticalAlign = subRowVerticalAlign)
              .resizableRow()
            val cells = row.cells.subList(1, row.cells.size)
            buildRow(cells, 0, cells.size, subGridBuilder)
            setLastColumnResizable(subGridBuilder)
          }
          if (row.resizableRow) {
            rowsGridBuilder.resizableRow()
          }
          rowsGridBuilder.row()

          buildCommentRow(row.cells, row.indent, maxColumnsCount, row.rowLayout, rowsGridBuilder)
        }

        RowLayout.PARENT_GRID -> {
          buildLabelRow(row.cells, row.indent, maxColumnsCount, row.rowLayout, rowsGridBuilder)

          buildRow(row.cells, row.indent, maxColumnsCount, rowsGridBuilder)
          if (row.resizableRow) {
            rowsGridBuilder.resizableRow()
          }
          rowsGridBuilder.row()

          buildCommentRow(row.cells, row.indent, maxColumnsCount, row.rowLayout, rowsGridBuilder)
        }
      }

      row.rowComment?.let {
        val gaps = UnscaledGaps(left = row.indent, bottom = spacingConfiguration.verticalComponentGap)
        val horizontalAlign = if (it.maxLineLength == MAX_LINE_LENGTH_WORD_WRAP) HorizontalAlign.FILL else HorizontalAlign.LEFT
        rowsGridBuilder.cell(it, maxColumnsCount, gaps = gaps, horizontalAlign = horizontalAlign)
        rowsGridBuilder.row()
      }

      val rowsGaps = rowsGridBuilder.grid.rowsGaps
      rowsGaps[rowsGaps.size - 2] = rowsGaps[rowsGaps.size - 2].copy(bottom = rowGaps.bottom)
    }

    setLastColumnResizable(rowsGridBuilder)
    checkNoDoubleRowGaps(rowsGridBuilder.grid)
  }

  /**
   * According to https://plugins.jetbrains.com/docs/intellij/layout.html#checkboxes-and-radio-buttons
   * space between label and CheckBox/RadioButton should be increased
   */
  private fun isLabelGap(cellAfterLabel: GridFormCell?): Boolean {
    val component = (cellAfterLabel as? GridFormComponentCell)?.component
    return !(component is JCheckBox || component is JRadioButton)
  }

  private fun setLastColumnResizable(builder: RowsGridBuilder) {
    if (builder.resizableColumns.isEmpty() && builder.columnsCount > 0) {
      builder.resizableColumns.add(builder.columnsCount - 1)
    }
  }

  private fun checkRow(row: GridFormRow): Boolean {
    if (row.cells.isEmpty()) {
      errorInInternalOrLogWarn("Row should not be empty", row.creationStackTrace)
      return false
    }

    return true
  }

  private fun checkNoDoubleRowGaps(grid: Grid) {
    val gaps = grid.rowsGaps
    for (i in gaps.indices) {
      if (i > 0 && gaps[i - 1].bottom > 0 && gaps[i].top > 0) {
        errorInInternalOrLogWarn("There is double gap between two near rows")
      }
    }
  }

  private fun buildRow(cells: List<GridFormCell?>,
                       firstCellIndent: Int,
                       maxColumnsCount: Int,
                       builder: RowsGridBuilder) {
    for ((cellIndex, cell) in cells.withIndex()) {
      val lastCell = cellIndex == cells.size - 1
      val width = if (lastCell) maxColumnsCount - cellIndex else 1
      val leftGap = if (cellIndex == 0) firstCellIndent else 0
      val isLabel = cell is GridFormComponentCell && (cell.component.getClientProperty(DslComponentProperty.ROW_LABEL) == true ||
                                                      cell.component.getClientProperty(DslComponentPropertyInternal.CELL_LABEL) == true)

      buildCell(cell, isLabel && isLabelGap(cells.getOrNull(cellIndex + 1)), leftGap, lastCell, width, builder)
    }
  }

  private fun buildCell(cell: GridFormCell?, isLabelGap: Boolean, leftGap: Int, lastCell: Boolean, width: Int,
                        builder: RowsGridBuilder) {
    val rightGap = getRightGap(cell, lastCell, isLabelGap)

    when (cell) {
      is GridFormComponentCell -> {
        val gaps = cell.customGaps ?: getComponentGaps(leftGap, rightGap, cell.component, spacingConfiguration)
        val commentRight = cell.commentRight
        val contextHelpLabel = cell.contextHelpLabel

        if (commentRight == null && contextHelpLabel == null) {
          builder.cell(cell.viewComponent, width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                       resizableColumn = cell.resizableColumn,
                       gaps = gaps, visualPaddings = prepareVisualPaddings(cell.viewComponent),
                       widthGroup = cell.widthGroup)
        } else {
          if (cell.verticalAlign == VerticalAlign.FILL) {
            log.error("Vertical align FILL is not supported for cells with right comment or context help, commentRight = " +
                      "${commentRight?.userText}, contextHelp = ${cell.contextHelpDescription}")
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

          if (contextHelpLabel != null) {
            subGridBuilder.cell(contextHelpLabel, gaps = UnscaledGaps(left = spacingConfiguration.horizontalSmallGap))
          }

          if (commentRight != null) {
            subGridBuilder.cell(commentRight, gaps = UnscaledGaps(left = spacingConfiguration.horizontalCommentGap))
          }
        }
      }
      is GridFormPanelCell -> {
        // todo visualPaddings
        val gaps = cell.customGaps ?: UnscaledGaps(left = leftGap, right = rightGap)
        val subGridBuilder = builder.subGridBuilder(width = width, horizontalAlign = cell.horizontalAlign,
                                                    verticalAlign = cell.verticalAlign,
                                                    resizableColumn = cell.resizableColumn, gaps = gaps)

        val subBuilder = PanelBuilder(cell.rows, cell.spacingConfiguration, subGridBuilder)
        subBuilder.build()
      }
      is GridFormDeferredCell -> {
        val gaps = cell.customGaps ?: UnscaledGaps(left = leftGap, right = rightGap)
        if (cell.resizableColumn) {
          builder.addResizableColumn()
        }
        val constraints = builder.constraints(width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                                              gaps = gaps)
        cell.place(constraints)
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

  private fun getRightGap(cell: GridFormCell?, lastCell: Boolean, isLabelGap: Boolean): Int {
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
  private fun buildLabelRow(cells: List<GridFormCell?>,
                            firstCellIndent: Int,
                            maxColumnsCount: Int,
                            layout: RowLayout,
                            builder: RowsGridBuilder) {
    val columnsAndLabels = cells.mapIndexedNotNull { index, cell ->
      val componentCell = cell as? GridFormComponentCell
      val label = componentCell?.label
      if (label == null || componentCell.labelPosition != LabelPosition.TOP ||
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
  private fun buildCommentRow(cells: List<GridFormCell?>,
                              firstCellIndent: Int,
                              maxColumnsCount: Int,
                              layout: RowLayout,
                              builder: RowsGridBuilder) {
    var columnsAndComments = cells.mapIndexedNotNull { index, cell ->
      val componentCell = cell as? GridFormComponentCell
      val comment = componentCell?.comment
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

    builder.setRowGaps(UnscaledGapsY(bottom = spacingConfiguration.verticalCommentBottomGap))
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

  private fun getAdditionalHorizontalIndent(cell: GridFormCell?): Int {
    return if (cell is GridFormComponentCell && cell.viewComponent is JToggleButton)
      spacingConfiguration.horizontalToggleButtonIndent
    else
      0
  }

  private fun getRowsGaps(rows: List<GridFormRow>): List<UnscaledGapsY> {
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

  private fun getRowGaps(row: GridFormRow, first: Boolean, last: Boolean): UnscaledGapsY {
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
