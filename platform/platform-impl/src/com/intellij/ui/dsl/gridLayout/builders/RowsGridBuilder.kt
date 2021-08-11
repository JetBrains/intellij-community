// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout.builders

import com.intellij.ui.dsl.gridLayout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import kotlin.math.max

/**
 * Builds grid layout row by row
 */
@ApiStatus.Experimental
class RowsGridBuilder(private val panel: JComponent, grid: JBGrid? = null) {

  private val GRID_EMPTY = -1
  private val layout = panel.layout as JBGridLayout
  private val grid = grid ?: layout.rootGrid
  private var x = 0
  private var y = GRID_EMPTY

  var columnsCount: Int = 0
    private set
  var resizableColumns: Set<Int> by this.grid::resizableColumns

  fun columnsDistance(value: List<Int>): RowsGridBuilder {
    grid.columnsDistance = value
    return this
  }

  /**
   * Starts new row. Can be omitted for the first and last rows
   *
   * @param distance distance between previous row and new one. Not used for the first row
   * @param resizable true if new row is resizable
   */
  fun row(distance: Int = 0, resizable: Boolean = false): RowsGridBuilder {
    if (distance < 0) {
      throw JBGridException("Invalid parameter: distance = $distance")
    }

    x = 0
    y++

    // Update rowsDistance
    if (y != GRID_EMPTY && distance > 0) {
      val rowsDistance = enlargeRowsDistance()
      rowsDistance[y - 1] = distance
    }


    if (resizable) {
      val resizableRows = grid.resizableRows.toMutableSet()
      resizableRows.add(y)
      grid.resizableRows = resizableRows
    }

    return this
  }

  fun cell(component: JComponent,
           width: Int = 1,
           horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,
           verticalAlign: VerticalAlign = VerticalAlign.TOP,
           resizableColumn: Boolean = false,
           gaps: Gaps = Gaps.EMPTY,
           visualPaddings: Gaps = Gaps.EMPTY): RowsGridBuilder {
    if (y == GRID_EMPTY) {
      y = 0
    }
    if (resizableColumn) {
      addResizableColumn()
    }

    val constraints = JBConstraints(grid, x, y, width = width, verticalAlign = verticalAlign, horizontalAlign = horizontalAlign,
                                    gaps = gaps, visualPaddings = visualPaddings)
    panel.add(component, constraints)
    return skip(width)
  }

  /**
   * Adds distance before current row. Cannot be used for the first row
   */
  fun addBeforeDistance(distance: Int) {
    val rowsDistance = enlargeRowsDistance()
    rowsDistance[y - 1] += distance
  }

  fun subGrid(width: Int = 1,
              horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,
              verticalAlign: VerticalAlign = VerticalAlign.TOP,
              resizableColumn: Boolean = false,
              gaps: Gaps = Gaps.EMPTY,
              visualPaddings: Gaps = Gaps.EMPTY): JBGrid {
    if (y == GRID_EMPTY) {
      y = 0
    }
    if (resizableColumn) {
      addResizableColumn()
    }

    val constraints = JBConstraints(grid, x, y, width = width, verticalAlign = verticalAlign, horizontalAlign = horizontalAlign,
                                    gaps = gaps, visualPaddings = visualPaddings)
    skip(width)
    return layout.addLayoutSubGrid(constraints)
  }

  /**
   * Skips [count] cells in current row
   */
  fun skip(count: Int = 1): RowsGridBuilder {
    x += count
    columnsCount = max(columnsCount, x)
    return this
  }

  private fun enlargeRowsDistance(): MutableList<Int> {
    val result = grid.rowsDistance.toMutableList()
    while (result.size <= y) {
      result.add(0)
    }
    grid.rowsDistance = result
    return result
  }

  private fun addResizableColumn() {
    val resizableColumns = grid.resizableColumns.toMutableSet()
    resizableColumns.add(x)
    grid.resizableColumns = resizableColumns
  }
}
