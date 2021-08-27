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

  override var columnsGaps = emptyList<ColumnGaps>()
  override var rowsGaps = emptyList<RowGaps>()

  val visible: Boolean
    get() = cells.any { it.visible }

  private val layoutData = JBLayoutData()
  private val cells = mutableListOf<JBCell>()

  fun register(component: JComponent, constraints: JBConstraints) {
    if (!isEmpty(constraints)) {
      throw UiDslException("Some cells are occupied already: $constraints")
    }

    cells.add(JBComponentCell(constraints, component))
  }

  fun registerSubGrid(constraints: JBConstraints): JBGrid {
    if (!isEmpty(constraints)) {
      throw UiDslException("Some cells are occupied already: $constraints")
    }

    val result = JBGridImpl()
    cells.add(JBGridCell(constraints, result))
    return result
  }

  fun unregister(component: JComponent): Boolean {
    val iterator = cells.iterator()
    for (cell in iterator) {
      when (cell) {
        is JBComponentCell -> {
          if (cell.component == component) {
            iterator.remove()
            return true
          }
        }
        is JBGridCell -> {
          if (cell.content.unregister(component)) {
            return true
          }
        }
      }
    }
    return false
  }

  fun getPreferredSize(): Dimension {
    calculateLayoutData(-1, -1)
    return Dimension(layoutData.preferredWidth, layoutData.preferredHeight)
  }

  /**
   * Layouts components
   */
  fun layout(rect: Rectangle) {
    layoutData.visibleCellsData.forEach { layoutCellData ->
      val bounds = calculateBounds(layoutCellData, rect.x, rect.y)
      when (val cell = layoutCellData.cell) {
        is JBComponentCell -> {
          cell.component.bounds = bounds
        }
        is JBGridCell -> {
          cell.content.layout(bounds)
        }
      }
    }
  }

  /**
   * Calculates [layoutData]
   *
   * @param width if negative - calculates layout for preferred size, otherwise uses [width]
   * @param height if negative - calculates layout for preferred size, otherwise uses [height]
   */
  fun calculateLayoutData(width: Int, height: Int) {
    calculateLayoutDataStep1()
    calculateLayoutDataStep2(width)
    calculateLayoutDataStep3(null)
    calculateLayoutDataStep4(height)
  }

  /**
   * Step 1 of [layoutData] calculations
   */
  fun calculateLayoutDataStep1() {
    layoutData.columnsSizeCalculator.reset()
    val visibleCellsData = mutableListOf<LayoutCellData>()
    var columnsCount = 0
    var rowsCount = 0

    for (cell in cells) {
      var preferredSize: Dimension

      when (cell) {
        is JBComponentCell -> {
          val component = cell.component
          if (!component.isVisible) {
            continue
          }
          preferredSize = component.preferredSize
        }

        is JBGridCell -> {
          val grid = cell.content
          if (!grid.visible) {
            continue
          }
          grid.calculateLayoutDataStep1()
          preferredSize = Dimension(grid.layoutData.preferredWidth, 0)
        }
      }

      val layoutCellData: LayoutCellData
      with(cell.constraints) {
        layoutCellData = LayoutCellData(cell = cell,
          preferredSize = preferredSize,
          columnGaps = ColumnGaps(
            left = columnsGaps.getOrNull(x)?.left ?: 0,
            right = columnsGaps.getOrNull(x + width - 1)?.right ?: 0),
          rowGaps = RowGaps(
            top = rowsGaps.getOrNull(y)?.top ?: 0,
            bottom = rowsGaps.getOrNull(y + height - 1)?.bottom ?: 0)
        )

        columnsCount = max(columnsCount, x + width)
        rowsCount = max(rowsCount, y + height)
      }

      visibleCellsData.add(layoutCellData)
      layoutData.columnsSizeCalculator.addConstraint(cell.constraints.x, cell.constraints.width, layoutCellData.cellPaddedWidth)
    }

    layoutData.visibleCellsData = visibleCellsData
    layoutData.preferredWidth = layoutData.columnsSizeCalculator.calculatePreferredSize()
    layoutData.dimension.setSize(columnsCount, rowsCount)
  }

  /**
   * Step 2 of [layoutData] calculations
   *
   * @param width see [calculateLayoutData]
   */
  fun calculateLayoutDataStep2(width: Int) {
    val calcWidth = if (width < 0) layoutData.preferredWidth else width
    layoutData.columnsCoord = layoutData.columnsSizeCalculator.calculateCoords(calcWidth, resizableColumns)

    for (layoutCellData in layoutData.visibleCellsData) {
      val cell = layoutCellData.cell
      if (cell is JBGridCell) {
        cell.content.calculateLayoutDataStep2(layoutData.getFullPaddedWidth(layoutCellData))
      }
    }
  }

  /**
   * Step 3 of [layoutData] calculations
   */
  fun calculateLayoutDataStep3(parentCellVerticalAlign: VerticalAlign?) {
    layoutData.rowsSizeCalculator.reset()
    layoutData.gridRowBaselineData = null
    initBaselineData()

    for (layoutCellData in layoutData.visibleCellsData) {
      val rowBaselineData = layoutCellData.rowBaselineData
      val constraints = layoutCellData.cell.constraints
      when (val cell = layoutCellData.cell) {
        is JBComponentCell -> {
          if (rowBaselineData == null) {
            continue
          }

          val componentWidth = layoutData.getPaddedWidth(layoutCellData) + constraints.visualPaddings.width
          val baseline: Int
          if (componentWidth >= 0) {
            baseline = cell.component.getBaseline(componentWidth, layoutCellData.preferredSize.height)
            // getBaseline changes preferredSize, at least for JLabel
            layoutCellData.preferredSize.height = cell.component.preferredSize.height
          }
          else {
            baseline = -1
          }

          if (baseline < 0) {
            layoutCellData.rowBaselineData = null
          }
          else {
            layoutCellData.baseline = baseline
            with(rowBaselineData) {
              maxAboveBaseline = max(maxAboveBaseline,
                baseline + layoutCellData.rowGaps.top + constraints.gaps.top - constraints.visualPaddings.top)
              maxBelowBaseline = max(maxBelowBaseline,
                layoutCellData.preferredSize.height - baseline + layoutCellData.rowGaps.bottom + constraints.gaps.bottom - constraints.visualPaddings.bottom)
            }

            if (layoutData.gridRowBaselineData == null && layoutData.dimension.height == 1 &&
                constraints.verticalAlign == parentCellVerticalAlign) {
              layoutData.gridRowBaselineData = rowBaselineData
            }
          }
        }

        is JBGridCell -> {
          val grid = cell.content
          grid.calculateLayoutDataStep3(constraints.verticalAlign)
          layoutCellData.preferredSize.height = grid.layoutData.preferredHeight
          val gridRowBaselineData = grid.layoutData.gridRowBaselineData
          if (gridRowBaselineData == null) {
            layoutCellData.rowBaselineData = null
          }
          else {
            if (rowBaselineData != null) {
              layoutCellData.baseline = gridRowBaselineData.maxAboveBaseline
              with(rowBaselineData) {
                maxAboveBaseline = max(maxAboveBaseline, gridRowBaselineData.maxAboveBaseline)
                maxBelowBaseline = max(maxBelowBaseline, gridRowBaselineData.maxBelowBaseline)
              }
            }
          }
        }
      }
    }

    for (layoutCellData in layoutData.visibleCellsData) {
      val constraints = layoutCellData.cell.constraints
      layoutData.rowsSizeCalculator.addConstraint(constraints.y, constraints.height, layoutCellData.cellPaddedHeight)
    }

    layoutData.preferredHeight = layoutData.rowsSizeCalculator.calculatePreferredSize()
  }

  /**
   * Step 4 of [layoutData] calculations
   *
   * @param height see [calculateLayoutData]
   */
  fun calculateLayoutDataStep4(height: Int) {
    val calcHeight = if (height < 0) layoutData.preferredHeight else height
    layoutData.rowsCoord = layoutData.rowsSizeCalculator.calculateCoords(calcHeight, resizableRows)

    for (layoutCellData in layoutData.visibleCellsData) {
      val cell = layoutCellData.cell
      if (cell is JBGridCell) {
        cell.content.calculateLayoutDataStep4(layoutData.getFullPaddedHeight(layoutCellData))
      }
    }
  }

  /**
   * Assigns [LayoutCellData.rowBaselineData]
   */
  private fun initBaselineData() {
    for (layoutCellData in layoutData.visibleCellsData) {
      layoutCellData.baseline = null
      layoutCellData.rowBaselineData = null
    }

    layoutData.visibleCellsData
      .filter { it.cell.constraints.verticalAlign != VerticalAlign.FILL && it.cell.constraints.height == 1 }
      .groupBy { it.cell.constraints.y }
      .forEach { cellsByRow ->
        cellsByRow.value.groupBy { it.cell.constraints.verticalAlign }.forEach { cellsByAlign ->
          val rowBaselineData = RowBaselineData()
          for (layoutCellData in cellsByAlign.value) {
            layoutCellData.rowBaselineData = rowBaselineData
          }
        }
      }
  }

  /**
   * Calculate bounds for [layoutCellData]
   */
  private fun calculateBounds(layoutCellData: LayoutCellData, offsetX: Int, offsetY: Int): Rectangle {
    val cell = layoutCellData.cell
    val constraints = cell.constraints
    val visualPaddings = constraints.visualPaddings
    val paddedWidth = layoutData.getPaddedWidth(layoutCellData)
    val fullPaddedWidth = layoutData.getFullPaddedWidth(layoutCellData)
    val x = layoutData.columnsCoord[constraints.x] + constraints.gaps.left + layoutCellData.columnGaps.left - visualPaddings.left +
            when (constraints.horizontalAlign) {
              HorizontalAlign.LEFT -> 0
              HorizontalAlign.CENTER -> (fullPaddedWidth - paddedWidth) / 2
              HorizontalAlign.RIGHT -> fullPaddedWidth - paddedWidth
              HorizontalAlign.FILL -> 0
            }

    val fullPaddedHeight = layoutData.getFullPaddedHeight(layoutCellData)
    val paddedHeight = if (constraints.verticalAlign == VerticalAlign.FILL)
      fullPaddedHeight
    else
      min(fullPaddedHeight, layoutCellData.preferredSize.height - visualPaddings.height)
    val y: Int
    val baseline = layoutCellData.baseline
    if (baseline == null) {
      y = layoutData.rowsCoord[constraints.y] + layoutCellData.rowGaps.top + constraints.gaps.top - visualPaddings.top +
          when (constraints.verticalAlign) {
            VerticalAlign.TOP -> 0
            VerticalAlign.CENTER -> (fullPaddedHeight - paddedHeight) / 2
            VerticalAlign.BOTTOM -> fullPaddedHeight - paddedHeight
            VerticalAlign.FILL -> 0
          }
    }
    else {
      val rowBaselineData = layoutCellData.rowBaselineData!!
      val rowHeight = layoutData.getInsideHeight(layoutCellData)
      var subGridOffset = 0

      if (cell is JBGridCell) {
        val gridRowBaselineData = cell.content.layoutData.gridRowBaselineData
        if (gridRowBaselineData != null) {
          subGridOffset = rowBaselineData.maxAboveBaseline - gridRowBaselineData.maxAboveBaseline
        }
      }

      y = layoutData.rowsCoord[constraints.y] + layoutCellData.rowGaps.top + rowBaselineData.maxAboveBaseline - baseline + subGridOffset +
          when (constraints.verticalAlign) {
            VerticalAlign.TOP -> 0
            VerticalAlign.CENTER -> (rowHeight - rowBaselineData.height) / 2
            VerticalAlign.BOTTOM -> rowHeight - rowBaselineData.height
            VerticalAlign.FILL -> 0
          }
    }

    return Rectangle(offsetX + x, offsetY + y, paddedWidth + visualPaddings.width, paddedHeight + visualPaddings.height)
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

  //
  // Step 1
  //

  var visibleCellsData = emptyList<LayoutCellData>()
  val columnsSizeCalculator = JBColumnsSizeCalculator()
  var preferredWidth = 0

  /**
   * Maximum indexes of occupied cells excluding hidden components
   */
  val dimension = Dimension()

  //
  // Step 2
  //
  var columnsCoord = emptyArray<Int>()

  //
  // Step 3
  //
  val rowsSizeCalculator = JBColumnsSizeCalculator()
  var preferredHeight = 0

  /**
   * Baseline data is calculated only for grids with one row and for vertical align equal to parent cell
   */
  var gridRowBaselineData: RowBaselineData? = null

  //
  // Step 4
  //
  var rowsCoord = emptyArray<Int>()

  fun getPaddedWidth(layoutCellData: LayoutCellData): Int {
    val fullPaddedWidth = getFullPaddedWidth(layoutCellData)
    return if (layoutCellData.cell.constraints.horizontalAlign == HorizontalAlign.FILL)
      fullPaddedWidth
    else
      min(fullPaddedWidth, layoutCellData.preferredSize.width - layoutCellData.cell.constraints.visualPaddings.width)
  }

  fun getFullPaddedWidth(layoutCellData: LayoutCellData): Int {
    val constraints = layoutCellData.cell.constraints
    return columnsCoord[constraints.x + constraints.width] - columnsCoord[constraints.x] - layoutCellData.gapWidth
  }

  fun getInsideHeight(layoutCellData: LayoutCellData): Int {
    val constraints = layoutCellData.cell.constraints
    return rowsCoord[constraints.y + constraints.height] - rowsCoord[constraints.y] - layoutCellData.rowGaps.height
  }

  fun getFullPaddedHeight(layoutCellData: LayoutCellData): Int {
    val constraints = layoutCellData.cell.constraints
    return rowsCoord[constraints.y + constraints.height] - rowsCoord[constraints.y] - layoutCellData.gapHeight
  }
}

