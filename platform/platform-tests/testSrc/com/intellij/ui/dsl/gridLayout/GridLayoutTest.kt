// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.*
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import org.junit.Assert
import org.junit.Test
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.assertEquals

class GridLayoutTest {

  @Test
  fun testEmpty() {
    val panel = JPanel(GridLayout())

    Assert.assertEquals(panel.preferredSize, Dimension(0, 0))

    panel.border = EmptyBorder(10, 20, 30, 40)
    Assert.assertEquals(panel.preferredSize, Dimension(60, 40))
  }

  @Test
  fun testOneCellSimple() {
    val panel = JPanel(GridLayout())
    RowsGridBuilder(panel).cell(label())
    assertEquals(panel.preferredSize, PREFERRED_SIZE)
  }

  @Test
  fun testOneCellGaps() {
    val panel = JPanel(GridLayout())
    val gaps = Gaps(1, 2, 3, 4)
    RowsGridBuilder(panel).cell(label(), gaps = gaps)
    assertEquals(panel.preferredSize, Dimension(PREFERRED_WIDTH + gaps.width, PREFERRED_HEIGHT + gaps.height))
  }

  @Test
  fun testOneCellVisualPaddings() {
    val panel = JPanel(GridLayout())
    val visualPaddings = Gaps(1, 2, 3, 4)
    val label = label()
    RowsGridBuilder(panel)
      .cell(label, visualPaddings = visualPaddings)
    doLayout(panel, 200, 100)
    // Visual paddings are compensated by layout, so whole component is fit
    assertEquals(0, label.x)
    assertEquals(0, label.y)
    assertEquals(panel.preferredSize, PREFERRED_SIZE)
    assertEquals(PREFERRED_SIZE, label.size)

    panel.removeAll()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.BOTTOM,
            resizableColumn = true, visualPaddings = visualPaddings)
    doLayout(panel, 200, 100)
    // Visual paddings are compensated by layout, so whole component is fit
    assertEquals(0, label.x)
    assertEquals(100 - PREFERRED_HEIGHT, label.y)
    assertEquals(panel.preferredSize, PREFERRED_SIZE)
    assertEquals(200, label.width)
    assertEquals(PREFERRED_HEIGHT, label.height)
  }

  @Test
  fun testOneCellHorizontalAlignments() {
    val panel = JPanel(GridLayout())
    val gaps = Gaps(1, 2, 3, 4)
    val label = label()
    RowsGridBuilder(panel)
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.LEFT, resizableColumn = true)
    doLayout(panel, 200, 100)
    assertEquals(label.size, PREFERRED_SIZE)
    assertEquals(gaps.left, label.x)

    panel.removeAll()
    RowsGridBuilder(panel)
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.CENTER, resizableColumn = true)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(gaps.left + (200 - PREFERRED_WIDTH - gaps.width) / 2, label.x)

    panel.removeAll()
    RowsGridBuilder(panel)
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.RIGHT, resizableColumn = true)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(200 - PREFERRED_WIDTH - gaps.right, label.x)

    panel.removeAll()
    RowsGridBuilder(panel)
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
    doLayout(panel, 200, 100)
    assertEquals(Dimension(200 - gaps.width, PREFERRED_HEIGHT), label.size)
    assertEquals(gaps.left, label.x)
  }

  @Test
  fun testOneCellVerticalAlignments() {
    val panel = JPanel(GridLayout())
    val gaps = Gaps(1, 2, 3, 4)
    val label = label()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, gaps = gaps, verticalAlign = VerticalAlign.TOP)
    doLayout(panel, 200, 100)
    assertEquals(label.size, PREFERRED_SIZE)
    assertEquals(gaps.top, label.y)

    panel.removeAll()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, gaps = gaps, verticalAlign = VerticalAlign.CENTER)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(gaps.top + (100 - PREFERRED_HEIGHT - gaps.height) / 2, label.y)

    panel.removeAll()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, gaps = gaps, verticalAlign = VerticalAlign.BOTTOM)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(100 - PREFERRED_HEIGHT - gaps.bottom, label.y)

    panel.removeAll()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, gaps = gaps, verticalAlign = VerticalAlign.FILL)
    doLayout(panel, 200, 100)
    assertEquals(Dimension(PREFERRED_WIDTH, 100 - gaps.height), label.size)
    assertEquals(gaps.top, label.y)
  }

  @Test
  fun testOneCellComplex() {
    val panel = JPanel(GridLayout())
    val label = label()
    val gaps = Gaps(1, 2, 3, 4)
    val visualPaddings = Gaps(5, 6, 7, 8)
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, horizontalAlign = HorizontalAlign.LEFT, verticalAlign = VerticalAlign.CENTER, resizableColumn = true, gaps = gaps,
            visualPaddings = visualPaddings)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.preferredSize)
    // Visual paddings are compensated by layout, so whole component is fit
    assertEquals(max(0, gaps.left - visualPaddings.left), label.x)
    assertEquals(gaps.top + (100 - PREFERRED_HEIGHT - gaps.height + visualPaddings.height) / 2 - visualPaddings.top, label.y)

    panel.removeAll()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, horizontalAlign = HorizontalAlign.RIGHT, verticalAlign = VerticalAlign.FILL, resizableColumn = true, gaps = gaps,
            visualPaddings = visualPaddings)
    doLayout(panel, 200, 100)
    // Visual paddings are compensated by layout, so whole component is fit
    assertEquals(Dimension(PREFERRED_WIDTH, 100), label.size)
    assertEquals(200 - PREFERRED_WIDTH, label.x)
    assertEquals(0, label.y)
  }

  @Test
  fun testLabeledGrid() {
    val panel = JPanel(GridLayout())
    val builder = RowsGridBuilder(panel)
    val rowsCount = 10
    val labels = mutableListOf<Array<JLabel>>()
    for (i in 1..rowsCount) {
      val row = arrayOf(
        label(preferredWidth = Random.nextInt(100)),
        label(),
        label(preferredWidth = Random.nextInt(150)))
      builder
        .row()
        .cell(row[0], horizontalAlign = HorizontalAlign.values().random())
        .cell(row[1], resizableColumn = true)
        .cell(row[2])
      labels.add(row)
    }

    val leftColumnWidth = labels.maxOf { it[0].preferredSize.width }
    val rightColumnWidth = labels.maxOf { it[2].preferredSize.width }
    assertEquals(Dimension(leftColumnWidth + PREFERRED_WIDTH + rightColumnWidth, PREFERRED_HEIGHT * rowsCount),
                 panel.preferredSize)

    doLayout(panel, 600, 600)

    for (row in labels) {
      assertEquals(leftColumnWidth, row[1].x)
      assertEquals(600 - rightColumnWidth, row[2].x)
    }
  }

  @Test
  fun testRowColumnGaps() {
    val layout = GridLayout()
    val panel = JPanel(layout)
    val labels = mutableListOf<List<JLabel>>()
    val columnsCount = 4
    val rowsCount = 5
    val columnGaps = (0 until columnsCount).map { HorizontalGaps(it * 20, it * 20 + 10) }
    val builder = RowsGridBuilder(panel)
      .columnsGaps(columnGaps)

    for (y in 0 until rowsCount) {
      builder.row(VerticalGaps(y * 15, y * 15 + 5))
      val row = mutableListOf<JLabel>()
      for (x in 1..columnsCount) {
        val label = label()
        builder.cell(label)
        row.add(label)
      }
      labels.add(row)
    }

    var preferredWidth = columnsCount * PREFERRED_WIDTH + columnGaps.sumOf { it.width }
    var preferredHeight = rowsCount * PREFERRED_HEIGHT + layout.rootGrid.rowsGaps.sumOf { it.height }
    assertEquals(Dimension(preferredWidth, preferredHeight), panel.preferredSize)

    // Hide whole column 1
    for (y in 0 until rowsCount) {
      labels[y][1].isVisible = false
      if (y == rowsCount - 1) {
        preferredWidth -= PREFERRED_WIDTH + columnGaps[1].width
      }
      assertEquals(Dimension(preferredWidth, preferredHeight), panel.preferredSize)
    }

    // Hide whole row 2
    for (x in 0 until columnsCount) {
      labels[2][x].isVisible = false
      if (x == columnsCount - 1) {
        preferredHeight -= PREFERRED_HEIGHT + layout.rootGrid.rowsGaps[2].height
      }

      assertEquals(Dimension(preferredWidth, preferredHeight), panel.preferredSize)
    }
  }
}
