// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.doLayout
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.label
import org.junit.Test
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals

/**
 * For non-resizable cells the preferred size should be used for parent minimum size calculation
 */
class GridLayoutMinimumSizeTest {

  @Test
  fun testMinimumSizeInRow() {
    val layout = GridLayout()
    layout.respectMinimumSize = true
    val panel = JPanel(layout)
    val labels = listOf(
      label(Dimension(10, 20), Dimension(20, 30)),
      label(Dimension(50, 60), Dimension(60, 70)),
      label(Dimension(100, 110), Dimension(110, 120)),
    )

    RowsGridBuilder(panel)
      .cell(labels[0])
      .cell(labels[1], resizableColumn = true, horizontalAlign = HorizontalAlign.FILL)
      .cell(labels[2], resizableColumn = true, horizontalAlign = HorizontalAlign.FILL)

    assertEquals(panel.minimumSize, Dimension(20 + 50 + 100, 120))
    assertEquals(panel.preferredSize, Dimension(20 + 60 + 110, 120))
    checkSizes(panel, panel.minimumSize, labels, listOf(labels[0].preferredSize, Dimension(50, 70), Dimension(100, 120)))
    checkSizes(panel, panel.preferredSize, labels, labels.map { it.preferredSize })
  }

  @Test
  fun testMinimumSizeInColumn() {
    val layout = GridLayout()
    layout.respectMinimumSize = true
    val panel = JPanel(layout)
    val labels = listOf(
      label(Dimension(10, 20), Dimension(20, 30)),
      label(Dimension(50, 60), Dimension(60, 70)),
      label(Dimension(100, 110), Dimension(110, 120)),
    )

    RowsGridBuilder(panel)
      .cell(labels[0])
      .row(resizable = true)
      .cell(labels[1], verticalAlign = VerticalAlign.FILL)
      .row(resizable = true)
      .cell(labels[2], verticalAlign = VerticalAlign.FILL)

    assertEquals(panel.minimumSize, Dimension(110, 30 + 60 + 110))
    assertEquals(panel.preferredSize, Dimension(110, 30 + 70 + 120))
    checkSizes(panel, panel.minimumSize, labels, listOf(labels[0].preferredSize, Dimension(60, 60), Dimension(110, 110)))
    checkSizes(panel, panel.preferredSize, labels, labels.map { it.preferredSize })
  }

  @Test
  fun testMinimumSizeDiagonal() {
    val layout = GridLayout()
    layout.respectMinimumSize = true
    val panel = JPanel(layout)
    val labels = listOf(
      label(Dimension(20, 10), Dimension(30, 20)),
      label(Dimension(60, 50), Dimension(70, 60)),
      label(Dimension(110, 100), Dimension(120, 110)),
    )

    RowsGridBuilder(panel)
      .cell(labels[0])
      .row(resizable = true)
      .skip(1)
      .cell(labels[1], resizableColumn = true, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL)
      .row()
      .skip(2)
      .cell(labels[2])

    assertEquals(panel.minimumSize, Dimension(30 + 60 + 120, 20 + 50 + 110))
    assertEquals(panel.preferredSize, Dimension(30 + 70 + 120, 20 + 60 + 110))
    checkSizes(panel, panel.minimumSize, labels, listOf(labels[0].preferredSize, labels[1].minimumSize, labels[2].preferredSize))
    checkSizes(panel, panel.preferredSize, labels, labels.map { it.preferredSize })
  }

  private fun checkSizes(panel: JPanel, panelSize: Dimension, labels: List<JLabel>, expectedLabelSizes: List<Dimension>) {
    assertEquals(labels.size, expectedLabelSizes.size)

    doLayout(panel, panelSize)

    for ((index, label) in labels.withIndex()) {
      assertEquals(expectedLabelSizes[index], label.size)
    }
  }
}
