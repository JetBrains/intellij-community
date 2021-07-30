// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.gridLayout

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
    resizeOneDimension(layoutData.columnsCoord, resizableColumns, rect.width)
    resizeOneDimension(layoutData.rowsCoord, resizableRows, rect.height)

    cells.forEach { cell ->
      var leftCellCoord = layoutData.columnsCoord[cell.constraints.x]
      var topCellCoord = layoutData.rowsCoord[cell.constraints.y]
      val nextColumn = cell.constraints.x + cell.constraints.width
      val nextRow = cell.constraints.y + cell.constraints.height
      var width = layoutData.columnsCoord[nextColumn] - leftCellCoord
      var height = layoutData.rowsCoord[nextRow] - topCellCoord
      leftCellCoord += rect.x
      topCellCoord += rect.y

      // todo check col/row visibility for last col/row
      if (isAfterColumnDistance(nextColumn - 1)) {
        width -= columnsDistance[nextColumn - 1]
      }
      if (isAfterRowDistance(nextRow - 1)) {
        height -= rowsDistance[nextRow - 1]
      }

      when (cell) {
        is JBComponentCell -> {
          val component = cell.component
          if (component.isVisible) {
            layoutComponent(component, cell.constraints, leftCellCoord, topCellCoord, width, height)
          }
        }
        is JBGridCell -> {
          (cell.content as JBGridImpl).layout(Rectangle(leftCellCoord, topCellCoord, width, height))
        }
      }
    }
  }

  /**
   * Calculates all data in [layoutData], measures all components etc
   */
  fun calculateLayoutData() {
    layoutData.dimension = getDimension()
    val preferredSizes = mutableMapOf<JComponent, Dimension>()
    val columnsCoordCalculator = JBCoordCalculator()
    val rowsCoordCalculator = JBCoordCalculator()

    cells.forEach { cell ->
      val preferredSize: Dimension?
      when (cell) {
        is JBComponentCell -> {
          val component = cell.component
          if (component.isVisible) {
            preferredSize = component.preferredSize
            preferredSizes[component] = preferredSize
          }
          else {
            preferredSize = null
          }
        }
        is JBGridCell -> {
          val grid = cell.content as JBGridImpl
          grid.calculateLayoutData()
          preferredSize = grid.layoutData.preferredSize
        }
      }

      if (preferredSize != null) {
        with(cell.constraints) {
          val rightColumn = x + width - 1
          val bottomRow = y + height - 1
          val rightDistance = if (isAfterColumnDistance(rightColumn)) columnsDistance[rightColumn] else 0
          val bottomDistance = if (isAfterRowDistance(bottomRow)) rowsDistance[bottomRow] else 0

          columnsCoordCalculator.addConstraint(
            x, width,
            preferredSize.width + gaps.width - visualPaddings.width + rightDistance
          )
          rowsCoordCalculator.addConstraint(
            y, height,
            preferredSize.height + gaps.height - visualPaddings.height + bottomDistance
          )
        }
      }
    }

    layoutData.preferredSizes = preferredSizes
    layoutData.columnsCoord = columnsCoordCalculator.calculate(layoutData.dimension.width)
    layoutData.rowsCoord = rowsCoordCalculator.calculate(layoutData.dimension.height)
  }


  private fun isAfterColumnDistance(column: Int): Boolean {
    return column < columnsDistance.size && column + 1 < layoutData.dimension.width
  }

  private fun isAfterRowDistance(row: Int): Boolean {
    return row < rowsDistance.size && row + 1 < layoutData.dimension.height
  }

  /**
   * Resizes columns and rows in [layoutData] so that the grid occupies [fullSize] (if there are resizable columns)
   * Extra size is distributed equally between [resizable]
   */
  private fun resizeOneDimension(coordinates: Array<Int>, resizable: Set<Int>, fullSize: Int) {
    var extraSize = fullSize - coordinates.last()

    if (extraSize == 0 || resizable.isEmpty()) {
      return
    }

    var previousShift = 0
    // Filter out resizable columns/rows that are out of scope
    // todo use isColumnVisible?
    var remainedResizable = resizable.count { it < coordinates.size - 1 }

    for (i in coordinates.indices) {
      coordinates[i] += previousShift

      if (i < coordinates.size - 1 && i in resizable) {
        // Use such correction so exactly whole extra size is used (rounding could break other approaches)
        val correction = extraSize / remainedResizable
        previousShift += correction
        extraSize -= correction
        remainedResizable--
      }
    }
  }

  private fun layoutComponent(
    component: JComponent, constraints: JBConstraints, x: Int, y: Int,
    width: Int, height: Int
  ) {
    val insideWidth = width - constraints.gaps.width + constraints.visualPaddings.width
    val insideHeight = height - constraints.gaps.height + +constraints.visualPaddings.height
    val preferredSize = layoutData.preferredSizes[component] ?: throw JBGridException()

    val resultWidth = if (constraints.horizontalAlign == HorizontalAlign.FILL)
      insideWidth
    else
      min(insideWidth, preferredSize.width)
    val resultHeight = if (constraints.verticalAlign == VerticalAlign.FILL)
      insideHeight
    else
      min(insideHeight, preferredSize.height)
    val resultX = x + constraints.gaps.left - constraints.visualPaddings.left +
                  when (constraints.horizontalAlign) {
                    HorizontalAlign.LEFT -> 0
                    HorizontalAlign.CENTER -> (insideWidth - resultWidth) / 2
                    HorizontalAlign.RIGHT -> insideWidth - resultWidth
                    HorizontalAlign.FILL -> 0
                  }
    val resultY = y + constraints.gaps.top - constraints.visualPaddings.top +
                  when (constraints.verticalAlign) {
                    VerticalAlign.TOP -> 0
                    VerticalAlign.CENTER -> (insideHeight - resultHeight) / 2
                    VerticalAlign.BOTTOM -> insideHeight - resultHeight
                    VerticalAlign.FILL -> 0
                  }

    component.setBounds(
      resultX, resultY,
      resultWidth, resultHeight
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

  var dimension: Dimension = Dimension()
  var preferredSizes = emptyMap<JComponent, Dimension>()
  var columnsCoord = emptyArray<Int>()
  var rowsCoord = emptyArray<Int>()

  val preferredSize: Dimension
    get() = Dimension(columnsCoord.last(), rowsCoord.last())

}

private class JBCoordCalculator {

  /**
   * [size] is a width/height constraint for columns/rows with correspondent [cellIndex] and [cellSize].
   * It includes gaps, visualPaddings, "right column"/"bottom row" distances except last columns/rows
   */
  private data class SizeConstraint(val cellIndex: Int, val cellSize: Int, val size: Int)

  private val sizeConstraints = mutableListOf<SizeConstraint>()

  fun addConstraint(cellIndex: Int, cellSize: Int, size: Int) =
    sizeConstraints.add(SizeConstraint(cellIndex, cellSize, size))

  /**
   * Calculates minimal coordinates of columns/rows with size limitations from [sizeConstraints]
   */
  fun calculate(dimension: Int): Array<Int> {
    val result = Array(dimension + 1) { 0 }
    sizeConstraints.sortWith(Comparator.comparingInt(SizeConstraint::cellIndex))
    for ((cellIndex, cellSize, size) in sizeConstraints) {
      result[cellIndex + cellSize] = max(result[cellIndex] + size, result[cellIndex + cellSize])
    }
    return result
  }
}

private sealed class JBCell constructor(val constraints: JBConstraints)

private class JBComponentCell(constraints: JBConstraints, val component: JComponent) : JBCell(constraints)

private class JBGridCell(constraints: JBConstraints, val content: JBGrid) : JBCell(constraints)
