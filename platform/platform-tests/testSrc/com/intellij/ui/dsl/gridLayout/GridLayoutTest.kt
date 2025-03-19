// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.*
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import org.junit.Assert
import org.junit.Test
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    val gaps = UnscaledGaps(1, 2, 3, 4)
    RowsGridBuilder(panel).cell(label(), gaps = gaps)
    assertEquals(panel.preferredSize,
                 Dimension(PREFERRED_WIDTH + JBUIScale.scale(gaps.width), PREFERRED_HEIGHT + JBUIScale.scale(gaps.height)))
  }

  @Test
  fun testOneCellVisualPaddings() {
    val panel = JPanel(GridLayout())
    val visualPaddings = UnscaledGaps(1, 2, 3, 4)
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
    val gaps = UnscaledGaps(1, 2, 3, 4)
    val label = label()
    RowsGridBuilder(panel)
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.LEFT, resizableColumn = true)
    doLayout(panel, 200, 100)
    assertEquals(label.size, PREFERRED_SIZE)
    assertEquals(JBUIScale.scale(gaps.left), label.x)

    panel.removeAll()
    RowsGridBuilder(panel)
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.CENTER, resizableColumn = true)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(JBUIScale.scale(gaps.left) + (200 - PREFERRED_WIDTH - JBUIScale.scale(gaps.width)) / 2, label.x)

    panel.removeAll()
    RowsGridBuilder(panel)
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.RIGHT, resizableColumn = true)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(200 - PREFERRED_WIDTH - JBUIScale.scale(gaps.right), label.x)

    panel.removeAll()
    RowsGridBuilder(panel)
      .cell(label, gaps = gaps, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
    doLayout(panel, 200, 100)
    assertEquals(Dimension(200 - JBUIScale.scale(gaps.width), PREFERRED_HEIGHT), label.size)
    assertEquals(JBUIScale.scale(gaps.left), label.x)
  }

  @Test
  fun testOneCellVerticalAlignments() {
    val panel = JPanel(GridLayout())
    val gaps = UnscaledGaps(1, 2, 3, 4)
    val label = label()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, gaps = gaps, verticalAlign = VerticalAlign.TOP)
    doLayout(panel, 200, 100)
    assertEquals(label.size, PREFERRED_SIZE)
    assertEquals(JBUIScale.scale(gaps.top), label.y)

    panel.removeAll()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, gaps = gaps, verticalAlign = VerticalAlign.CENTER)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(JBUIScale.scale(gaps.top) + (100 - PREFERRED_HEIGHT - JBUIScale.scale(gaps.height)) / 2, label.y)

    panel.removeAll()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, gaps = gaps, verticalAlign = VerticalAlign.BOTTOM)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.size)
    assertEquals(100 - PREFERRED_HEIGHT - JBUIScale.scale(gaps.bottom), label.y)

    panel.removeAll()
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, gaps = gaps, verticalAlign = VerticalAlign.FILL)
    doLayout(panel, 200, 100)
    assertEquals(Dimension(PREFERRED_WIDTH, 100 - JBUIScale.scale(gaps.height)), label.size)
    assertEquals(JBUIScale.scale(gaps.top), label.y)
  }

  @Test
  fun testOneCellComplex() {
    val panel = JPanel(GridLayout())
    val label = label()
    val gaps = UnscaledGaps(1, 2, 3, 4)
    val visualPaddings = UnscaledGaps(5, 6, 7, 8)
    RowsGridBuilder(panel)
      .row(resizable = true)
      .cell(label, horizontalAlign = HorizontalAlign.LEFT, verticalAlign = VerticalAlign.CENTER, resizableColumn = true, gaps = gaps,
            visualPaddings = visualPaddings)
    doLayout(panel, 200, 100)
    assertEquals(PREFERRED_SIZE, label.preferredSize)
    // Visual paddings are compensated by layout, so whole component is fit
    assertEquals(max(0, JBUIScale.scale(gaps.left) - JBUIScale.scale(visualPaddings.left)), label.x)
    assertEquals(JBUIScale.scale(gaps.top) + (100 - PREFERRED_HEIGHT - JBUIScale.scale(gaps.height) + JBUIScale.scale(visualPaddings.height)) / 2 - JBUIScale.scale(visualPaddings.top), label.y)

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
        .cell(row[0], horizontalAlign = HorizontalAlign.entries.random())
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
    val columnGaps = (0 until columnsCount).map { UnscaledGapsX(it * 20, it * 20 + 10) }
    val builder = RowsGridBuilder(panel)
      .columnsGaps(columnGaps)

    for (y in 0 until rowsCount) {
      builder.row(UnscaledGapsY(y * 15, y * 15 + 5))
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

  @Test
  fun testGetConstraints() {
    val label1 = JLabel()
    val label2 = JLabel()
    val label3 = JLabel()
    val layout = GridLayout()
    val panel = JPanel(layout)
    val subGridConstraints = Constraints(layout.rootGrid, 1, 0)
    val subGrid = layout.addLayoutSubGrid(subGridConstraints)

    val constraints1 = Constraints(layout.rootGrid, 0, 0)
    panel.add(label1, constraints1)

    val constraints2 = Constraints(subGrid, 0, 0)
    panel.add(label2, constraints2)

    val constraints3 = Constraints(layout.rootGrid, 2, 0)
    panel.add(label3, constraints3)

    assertEquals(constraints1, layout.getConstraints(label1))
    assertEquals(constraints2, layout.getConstraints(label2))
    assertEquals(constraints3, layout.getConstraints(label3))
    assertEquals(subGridConstraints, layout.getConstraints(subGrid))
    assertNull(layout.getConstraints(layout.rootGrid))
  }

  /**
   * Check IDEA-298908 Internal error happens when there are resizable Column/Row and some components are hidden
   */
  @Test
  fun testIdea298908_resizableColumn() {
    val nonResizeableIndex = 3
    val panel = JPanel(GridLayout())
    val builder = RowsGridBuilder(panel)
    val labels = mutableListOf<JLabel>()
    for (i in 0..4) {
      val label = JLabel("Label").apply { preferredSize = Dimension(40, 20) }
      labels.add(label)
      builder.cell(label, horizontalAlign = HorizontalAlign.FILL, resizableColumn = i != nonResizeableIndex)
    }

    testIdea298908(panel, labels, true, nonResizeableIndex)
  }

  /**
   * Check IDEA-298908 Internal error happens when there are resizable Column/Row and some components are hidden
   */
  @Test
  fun testIdea298908_resizableRow() {
    val nonResizeableIndex = 2
    val panel = JPanel(GridLayout())
    val builder = RowsGridBuilder(panel)
    val labels = mutableListOf<JLabel>()
    for (i in 0..4) {
      val label = JLabel("Label").apply { preferredSize = Dimension(40, 20) }
      labels.add(label)
      builder
        .row(resizable = i != nonResizeableIndex)
        .cell(label, verticalAlign = VerticalAlign.FILL)
    }

    testIdea298908(panel, labels, false, nonResizeableIndex)
  }

  /**
   * Try every two cells invisible
   */
  private fun testIdea298908(panel: JPanel, labels: List<JLabel>, horizontal: Boolean, nonResizeableIndex: Int) {
    for (i in 1..labels.lastIndex) {
      assertEquals(labels[0].preferredSize, labels[i].preferredSize)
    }
    val preferredSize = if (horizontal) labels[0].preferredSize.width else labels[0].preferredSize.height
    assertEquals(preferredSize % 2, 0) // must be even
    val size = preferredSize * 2 * 3 // size should be dividable on 2 and 3, see resizableSize calculation

    for (i in 0..labels.size - 2) {
      for (j in i + 1 until labels.size) {
        for ((index, label) in labels.withIndex()) {
          label.isVisible = index != i && index != j
        }

        doLayout(panel, size, size)

        val resizableSize = if (i == nonResizeableIndex || j == nonResizeableIndex) size / 3 else (size - preferredSize) / 2
        var expectedCoord = 0
        for ((index, label) in labels.withIndex()) {
          if (label.isVisible) {
            assertEquals(expectedCoord, if (horizontal) label.x else label.y)
            expectedCoord += if (index == nonResizeableIndex) preferredSize else resizableSize
          }
        }

        assertEquals(expectedCoord, size)
      }
    }
  }
}
