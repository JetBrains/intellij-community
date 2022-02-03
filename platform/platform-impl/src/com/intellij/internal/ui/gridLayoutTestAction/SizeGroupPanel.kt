// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.gridLayoutTestAction

import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import javax.swing.JLabel
import javax.swing.JPanel

class SizeGroupPanel {

  val panel = createTabPanel("Cells in same size groups should have the same sizes", createContent())

  private fun createContent(): JPanel {
    val result = JPanel(GridLayout())
    val builder = RowsGridBuilder(result)

    builder.label("Label")
      .label("Label", widthGroup = "horiz1")
      .row()
      .label("Label a very very very very long label", widthGroup = "horiz1")

    return result
  }

  private fun RowsGridBuilder.label(text: String, widthGroup: String? = null): RowsGridBuilder {
    val lines = mutableListOf(text)
    if (widthGroup != null) {
      lines += "widthGroup = $widthGroup"
    }
    cell(JLabel(lines.joinToString("<br>", prefix = "<html>")),
         widthGroup = widthGroup)
    return this
  }
}
