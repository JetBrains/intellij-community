// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

internal class JBGridImpl : JBGrid {

  override var resizableColumns = emptySet<Int>()
  override var resizableRows = emptySet<Int>()

  override var columnsDistance = emptyList<Int>()
  override var rowsDistance = emptyList<Int>()

  private val layoutData = JBLayoutData()
  private val cells = mutableListOf<JBCell>()

  fun register(component: JComponent, constraints: JBConstraints) {
    if (!isEmpty(constraints)) {
      throw JBGridException("Some cells are occupied already: $constraints")
    }

    cells.add(JBComponentCell(constraints, component))
  }

  fun registerSubGrid(constraints: JBConstraints): JBGrid {
    if (!isEmpty(constraints)) {
      throw JBGridException("Some cells are occupied already: $constraints")
    }

    val result = JBGridImpl()
    cells.add(JBGridCell(constraints, result))
    return result
  }

  fun unregister(component: JComponent) {
    val iterator = cells.iterator()
    for (cell in iterator) {
      if ((cell as? JBComponentCell)?.component == component) {
        iterator.remove()
        return
      }
    }

    throw JBGridException("Component has not been registered: $component")
  }

  fun getPreferredSize(): Dimension {
    calculateLayoutData()
    return layoutData.preferredSize
  }

  /**
   * Layouts components
   */
  fun layout(rect: Rectangle) {
    val columnsCoord = layoutData.columnsSizeCalculator.calculateCoords(rect.width, resizableColumns)
    val rowsCoord = layoutData.rowsSizeCalculator.calculateCoords(rect.height, resizableRows)

    layoutData.visibleCellsData.forEach { layoutCellData ->
      val cell = layoutCellData.cell
      val constraints = cell.constraints
      var visualX = columnsCoord[constraints.x]
      var visualY = rowsCoord[constraints.y]
      val nextColumn = constraints.x + constraints.width
      val nextRow = constraints.y + constraints.height
      val visualWidth = columnsCoord[nextColumn] - visualX - layoutCellData.gapWidth
      val visualHeight = rowsCoord[nextRow] - visualY - layoutCellData.gapHeight
      visualX += rect.x + constraints.gaps.left
      visualY += rect.y + constraints.gaps.top

      when (cell) {
        is JBComponentCell -> {
          layoutComponent(cell.component, layoutCellData, visualX, visualY, visualWidth, visualHeight)
        }
        is JBGridCell -> {
          (cell.content as JBGridImpl).layout(Rectangle(visualX, visualY, visualWidth, visualHeight))
        }
      }
    }
  }

  /**
   * Calculates all data in [layoutData], measures all components etc
   */
  fun calculateLayoutData() {
    layoutData.columnsSizeCalculator.reset()
    layoutData.rowsSizeCalculator.reset()

    calculateLayoutDataStep1()
    calculateLayoutDataStep2()
  }

  private fun calculateLayoutDataStep1() {
    layoutData.dimension = getDimension()
    val visibleCellsData = mutableListOf<LayoutCellData>()

    cells.forEach { cell ->
      when (cell) {
        is JBComponentCell -> {
          val component = cell.component
          if (component.isVisible) {
            visibleCellsData.add(LayoutCellData(cell, component.preferredSize))
          }
        }
        is JBGridCell -> {
          // todo visibility, visibleColumns and visibleRows
          val grid = cell.content as JBGridImpl
          grid.calculateLayoutData()
          visibleCellsData.add(LayoutCellData(cell, grid.layoutData.preferredSize))
        }
      }
    }

    layoutData.visibleCellsData = visibleCellsData
  }

  private fun calculateLayoutDataStep2() {
    fun isAfterColumnDistance(column: Int): Boolean {
      return column < columnsDistance.size &&
             column + 1 < layoutData.dimension.width // No distance after last column
    }

    fun isAfterRowDistance(row: Int): Boolean {
      return row < rowsDistance.size
             && row + 1 < layoutData.dimension.height // No distance after last row
    }

    layoutData.visibleCellsData.forEach { layoutCellData ->
      with(layoutCellData.cell.constraints) {
        val rightColumn = x + width - 1
        val bottomRow = y + height - 1
        layoutCellData.rightDistance = if (isAfterColumnDistance(rightColumn)) columnsDistance[rightColumn] else 0
        layoutCellData.bottomDistance = if (isAfterRowDistance(bottomRow)) rowsDistance[bottomRow] else 0

        layoutData.columnsSizeCalculator.addConstraint(x, width, layoutCellData.cellWidth)
        layoutData.rowsSizeCalculator.addConstraint(y, height, layoutCellData.cellHeight)
      }
    }
  }

