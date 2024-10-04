// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.vcs.log.data.VcsCommitExternalStatus
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.column.util.VcsLogExternalStatusColumnService
import org.jetbrains.annotations.ApiStatus
import javax.swing.table.TableCellRenderer

@ApiStatus.Internal
abstract class VcsLogExternalStatusColumn<T : VcsCommitExternalStatus> : VcsLogCustomColumn<T> {

  override val isDynamic = true
  override val isResizable = false

  protected abstract fun getExternalStatusColumnService(): VcsLogExternalStatusColumnService<T>

  final override fun getValue(model: GraphTableModel, row: Int): T {
    return getExternalStatusColumnService().getStatus(model, row) ?: getStubValue(model)
  }

  final override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer {
    getExternalStatusColumnService().initialize(table, this)
    return doCreateTableCellRenderer(table)
  }

  abstract fun doCreateTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer
}