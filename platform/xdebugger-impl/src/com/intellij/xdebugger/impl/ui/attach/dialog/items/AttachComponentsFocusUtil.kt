package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.ui.table.JBTable
import com.intellij.util.application
import java.awt.event.FocusEvent
import java.awt.event.FocusListener

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