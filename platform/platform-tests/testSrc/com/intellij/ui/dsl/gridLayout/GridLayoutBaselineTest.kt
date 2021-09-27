// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.doLayout
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.label
import org.junit.Test
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals

class GridLayoutBaselineTest {

  @Test
  fun testBaseline() {
    for (verticalAlign in VerticalAlign.values()) {
      if (verticalAlign == VerticalAlign.FILL) {
        continue
      }

      val panel = JPanel(GridLayout())
      val builder = RowsGridBuilder(panel)
        .defaultBaselineAlign(true)

      for (i in 10..16) {
        builder.label(verticalAlign, i)
      }

      doLayout(panel, 1600, 800)
      var baseline: Int? = null
      for (component in panel.components) {
        val size = component.size
        val componentBaseline = component.getBaseline(size.width, size.height)
        if (baseline == null) {
          baseline = component.y + componentBaseline
        }
        else {
          assertEquals(baseline, component.y + componentBaseline)
        }
      }
    }
  }

  @Test
  fun testBaselineWithSubPanels() {
    for (verticalAlign in VerticalAlign.values()) {
      if (verticalAlign == VerticalAlign.FILL) {
        continue
      }

      val panel = JPanel(GridLayout())
      val builder = RowsGridBuilder(panel)
        .defaultBaselineAlign(true)

      builder
        .label(verticalAlign, 14)
        .subGridBuilder(verticalAlign = verticalAlign)
        .label(verticalAlign, 12)
        .subGridBuilder(verticalAlign = verticalAlign)
        .label(verticalAlign, 16)
        .label(verticalAlign, 10)

      doLayout(panel, 1600, 800)
      var baseline: Int? = null
      for (component in panel.components) {
        val size = component.size
        val componentBaseline = component.getBaseline(size.width, size.height)
        if (baseline == null) {
          baseline = component.y + componentBaseline
        }
        else {
          assertEquals(baseline, component.y + componentBaseline)
        }
      }
    }
  }

  @Test
  fun testSubPanelsNoBaseline() {
    var rowComboBoxNoBaseline = createRowComboBoxNoBaseline(VerticalAlign.TOP)
    val height = rowComboBoxNoBaseline.comboBox.height
    assertEquals(rowComboBoxNoBaseline.label.y, 0)
    assertEquals(rowComboBoxNoBaseline.comboBox.y, 0)
    assertEquals(rowComboBoxNoBaseline.label2.y, 0)

    rowComboBoxNoBaseline = createRowComboBoxNoBaseline(VerticalAlign.CENTER)
    assertEquals(rowComboBoxNoBaseline.label.y, (height - rowComboBoxNoBaseline.label.height) / 2)
    assertEquals(rowComboBoxNoBaseline.comboBox.y, 0)
    assertEquals(rowComboBoxNoBaseline.label2.y, (height - rowComboBoxNoBaseline.label2.height) / 2)

    rowComboBoxNoBaseline = createRowComboBoxNoBaseline(VerticalAlign.BOTTOM)
    assertEquals(rowComboBoxNoBaseline.label.y, height - rowComboBoxNoBaseline.label.height)
    assertEquals(rowComboBoxNoBaseline.comboBox.y, 0)
    assertEquals(rowComboBoxNoBaseline.label2.y, height - rowComboBoxNoBaseline.label2.height)

    rowComboBoxNoBaseline = createRowComboBoxNoBaseline(VerticalAlign.FILL)
    assertEquals(rowComboBoxNoBaseline.label.y, 0)
    assertEquals(rowComboBoxNoBaseline.label.height, height)
    assertEquals(rowComboBoxNoBaseline.comboBox.y, 0)
    assertEquals(rowComboBoxNoBaseline.label2.y, 0)
    assertEquals(rowComboBoxNoBaseline.label2.height, height)
  }

  private fun createRowComboBoxNoBaseline(verticalAlign: VerticalAlign): RowComboBoxNoBaseline {
    val result = RowComboBoxNoBaseline(
      JLabel("Label"),
      object : ComboBox<String>() {
        override fun getBaseline(width: Int, height: Int): Int {
          return -1
        }
      },
      JLabel("Label2")
    )

    val panel = JPanel(GridLayout())
    RowsGridBuilder(panel)
      .defaultBaselineAlign(true)
      .defaultVerticalAlign(verticalAlign)
      .cell(result.label)
      .subGridBuilder(1)
      .cell(result.comboBox)
      .cell(result.label2)

    doLayout(panel)

    assertEquals(result.label.height, result.label2.height)
    assertEquals(result.label.y, result.label2.y)

    return result
  }
}

private data class RowComboBoxNoBaseline(val label: JLabel, val comboBox: ComboBox<*>, val label2: JLabel)