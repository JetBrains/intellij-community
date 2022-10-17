package com.intellij.xdebugger.impl.ui.attach.dialog.items.list

import com.intellij.ide.util.PropertiesComponent
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachColumnSettingsState

class AttachListColumnSettingsState: AttachColumnSettingsState {

  companion object {
    private const val ATTACH_TREE_EXECUTABLE_COLUMN_WIDTH_KEY = "ATTACH_TREE_EXECUTABLE_COLUMN_WIDTH"
    private const val ATTACH_TREE_PID_COLUMN_WIDTH_KEY = "ATTACH_TREE_PID_COLUMN_WIDTH"
    private const val ATTACH_TREE_COMMAND_LINE_COLUMN_WIDTH_KEY = "ATTACH_TREE_COMMAND_LINE_COLUMN_WIDTH"

    private const val EXECUTABLE_COLUMN_DEFAULT_WIDTH = 270
    private const val PID_COLUMN_DEFAULT_WIDTH = 100
    private const val COMMAND_LINE_COLUMN_DEFAULT_WIDTH = 430
  }

  private var executableColumnWidth: Int
    get() = PropertiesComponent.getInstance().getInt(ATTACH_TREE_EXECUTABLE_COLUMN_WIDTH_KEY, EXECUTABLE_COLUMN_DEFAULT_WIDTH)
    set(value) {
      PropertiesComponent.getInstance().setValue(ATTACH_TREE_EXECUTABLE_COLUMN_WIDTH_KEY, value, EXECUTABLE_COLUMN_DEFAULT_WIDTH)
    }

  private var pidColumnWidth: Int
    get() = PropertiesComponent.getInstance().getInt(ATTACH_TREE_PID_COLUMN_WIDTH_KEY, PID_COLUMN_DEFAULT_WIDTH)
    set(value) {
      PropertiesComponent.getInstance().setValue(ATTACH_TREE_PID_COLUMN_WIDTH_KEY, value, PID_COLUMN_DEFAULT_WIDTH)
    }

  private var commandLineColumnWidth: Int
    get() = PropertiesComponent.getInstance().getInt(ATTACH_TREE_COMMAND_LINE_COLUMN_WIDTH_KEY, COMMAND_LINE_COLUMN_DEFAULT_WIDTH)
    set(value) {
      PropertiesComponent.getInstance().setValue(ATTACH_TREE_COMMAND_LINE_COLUMN_WIDTH_KEY, value, COMMAND_LINE_COLUMN_DEFAULT_WIDTH)
    }

  override fun getColumnWidth(index: Int): Int {
    return when(index) {
      0 -> executableColumnWidth
      1 -> pidColumnWidth
      2 -> commandLineColumnWidth
      else -> throw IllegalStateException("Unexpected column index: $index")
    }
  }

  override fun setColumnWidth(index: Int, value: Int) {
    when (index) {
      0 -> executableColumnWidth = value
      1 -> pidColumnWidth = value
      2 -> commandLineColumnWidth = value
      else -> throw IllegalStateException("Unexpected column index: $index")
    }
  }
}