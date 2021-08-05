// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.JBGridLayout
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JToggleButton

@ApiStatus.Experimental
class PanelBuilder {

  val spacing = createIntelliJSpacingConfiguration()

  val applyCallbacks: MutableMap<JComponent?, MutableList<() -> Unit>> = linkedMapOf()
  val resetCallbacks: MutableMap<JComponent?, MutableList<() -> Unit>> = linkedMapOf()
  val isModifiedCallbacks: MutableMap<JComponent?, MutableList<() -> Boolean>> = linkedMapOf()

  private val rows = mutableListOf<RowBuilder>()

  fun row(@Nls label: String, init: RowBuilder.() -> Unit): RowBuilder {
    return row(JLabel(label), init)
  }

  fun row(label: JLabel? = null, init: RowBuilder.() -> Unit): RowBuilder {
    val result = RowBuilder(this, label)
    result.init()
    rows.add(result)
    return result
  }

  internal fun build(): DialogPanel {
    val result = DialogPanel(layout = JBGridLayout())
    val columnsCount = rows.maxOf { it.cells.size }
    val rowsGridBuilder = RowsGridBuilder(result)

    for ((y, row) in rows.withIndex()) {
      for ((x, cell) in row.cells.withIndex()) {
        val width = if (x == row.cells.size - 1) columnsCount - x else 1
        rowsGridBuilder.cell(cell.component, width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
                             gaps = Gaps(right = cell.rightGap))
      }

      rowsGridBuilder.row()
      createCommentRow(rowsGridBuilder, row, columnsCount)
    }

    return result
  }

  /**
   * Appends comment (currently one comment for a row is supported, can be fixed later)
   */
  private fun createCommentRow(builder: RowsGridBuilder, row: RowBuilder, columnsCount: Int) {
    for ((column, cell) in row.cells.withIndex()) {
      cell.comment?.let {
        createComment(builder, column, columnsCount, row.label != null, it, cell.viewComponent)
        return
      }
    }
  }

  private fun createComment(builder: RowsGridBuilder,
                            column: Int,
                            columnsCount: Int,
                            labeledRow: Boolean,
                            comment: JComponent,
                            anchorComponent: JComponent) {
    if (labeledRow && column == 1) {
      builder.cell(comment, width = columnsCount)
    }
    else {
      val gaps = if (anchorComponent is JToggleButton) Gaps(left = spacing.horizontalIndent) else Gaps.EMPTY
      builder.skip(column)
      builder.cell(comment, columnsCount - column, gaps = gaps)
    }
    builder.row()
  }
}
