package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.openapi.Disposable
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode
import javax.swing.JComponent

interface AttachToProcessItemsListBase {
  fun updateFilter(searchFilterValue: String)
  fun getFocusedComponent(): JComponent
  fun addSelectionListener(disposable: Disposable, listenerAction: (AttachDialogElementNode?) -> Unit)
  fun getSelectedItem(): AttachDialogElementNode?
  fun selectNextItem()
}