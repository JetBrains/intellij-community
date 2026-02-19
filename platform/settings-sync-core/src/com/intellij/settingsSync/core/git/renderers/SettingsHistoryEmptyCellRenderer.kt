package com.intellij.settingsSync.core.git.renderers

import com.intellij.settingsSync.core.git.table.SettingsHistoryTable
import com.intellij.settingsSync.core.git.table.SettingsHistoryTableRow

internal class SettingsHistoryEmptyCellRenderer : SettingsHistoryCellRenderer() {
  override fun customizeHistoryCellRenderer(table: SettingsHistoryTable,
                                            row: SettingsHistoryTableRow,
                                            selected: Boolean,
                                            hasFocus: Boolean,
                                            rowIndex: Int) {
    // Nothing here. The cell is empty
  }
}