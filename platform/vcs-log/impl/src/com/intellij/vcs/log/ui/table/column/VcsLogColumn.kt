// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn

interface VcsLogColumn<T> {
  @get:NonNls
  val id: String

  @get:Nls
  val localizedName: String

  val isDynamic: Boolean

  val contentClass: Class<*>

  /**
   * @return content sample to estimate the width of the column,
   * or null if content width may vary significantly and width cannot be estimated from the sample
   */
  @JvmDefault
  val contentSample: String?
    get() = null

  fun getValue(model: GraphTableModel, row: Int): T

  fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer

  @JvmDefault
  fun initColumn(table: VcsLogGraphTable, column: TableColumn) {
  }

  fun getStubValue(model: GraphTableModel): T
}