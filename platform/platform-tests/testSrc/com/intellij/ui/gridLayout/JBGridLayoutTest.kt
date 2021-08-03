// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.gridLayout

import com.intellij.ui.gridLayout.builders.RowsGridBuilder
import org.junit.Assert
import org.junit.Test
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlin.random.Random
import kotlin.test.assertEquals

const val PREFERRED_WIDTH = 60
const val PREFERRED_HEIGHT = 40
val PREFERRED_SIZE = Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT)

class JBGridLayoutTest {

  @Test
  fun testEmpty() {
    val panel = JPanel(JBGridLayout())

    Assert.assertEquals(panel.preferredSize, Dimension(0, 0))

    panel.border = EmptyBorder(10, 20, 30, 40)
    Assert.assertEquals(panel.preferredSize, Dimension(60, 40))
  }

  @Test
  fun testOneCellSimple() {
    val panel = JPanel(JBGridLayout())
    RowsGridBuilder(panel).cell(label())
    assertEquals(panel.preferredSize, PREFERRED_SIZE)
  }

  @Test
  fun testOneCellVisualPaddings() {
    val panel = JPanel(JBGridLayout())
    val gaps = Gaps(1, 2, 3, 4)
    RowsGridBuilder(panel).cell(label(), gaps = gaps)
    assertEquals(panel.preferredSize, Dimension(PREFERRED_WIDTH + gaps.width, PREFERRED_HEIGHT + gaps.height))
  }

  @Test
  fun testOneCellGaps() {
    val panel = JPanel(JBGridLayout())
    val gaps = Gaps(1, 2, 3, 4)
    RowsGridBuilder(panel).cell(label(), visualPaddings = gaps)
    assertEquals(panel.preferredSize, Dimension(PREFERRED_WIDTH - gaps.width, PREFERRED_HEIGHT - gaps.height))
  }

  @Test
  fun testOneCellHorizontalAlignments() {
    val panel = JPanel(JBGridLayout())
    val gaps = Gaps(1, 2, 3, 4)
    val label = label()
    RowsGridBuilder(panel)
      .resizableColumns(setOf(0))
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.LEFT)
    doLayout(panel, 200, 100)
    assertEquals(label.size, PREFERRED_SIZE)
    assertEquals(gaps.left, label.x)

    panel.removeAll()
    RowsGridBuilder(panel)
      .resizableColumns(setOf(0))
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.CENTER)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(gaps.left + (200 - PREFERRED_WIDTH - gaps.width) / 2, label.x)

    panel.removeAll()
    RowsGridBuilder(panel)
      .resizableColumns(setOf(0))
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.RIGHT)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(200 - PREFERRED_WIDTH - gaps.right, label.x)

    panel.removeAll()
    RowsGridBuilder(panel)
      .resizableColumns(setOf(0))
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.FILL)
    doLayout(panel, 200, 100)
    assertEquals(Dimension(200 - gaps.width, PREFERRED_HEIGHT), label.size)
    assertEquals(gaps.left, label.x)
  }

  @Test
  fun testOneCellVerticalAlignments() {
    val panel = JPanel(JBGridLayout())
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
    val panel = JPanel(JBGridLayout())
    val label = label()
    val gaps = Gaps(1, 2, 3, 4)
    val visualPaddings = Gaps(5, 6, 7, 8)
    RowsGridBuilder(panel)
      .resizableColumns(setOf(0))
      .row(resizable = true)
      .cell(label, horizontalAlign = HorizontalAlign.LEFT, verticalAlign = VerticalAlign.CENTER, gaps = gaps,
            visualPaddings = visualPaddings)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.preferredSize)
    assertEquals(gaps.left - visualPaddings.left, label.x)
    assertEquals(gaps.top + (100 - PREFERRED_HEIGHT - gaps.height + visualPaddings.height) / 2 - visualPaddings.top, label.y)

    panel.removeAll()
    RowsGridBuilder(panel)
      .resizableColumns(setOf(0))
      .row(resizable = true)
      .cell(label, horizontalAlign = HorizontalAlign.RIGHT, verticalAlign = VerticalAlign.FILL, gaps = gaps,
            visualPaddings = visualPaddings)
    doLayout(panel, 200, 100)
    assertEquals(Dimension(PREFERRED_WIDTH, 100 - gaps.height + visualPaddings.height), label.size)
    assertEquals(200 - PREFERRED_WIDTH - gaps.right + visualPaddings.right, label.x)
    assertEquals(gaps.top - visualPaddings.top, label.y)
  }

  @Test
  fun testLabeledGrid() {
    val panel = JPanel(JBGridLayout())
    val builder = RowsGridBuilder(panel)
      .resizableColumns(setOf(1))
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
        .cell(row[1])
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
  fun testDistances() {
    val layout = JBGridLayout()
    val panel = JPanel(layout)
    val labels = mutableListOf<List<JLabel>>()
    val builder = RowsGridBuilder(panel)
      .columnsDistance(listOf(10, 20, 30, 40)) // 40 shouldn't be used by layout
    val columnsCount = 4
    val rowsCount = 5
    var sumRowsDistance = 0

    for (y in 1..rowsCount) {
      val row = mutableListOf<JLabel>()
      for (x in 1..columnsCount) {
        val label = label()
        builder.cell(label)
        row.add(label)
      }
      labels.add(row)

      val distance = y * 15
      builder.row(distance)
      if (y != rowsCount) {
        sumRowsDistance += distance
      }
    }

    assertEquals(Dimension(columnsCount * PREFERRED_WIDTH + 60, rowsCount * PREFERRED_HEIGHT + sumRowsDistance),
                 panel.preferredSize)

    // Hide whole column 1
    for (y in 0 until rowsCount) {
      labels[y][1].isVisible = false
      var preferredWidth = columnsCount * PREFERRED_WIDTH + 60
      if (y == rowsCount - 1) {
        preferredWidth -= PREFERRED_WIDTH + 20
      }
      assertEquals(Dimension(preferredWidth, rowsCount * PREFERRED_HEIGHT + sumRowsDistance), panel.preferredSize)
    }

    // Hide whole row 2
    for (x in 0 until columnsCount) {
      labels[2][x].isVisible = false
      var preferredHeight = rowsCount * PREFERRED_HEIGHT + sumRowsDistance
      if (x == columnsCount - 1) {
        preferredHeight -= PREFERRED_HEIGHT + 45
      }

      assertEquals(Dimension((columnsCount - 1) * PREFERRED_WIDTH + 40, preferredHeight), panel.preferredSize)
    }
  }

  private fun doLayout(panel: JPanel, width: Int, height: Int) {
    panel.setSize(width, height)
    (panel.layout as JBGridLayout).layoutContainer(panel)
  }

  private fun label(preferredWidth: Int = PREFERRED_WIDTH, preferredHeight: Int = PREFERRED_HEIGHT): JLabel {
    val result = JLabel("Label")
    result.preferredSize = Dimension(preferredWidth, preferredHeight)
    return result
  }
}