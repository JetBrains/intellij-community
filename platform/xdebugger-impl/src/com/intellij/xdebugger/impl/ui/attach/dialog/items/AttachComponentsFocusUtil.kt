package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.ui.table.JBTable
import com.intellij.util.application
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogState
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogGroupNode
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener

internal fun JBTable.installSelectionOnFocus() {
  addFocusListener(object : FocusListener {
    override fun focusGained(e: FocusEvent) {
      if (e.cause != FocusEvent.Cause.TRAVERSAL &&
          e.cause != FocusEvent.Cause.TRAVERSAL_BACKWARD &&
          e.cause != FocusEvent.Cause.TRAVERSAL_FORWARD &&
          e.cause != FocusEvent.Cause.TRAVERSAL_DOWN &&
          e.cause != FocusEvent.Cause.TRAVERSAL_UP
      ) {
        return
      }
      focusFirst()
      application.invokeLater { updateUI() }
    }

    override fun focusLost(e: FocusEvent) {
      if (e.cause != FocusEvent.Cause.TRAVERSAL &&
          e.cause != FocusEvent.Cause.TRAVERSAL_BACKWARD &&
          e.cause != FocusEvent.Cause.TRAVERSAL_FORWARD &&
          e.cause != FocusEvent.Cause.TRAVERSAL_DOWN &&
          e.cause != FocusEvent.Cause.TRAVERSAL_UP
      ) {
        return
      }
      application.invokeLater { updateUI() }
    }
  })
}

internal fun JBTable.installRowsHeightUpdater() {
  updateRowsHeight()
  model.addTableModelListener(object : TableModelListener {
    override fun tableChanged(e: TableModelEvent?) {
      e ?: return
      updateRowsHeight(e.firstRow, e.lastRow)
    }
  })
}

internal fun JBTable.updateRowsHeight(from: Int = 0, to: Int = rowCount - 1) {
  rowHeight = AttachDialogState.DEFAULT_ROW_HEIGHT
  for (row in from until to + 1) {
    val valueAt = model.getValueAt<AttachDialogElementNode>(row)
    if (valueAt is AttachDialogGroupNode) {
      setRowHeight(row, valueAt.getExpectedHeight())
    }
  }
}