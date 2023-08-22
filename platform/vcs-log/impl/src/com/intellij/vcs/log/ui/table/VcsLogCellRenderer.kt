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

  @Deprecated(message = "Implement getPreferredWidth() instead")
  fun getPreferredWidth(table: JTable): Int? = null

  /**
   * @return strategy for calculating column preferred width.
   * @see PreferredWidth
   */
  fun getPreferredWidth(): PreferredWidth? {
    @Suppress("DEPRECATION")
    return PreferredWidth.Fixed { getPreferredWidth(it) ?: -1 }
  }

  /**
   * Defines a strategy to calculate column prefered width.
   */
  sealed interface PreferredWidth {
    /**
     * Column width is fixed and does not change with the cells content.
     */
    class Fixed(val function: (JTable) -> Int) : PreferredWidth

    /**
     * Column width is calculated based on the data in the cells.
     * @property function function which calculates the width or returns null when data are not loaded yet.
     */
    class FromData(val function: (JTable, Any, Int, Int) -> Int?) : PreferredWidth
  }
}