  /**
   * Layouts visual bounds of [component] (size minus visualPaddings) into provided rectangle
   */
  private fun layoutComponent(component: JComponent,
                              layoutCellData: LayoutCellData,
                              visualX: Int,
                              visualY: Int,
                              visualWidth: Int,
                              visualHeight: Int) {
    val constraints = layoutCellData.cell.constraints
    val visualPaddings = constraints.visualPaddings
    val resultVisualWidth = if (constraints.horizontalAlign == HorizontalAlign.FILL)
      visualWidth
    else
      min(visualWidth, layoutCellData.preferredSize.width - visualPaddings.width)
    val resultVisualHeight = if (constraints.verticalAlign == VerticalAlign.FILL)
      visualHeight
    else
      min(visualHeight, layoutCellData.preferredSize.height - visualPaddings.height)
    val resultVisualX = visualX +
                        when (constraints.horizontalAlign) {
                          HorizontalAlign.LEFT -> 0
                          HorizontalAlign.CENTER -> (visualWidth - resultVisualWidth) / 2
                          HorizontalAlign.RIGHT -> visualWidth - resultVisualWidth
                          HorizontalAlign.FILL -> 0
                        }
    val resultVisualY = visualY +
                        when (constraints.verticalAlign) {
                          VerticalAlign.TOP -> 0
                          VerticalAlign.CENTER -> (visualHeight - resultVisualHeight) / 2
                          VerticalAlign.BOTTOM -> visualHeight - resultVisualHeight
                          VerticalAlign.FILL -> 0
                        }

    component.setBounds(
      resultVisualX - visualPaddings.left, resultVisualY - visualPaddings.top,
      resultVisualWidth + visualPaddings.width, resultVisualHeight + visualPaddings.height
    )
  }

  /**
   * Maximum indexes of occupied cells including hidden components
   */
  private fun getDimension(): Dimension {
    var width = 0
    var height = 0
    cells.forEach { cell ->
      width = max(width, cell.constraints.x + cell.constraints.width)
      height = max(height, cell.constraints.y + cell.constraints.height)
    }
    return Dimension(width, height)
  }

  private fun isEmpty(constraints: JBConstraints): Boolean {
    cells.forEach { cell ->
      with(cell.constraints) {
        if (constraints.x + constraints.width > x &&
            x + width > constraints.x &&
            constraints.y + constraints.height > y &&
            y + height > constraints.y
        ) {
          return false
        }
      }
    }
    return true
  }
}

/**
 * Data that collected before layout/preferred size calculations
 */
private class JBLayoutData {

  var dimension = Dimension()
  var visibleCellsData = emptyList<LayoutCellData>()
  val columnsSizeCalculator = JBColumnsSizeCalculator()
  val rowsSizeCalculator = JBColumnsSizeCalculator()

  val preferredSize: Dimension
    get() = Dimension(columnsSizeCalculator.calculatePreferredSize(), rowsSizeCalculator.calculatePreferredSize())

}

private data class LayoutCellData(val cell: JBCell, val preferredSize: Dimension,
                                  var rightDistance: Int = 0, var bottomDistance: Int = 0) {

  val gapWidth: Int
    get() = cell.constraints.gaps.width + rightDistance

  val gapHeight: Int
    get() = cell.constraints.gaps.height + bottomDistance

  val cellWidth: Int
    get() = preferredSize.width + gapWidth - cell.constraints.visualPaddings.width

  val cellHeight: Int
    get() = preferredSize.height + gapHeight - cell.constraints.visualPaddings.height
}

private sealed class JBCell constructor(val constraints: JBConstraints)

private class JBComponentCell(constraints: JBConstraints, val component: JComponent) : JBCell(constraints)

private class JBGridCell(constraints: JBConstraints, val content: JBGrid) : JBCell(constraints)
