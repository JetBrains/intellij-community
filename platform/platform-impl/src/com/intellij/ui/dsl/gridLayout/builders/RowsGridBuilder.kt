// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout.builders

import com.intellij.ui.dsl.gridLayout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import kotlin.math.max

private const val GRID_EMPTY = -1

/**
 * Builds grid layout row by row
 */
@ApiStatus.Experimental
class RowsGridBuilder(private val panel: JComponent, grid: JBGrid? = null) {

  private val layout = panel.layout as JBGridLayout

  val grid = grid ?: layout.rootGrid

  var columnsCount: Int = 0
    private set

  val resizableColumns: MutableSet<Int> by this.grid::resizableColumns

  var defaultHorizontalAlign = HorizontalAlign.LEFT
    private set

  var defaultVerticalAlign = VerticalAlign.CENTER
    private set

  var defaultBaselineAlign = false
    private set

  private var x = 0
  private var y = GRID_EMPTY

  fun columnsGaps(value: List<ColumnGaps>): RowsGridBuilder {
    grid.columnsGaps.clear()
    grid.columnsGaps.addAll(value)
    return this
  }

  /**
   * Starts new row. Can be omitted for the first and last rows
   *
   * @param resizable true if new row is resizable
   */
  fun row(rowGaps: RowGaps = RowGaps.EMPTY, resizable: Boolean = false): RowsGridBuilder {
    x = 0
    y++

    setRowGaps(rowGaps)

    if (resizable) {
      addResizableRow()
    }

    return this
  }

  fun cell(component: JComponent,
           width: Int = 1,
           horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
           verticalAlign: VerticalAlign = defaultVerticalAlign,
           baselineAlign: Boolean = defaultBaselineAlign,
           resizableColumn: Boolean = false,
           gaps: Gaps = Gaps.EMPTY,
           visualPaddings: Gaps = Gaps.EMPTY): RowsGridBuilder {
    if (y == GRID_EMPTY) {
      y = 0
    }
    if (resizableColumn) {
      addResizableColumn()
    }

    val constraints = JBConstraints(grid, x, y, width = width, horizontalAlign = horizontalAlign,
      verticalAlign = verticalAlign, baselineAlign = baselineAlign,
      gaps = gaps, visualPaddings = visualPaddings)
    panel.add(component, constraints)
    return skip(width)
  }

  fun subGrid(width: Int = 1,
              horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
              verticalAlign: VerticalAlign = defaultVerticalAlign,
              baselineAlign: Boolean = defaultBaselineAlign,
              resizableColumn: Boolean = false,
              gaps: Gaps = Gaps.EMPTY,
              visualPaddings: Gaps = Gaps.EMPTY): JBGrid {
    startFirstRow()
    if (resizableColumn) {
      addResizableColumn()
    }

    val constraints = JBConstraints(grid, x, y, width = width, horizontalAlign = horizontalAlign,
      verticalAlign = verticalAlign, baselineAlign = baselineAlign,
      gaps = gaps, visualPaddings = visualPaddings)
    skip(width)
    return layout.addLayoutSubGrid(constraints)
  }

  fun subGridBuilder(width: Int = 1,
                     horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
                     verticalAlign: VerticalAlign = defaultVerticalAlign,
                     baselineAlign: Boolean = defaultBaselineAlign,
                     resizableColumn: Boolean = false,
                     gaps: Gaps = Gaps.EMPTY,
                     visualPaddings: Gaps = Gaps.EMPTY): RowsGridBuilder {
    return RowsGridBuilder(panel, subGrid(width, horizontalAlign, verticalAlign, baselineAlign, resizableColumn, gaps, visualPaddings))
      .defaultHorizontalAlign(defaultHorizontalAlign)
      .defaultVerticalAlign(defaultVerticalAlign)
      .defaultBaselineAlign(defaultBaselineAlign)
  }

  /**
   * Skips [count] cells in current row
   */
  fun skip(count: Int = 1): RowsGridBuilder {
    x += count
    columnsCount = max(columnsCount, x)
    return this
  }

  fun setRowGaps(rowGaps: RowGaps) {
    startFirstRow()

    while (grid.rowsGaps.size <= y) {
      grid.rowsGaps.add(RowGaps.EMPTY)
    }
    grid.rowsGaps[y] = rowGaps
  }

  fun defaultHorizontalAlign(defaultHorizontalAlign: HorizontalAlign): RowsGridBuilder {
    this.defaultHorizontalAlign = defaultHorizontalAlign
    return this
  }

  fun defaultVerticalAlign(defaultVerticalAlign: VerticalAlign): RowsGridBuilder {
    this.defaultVerticalAlign = defaultVerticalAlign
    return this
  }

  fun defaultBaselineAlign(defaultBaselineAlign: Boolean): RowsGridBuilder {
    this.defaultBaselineAlign = defaultBaselineAlign
    return this
  }

  private fun addResizableColumn() {
    grid.resizableColumns += x
  }

  private fun addResizableRow() {
    grid.resizableRows += y
  }

  private fun startFirstRow() {
    if (y == GRID_EMPTY) {
      y = 0
    }
  }
}
