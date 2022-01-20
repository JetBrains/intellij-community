// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.doLayout
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import org.junit.Test
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GridLayoutSizeGroupTest {

  @Test
  fun testWidthGroup() {
    val panel = JPanel(GridLayout())
    val builder = RowsGridBuilder(panel)
    val label1 = label("Label")
    val label2 = label("Label")
    val label = label("Label")
    val bigLabel = label("A very long label<br>second line")
    builder.cell(label1)
    builder.cell(label2, widthGroup = "anotherGroup")
    builder.cell(label, widthGroup = "group")
    builder.cell(bigLabel, widthGroup = "group")

    doLayout(panel)

    val label1Size = label1.size
    val labelSize = label.size
    val bigLabelSize = bigLabel.size

    assertEquals(label1Size, label2.size)
    assertEquals(label1Size.height, labelSize.height)
    assertEquals(labelSize.width, bigLabelSize.width)
    assertTrue(labelSize.height < bigLabelSize.height)
  }

  @Test
  fun testHeightGroup() {
    val panel = JPanel(GridLayout())
    val builder = RowsGridBuilder(panel)
    val label1 = label("Label")
    val label2 = label("Label")
    val label = label("Label")
    val bigLabel = label("A very long label<br>second line")
    builder.cell(label1)
    builder.cell(label2, heightGroup = "anotherGroup")
    builder.cell(label, heightGroup = "group")
    builder.cell(bigLabel, heightGroup = "group")

    doLayout(panel)

    val label1Size = label1.size
    val labelSize = label.size
    val bigLabelSize = bigLabel.size

    assertEquals(label1Size, label2.size)
    assertEquals(label1Size.width, labelSize.width)
    assertEquals(labelSize.height, bigLabelSize.height)
    assertTrue(labelSize.width < bigLabelSize.width)
  }

  private fun label(text: String): JLabel {
    return JLabel("<html>$text")
  }
}