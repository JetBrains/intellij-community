// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.items.cells

import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachToProcessElementsFilters
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.columns.AttachDialogDefaultColumnsLayout
import com.intellij.xdebugger.impl.ui.attach.dialog.items.nodes.AttachDialogProcessNode

internal class UserCell(
  private val attachTreeProcessNode: AttachDialogProcessNode,
  filters: AttachToProcessElementsFilters,
  columnsLayout: AttachDialogColumnsLayout) : AttachFiltersAwareCell(AttachDialogDefaultColumnsLayout.USER_CELL_KEY, attachTreeProcessNode,
                                                                     filters, columnsLayout) {
  override fun getTextToDisplay(): String = attachTreeProcessNode.item.processInfo.user ?: ""
}
