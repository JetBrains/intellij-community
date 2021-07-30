// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.gridLayout.builders

import com.intellij.util.ui.gridLayout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Builds grid layout row by row
 */
@ApiStatus.Experimental
class RowsGridBuilder(private val panel: JComponent, private val grid: JBGrid = (panel.layout as JBGridLayout).rootGrid) {

  private var x = 0
  private var y = 0

  fun resizableColumns(vararg columns: Int): RowsGridBuilder {
    grid.resizableColumns = columns.toSet()
    return this
  }

  fun resizableRows(vararg rows: Int): RowsGridBuilder {
    grid.resizableRows = rows.toSet()
    return this
  }

  fun row(): RowsGridBuilder {
    x = 0
    y++
    return this
  }

  fun cell(component: JComponent,
           width: Int = 1,
           horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,
           verticalAlign: VerticalAlign = VerticalAlign.TOP,
           gaps: Gaps = Gaps.EMPTY,
           visualPaddings: Gaps = Gaps.EMPTY): RowsGridBuilder {
    val constraints = JBConstraints(grid, x, y, width = width, verticalAlign = verticalAlign, horizontalAlign = horizontalAlign,
                                    gaps = gaps, visualPaddings = visualPaddings)
    panel.add(component, constraints)
    return skipCell()
  }

  fun skipCell(): RowsGridBuilder {
    x++
    return this
  }
}
