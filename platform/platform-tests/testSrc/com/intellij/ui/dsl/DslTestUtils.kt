// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

const val PREFERRED_WIDTH = 60
const val PREFERRED_HEIGHT = 40
val PREFERRED_SIZE = Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT)


fun doLayout(panel: JPanel) {
  doLayout(panel, 800, 600)
}

fun doLayout(panel: JPanel, width: Int, height: Int) {
  panel.setSize(width, height)
  (panel.layout as GridLayout).layoutContainer(panel)
}

fun label(preferredWidth: Int = PREFERRED_WIDTH, preferredHeight: Int = PREFERRED_HEIGHT): JLabel {
  val result = JLabel("Label")
  result.preferredSize = Dimension(preferredWidth, preferredHeight)
  return result
}

fun RowsGridBuilder.label(verticalAlign: VerticalAlign, size: Int): RowsGridBuilder {
  val label = JLabel("${verticalAlign.name} $size")
  label.font = label.font.deriveFont(size.toFloat())
  cell(label, verticalAlign = verticalAlign)
  return this
}

fun generateLongString(): String {
  return (0..9).map { "Long string $it" }.joinToString()
}
