package com.intellij.xdebugger.impl.ui.attach.dialog.items

interface AttachColumnSettingsState {
  fun getColumnWidth(index: Int): Int

  fun setColumnWidth(index: Int, value: Int)
}