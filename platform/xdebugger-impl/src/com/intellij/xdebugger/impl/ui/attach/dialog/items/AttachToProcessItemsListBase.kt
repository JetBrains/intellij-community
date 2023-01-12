package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.openapi.Disposable
import javax.swing.JComponent

interface AttachToProcessItemsListBase {
  fun updateFilter(searchFilterValue: String)
  fun getFocusedComponent(): JComponent
  fun addSelectionListener(disposable: Disposable, listenerAction: (AttachToProcessElement?) -> Unit)
  fun getSelectedItem(): AttachToProcessElement?
}