// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.openapi.Disposable
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogElementNode
import javax.swing.JComponent

internal interface AttachToProcessItemsListBase {
  fun updateFilter(searchFilterValue: String)
  fun getFocusedComponent(): JComponent
  fun addSelectionListener(disposable: Disposable, listenerAction: (AttachDialogElementNode?) -> Unit)
  fun getSelectedItem(): AttachDialogElementNode?
  fun selectNextItem()
}