package com.intellij.xdebugger.impl.ui.attach.dialog.items

import com.intellij.xdebugger.impl.ui.attach.dialog.AttachDialogProcessItem

interface AttachToProcessElement {
  fun visit(filters: AttachToProcessElementsFilters): Boolean

  fun getProcessItem(): AttachDialogProcessItem?
}