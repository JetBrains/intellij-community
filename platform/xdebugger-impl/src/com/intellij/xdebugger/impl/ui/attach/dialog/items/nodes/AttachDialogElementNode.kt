// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes

import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import javax.swing.table.TableCellRenderer

internal interface AttachDialogElementNode {
  fun visit(filters: AttachToProcessElementsFilters): Boolean

  fun getProcessItem(): AttachDialogProcessItem?

  fun getValueAtColumn(column: Int): Any

  fun getRenderer(column: Int): TableCellRenderer?
}