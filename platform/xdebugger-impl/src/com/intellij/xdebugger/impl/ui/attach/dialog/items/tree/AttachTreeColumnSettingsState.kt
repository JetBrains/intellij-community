package com.intellij.xdebugger.impl.ui.attach.dialog.items.tree

import com.intellij.ide.util.PropertiesComponent
import com.intellij.xdebugger.impl.ui.attach.dialog.items.AttachColumnSettingsState

class AttachTreeColumnSettingsState: AttachColumnSettingsState {

  companion object {
    private const val ATTACH_TREE_EXECUTABLE_COLUMN_WIDTH_KEY = "ATTACH_TREE_EXECUTABLE_COLUMN_WIDTH"
    private const val ATTACH_TREE_PID_COLUMN_WIDTH_KEY = "ATTACH_TREE_PID_COLUMN_WIDTH"
    private const val ATTACH_TREE_DEBUGGERS_COLUMN_WIDTH_KEY = "ATTACH_TREE_DEBUGGERS_COLUMN_WIDTH"
    private const val ATTACH_TREE_COMMAND_LINE_COLUMN_WIDTH_KEY = "ATTACH_TREE_COMMAND_LINE_COLUMN_WIDTH"

    private const val EXECUTABLE_COLUMN_DEFAULT_WIDTH = 250
    private const val PID_COLUMN_DEFAULT_WIDTH = 70
    private const val DEBUGGERS_COLUMN_DEFAULT_WIDTH = 130
    private const val COMMAND_LINE_COLUMN_DEFAULT_WIDTH = 250
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

  private var debuggersColumnWidth: Int
    get() = PropertiesComponent.getInstance().getInt(ATTACH_TREE_DEBUGGERS_COLUMN_WIDTH_KEY, DEBUGGERS_COLUMN_DEFAULT_WIDTH)
    set(value) {
      PropertiesComponent.getInstance().setValue(ATTACH_TREE_DEBUGGERS_COLUMN_WIDTH_KEY, value, DEBUGGERS_COLUMN_DEFAULT_WIDTH)
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
      2 -> debuggersColumnWidth
      3 -> commandLineColumnWidth
      else -> throw IllegalStateException("Unexpected column index: $index")
    }
  }

  override fun setColumnWidth(index: Int, value: Int) {
    when (index) {
      0 -> executableColumnWidth = value
      1 -> pidColumnWidth = value
      2 -> debuggersColumnWidth = value
      3 -> commandLineColumnWidth = value
      else -> throw IllegalStateException("Unexpected column index: $index")
    }
  }
}