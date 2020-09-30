// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.table.TableCellRenderer

/**
 * Column that is displayed in the VCS Log (e.g. Author, Date, Hash).
 *
 * @see VcsLogDefaultColumn
 * @see VcsLogCustomColumn
 */
@ApiStatus.Experimental
interface VcsLogColumn<T> {
  /**
   * Column identifier. Used to persist column properties (e.g. width, order).
   */
  @get:NonNls
  val id: String

  /**
   * Localized column name. Used to display the column name in the user interface.
   */
  @get:Nls
  val localizedName: String

  /**
   * Allow user to hide [VcsLogColumn] in Presentation Settings
   */
  val isDynamic: Boolean

  /**
   * Allow user to resize [VcsLogColumn]
   */
  @JvmDefault
  val isResizable: Boolean
    get() = true

  /**
   * @return value for given [row] which will be displayed by [TableCellRenderer] created in [createTableCellRenderer]
   *
   * @see getStubValue
   */
  fun getValue(model: GraphTableModel, row: Int): T

  /**
   * @return [TableCellRenderer] which will be used to draw the column rows
   *
   * @see com.intellij.vcs.log.ui.table.VcsLogStringCellRenderer
   * @see com.intellij.vcs.log.ui.table.VcsLogCellRenderer
   */
  fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer

  /**
   * @return a value which should be used if [getValue] were not calculated (e.g. exception is thrown)
   */
  fun getStubValue(model: GraphTableModel): T
}