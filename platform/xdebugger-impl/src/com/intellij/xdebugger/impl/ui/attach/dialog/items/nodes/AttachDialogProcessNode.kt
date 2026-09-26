// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes

import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import javax.swing.table.TableCellRenderer

internal class AttachDialogProcessNode(
  val item: AttachDialogProcessItem,
  private val filters: AttachToProcessElementsFilters,
  private val columnsLayout: AttachDialogColumnsLayout): AttachDialogElementNode {

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return filters.accept(item)
  }

  override fun getProcessItem(): AttachDialogProcessItem = item

  override fun getValueAtColumn(column: Int): Any {
    return columnsLayout.createCell(column, this, filters, false)
  }

  override fun getRenderer(column: Int): TableCellRenderer? = null

  override fun toString(): String = item.toString()
}