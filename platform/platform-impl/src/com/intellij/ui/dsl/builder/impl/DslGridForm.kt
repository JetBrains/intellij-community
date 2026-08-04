// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.SpacingConfiguration

/**
 * Says what the rows of a `panel { }` are, in the terms [buildGridForm] lays a form out in.
 *
 * Only what the layout has to know crosses over. A placeholder keeps [panel] and [spacing] so that it can
 * install its component into the panel once it has one.
 */
internal fun toGridForm(rows: List<RowImpl>,
                        dialogPanelConfig: DialogPanelConfig,
                        panel: DialogPanel,
                        spacing: SpacingConfiguration): List<GridFormRow> {
  preprocess(rows, dialogPanelConfig)

  return rows.map { row ->
    GridFormRow(
      rowLayout = row.rowLayout,
      cells = row.cells.map { toGridFormCell(it, dialogPanelConfig, panel, spacing) },
      indent = row.getIndent(),
      resizableRow = row.resizableRow,
      rowComment = row.rowComment,
      customGaps = row.customGaps,
      topGap = row.topGap,
      bottomGap = row.bottomGap,
      internalGaps = row.internalGaps,
      creationStackTrace = row.creationStackTrace,
    )
  }
}

private fun toGridFormCell(cell: CellBaseImpl<*>?,
                           dialogPanelConfig: DialogPanelConfig,
                           panel: DialogPanel,
                           spacing: SpacingConfiguration): GridFormCell? =
  when (cell) {
    null -> null
    is CellImpl<*> -> GridFormComponentCell(
      component = cell.component,
      viewComponent = cell.viewComponent,
      label = cell.label,
      labelPosition = cell.labelPosition,
      comment = cell.comment,
      commentRight = cell.commentRight,
      contextHelpLabel = cell.contextHelpLabel,
      contextHelpDescription = cell.contextHelpInfo?.description,
      widthGroup = cell.widthGroup,
      horizontalAlign = cell.horizontalAlign,
      verticalAlign = cell.verticalAlign,
      resizableColumn = cell.resizableColumn,
      rightGap = cell.rightGap,
      customGaps = cell.customGaps,
    )
    // A nested panel brings its own spacing, and everything within it is measured by that.
    is PanelImpl -> GridFormPanelCell(
      rows = toGridForm(cell.rows, dialogPanelConfig, panel, cell.spacingConfiguration),
      spacingConfiguration = cell.spacingConfiguration,
      horizontalAlign = cell.horizontalAlign,
      verticalAlign = cell.verticalAlign,
      resizableColumn = cell.resizableColumn,
      rightGap = cell.rightGap,
      customGaps = cell.customGaps,
    )
    is PlaceholderBaseImpl<*> -> GridFormDeferredCell(
      place = { constraints -> cell.init(panel, constraints, spacing) },
      horizontalAlign = cell.horizontalAlign,
      verticalAlign = cell.verticalAlign,
      resizableColumn = cell.resizableColumn,
      rightGap = cell.rightGap,
      customGaps = cell.customGaps,
    )
  }

/**
 * Preprocesses rows/cells and adds necessary rows/cells
 * 1. Labels, see [Cell.label]
 */
private fun preprocess(rows: List<RowImpl>, dialogPanelConfig: DialogPanelConfig) {
  for (row in rows) {
    var i = 0
    while (i < row.cells.size) {
      val cell = row.cells[i]
      if (cell is CellImpl<*>) {
        cell.label?.let {
          if (cell.labelPosition == LabelPosition.LEFT) {
            val labelCell = CellImpl(dialogPanelConfig, it, row)
            row.cells.add(i, labelCell)
            i++
          }

          labelCell(it, cell)
        }
      }

      i++
    }
  }
}
