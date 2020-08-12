// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.vcs.log.impl.CommonUiProperties.COLUMN_ORDER
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.table.VcsLogColumn

internal fun VcsLogUiProperties.supportsColumnsReordering() = exists(COLUMN_ORDER)

/**
 * Provides a list of [VcsLogColumn] ordered based on the saved state.
 * Some [VcsLogColumn] may not be included in the list, which means that the [VcsLogColumn] should not be displayed in the interface.
 *
 * @see moveColumn
 * @see addColumn
 * @see removeColumn
 * @see updateOrder
 */
internal fun VcsLogUiProperties.getColumnsOrder(): List<VcsLogColumn> = get(COLUMN_ORDER).map { VcsLogColumn.fromOrdinal(it) }

internal fun VcsLogUiProperties.moveColumn(column: VcsLogColumn, newIndex: Int) = updateOrder { order ->
  order.remove(column)
  order.add(newIndex, column)
}

internal fun VcsLogUiProperties.addColumn(column: VcsLogColumn) = updateOrder { order ->
  order.add(column)
}

internal fun VcsLogUiProperties.removeColumn(column: VcsLogColumn) = updateOrder { order ->
  order.remove(column)
}

internal fun VcsLogUiProperties.updateOrder(newOrder: List<VcsLogColumn>) {
  set(COLUMN_ORDER, newOrder.map { it.ordinal })
}

private fun VcsLogUiProperties.updateOrder(update: (MutableList<VcsLogColumn>) -> Unit) {
  val order = getColumnsOrder().toMutableList()
  update(order)
  updateOrder(order)
}