package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogDefaultColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode
import javax.swing.SwingConstants

internal class PidCell(
  private val attachTreeProcessNode: AttachDialogProcessNode,
  filters: AttachToProcessElementsFilters,
  columnsLayout: AttachDialogColumnsLayout) : AttachFiltersAwareCell(AttachDialogDefaultColumnsLayout.PID_CELL_KEY, attachTreeProcessNode,
                                                                     filters, columnsLayout) {
  override fun getTextToDisplay(): String = attachTreeProcessNode.item.processInfo.pid.toString()

  override fun getTextStartOffset(component: SimpleColoredComponent): Int = JBUI.scale(5)

  override fun getAlignment(): Int = SwingConstants.RIGHT
}