/**
 * For sub-grids height of [preferredSize] calculated on late steps of [JBGridImpl.calculateLayoutData]
 */
private data class LayoutCellData(val cell: JBCell, val preferredSize: Dimension,
                                  val columnGaps: ColumnGaps, val rowGaps: RowGaps) {
  /**
   * Calculated on step 3
   */
  var baseline: Int? = null

  /**
   * Calculated on step 3. null for cells without baseline,  height > 1 or vertical align FILL.
   * After full calculations [baseline] and [rowBaselineData] are null or not null together
   */
  var rowBaselineData: RowBaselineData? = null

  val gapWidth: Int
    get() = cell.constraints.gaps.width + columnGaps.width

  val gapHeight: Int
    get() = cell.constraints.gaps.height + rowGaps.height

  /**
   * Cell width including gaps and excluding visualPaddings
   */
  val cellPaddedWidth: Int
    get() = preferredSize.width + gapWidth - cell.constraints.visualPaddings.width

  /**
   * Cell height including gaps and excluding visualPaddings
   */
  val cellPaddedHeight: Int
    get() {
      val baselineData = rowBaselineData
      val height = if (baselineData == null) preferredSize.height else baselineData.height
      return gapHeight - cell.constraints.visualPaddings.height + height
    }
}

/**
 * Max sizes for a row which include gaps and exclude paddings
 */
private data class RowBaselineData(var maxAboveBaseline: Int = 0, var maxBelowBaseline: Int = 0) {
  val height: Int
    get() = maxAboveBaseline + maxBelowBaseline
}

private sealed class JBCell(val constraints: JBConstraints) {
  abstract val visible: Boolean
}

private class JBComponentCell(constraints: JBConstraints, val component: JComponent) : JBCell(constraints) {
  override val visible: Boolean
    get() = component.isVisible
}

private class JBGridCell(constraints: JBConstraints, val content: JBGridImpl) : JBCell(constraints) {
  override val visible: Boolean
    get() = content.visible
}
