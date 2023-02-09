package com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes

import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.AttachGroupColumnRenderer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.AttachGroupFirstColumnRenderer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.AttachGroupLastColumnRenderer
import com.intellij.xdebugger.impl.ui.attach.dialog.items.separators.TableGroupHeaderSeparator
import org.jetbrains.annotations.Nls
import javax.swing.table.TableCellRenderer

internal class AttachDialogGroupNode(
  @Nls val message: String?,
  private val columnsLayout: AttachDialogColumnsLayout,
  private val relatedNodes: List<AttachDialogElementNode>): AttachDialogElementNode, AttachSelectionIgnoredNode {

  var isFirstGroup: Boolean = false

  override fun visit(filters: AttachToProcessElementsFilters): Boolean {
    return relatedNodes.any { filters.matches(it) }
  }

  override fun getValueAtColumn(column: Int): Any = this

  override fun getRenderer(column: Int): TableCellRenderer {
    val columnsCount = columnsLayout.getColumnsCount()
    if (column >= columnsCount || column < 0) throw IllegalStateException("Unexpected column index: $column")
    if (column == 0) return AttachGroupFirstColumnRenderer()
    if (column == columnsCount - 1) return AttachGroupLastColumnRenderer()
    return AttachGroupColumnRenderer()
  }

  override fun getProcessItem(): AttachDialogProcessItem? = null

  fun getExpectedHeight(): Int {
    return TableGroupHeaderSeparator.getExpectedHeight(isFirstGroup, !message.isNullOrBlank())
  }
}