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
      .label("Label", horizontalSizeGroup = "horiz1")
      .row()
      .label("Label a very very very very long label", horizontalSizeGroup = "horiz1")
      .row()
      .label("High label<br>second line", verticalSizeGroup = "vert1")
      .label("Label")
      .label("Label", verticalSizeGroup = "vert1")

    return result
  }

  private fun RowsGridBuilder.label(text: String, horizontalSizeGroup: String? = null, verticalSizeGroup: String? = null): RowsGridBuilder {
    val lines = mutableListOf(text)
    if (horizontalSizeGroup != null) {
      lines += "horizontalSizeGroup = $horizontalSizeGroup"
    }
    if (verticalSizeGroup != null) {
      lines += "verticalSizeGroup = $verticalSizeGroup"
    }
    cell(JLabel(lines.joinToString("<br>", prefix = "<html>")),
         horizontalSizeGroup = horizontalSizeGroup, verticalSizeGroup = verticalSizeGroup)
    return this
  }
}
