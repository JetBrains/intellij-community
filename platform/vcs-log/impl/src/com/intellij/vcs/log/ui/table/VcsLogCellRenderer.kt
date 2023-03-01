// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import javax.swing.JTable

/**
 * Extends the capabilities of [javax.swing.table.TableCellRenderer] when displaying cells in the VCS Log
 *
 * @see com.intellij.vcs.log.ui.table.column.VcsLogColumn.createTableCellRenderer
 */
interface VcsLogCellRenderer {
  fun getCellController(): VcsLogCellController? = null

  /**
   * @return default width of the [com.intellij.vcs.log.ui.table.column.VcsLogColumn].
   * If `null` and [com.intellij.vcs.log.ui.table.column.VcsLogColumn] returns [String]
   * in [com.intellij.vcs.log.ui.table.column.VcsLogColumn.getValue] then Log will calculate the width of column using some top rows,
   * otherwise preferred width will be used.
   */
  fun getPreferredWidth(table: JTable): Int? = null
}
