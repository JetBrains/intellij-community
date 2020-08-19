// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column.storage

import com.intellij.vcs.log.impl.CommonUiProperties.COLUMN_ID_ORDER
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.table.column.VcsLogColumn
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager

internal fun VcsLogUiProperties.supportsColumnsReordering() = exists(COLUMN_ID_ORDER)

/**
 * Provides a list of visible [VcsLogColumn] ordered based on the saved state.
 * If [VcsLogColumn] is visible and its state were not saved, it will go after ordered columns.
 *
 * Columns visibility is checked using [VcsLogColumnsVisibilityStorage].
 *
 * @see moveColumn
 * @see addColumn
 * @see removeColumn
 * @see updateOrder
 */
internal fun VcsLogUiProperties.getColumnsOrder(): List<VcsLogColumn<*>> {
  val currentColumns = VcsLogColumnManager.getInstance().getCurrentColumns()
  val savedOrder = get(COLUMN_ID_ORDER).mapNotNull { id -> currentColumns.find { it.id == id } }
  val visibilityStorage = VcsLogColumnsVisibilityStorage.getInstance()
  val visibleColumns = (currentColumns - savedOrder).filter { visibilityStorage.isVisible(this, it) }
  return savedOrder + visibleColumns
}

internal fun VcsLogUiProperties.moveColumn(column: VcsLogColumn<*>, newIndex: Int) = updateOrder { order ->
  order.remove(column)
  order.add(newIndex, column)
}

internal fun VcsLogUiProperties.addColumn(column: VcsLogColumn<*>) = updateOrder { order ->
  order.add(column)
}

internal fun VcsLogUiProperties.removeColumn(column: VcsLogColumn<*>) = updateOrder { order ->
  order.remove(column)
}

internal fun VcsLogUiProperties.updateOrder(newOrder: List<VcsLogColumn<*>>) {
  set(COLUMN_ID_ORDER, newOrder.map { it.id }.distinct())
}

private fun VcsLogUiProperties.updateOrder(update: (MutableList<VcsLogColumn<*>>) -> Unit) {
  val order = getColumnsOrder().toMutableList()
  update(order)
  updateOrder(order)
}