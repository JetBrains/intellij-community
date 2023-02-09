// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import org.jetbrains.annotations.ApiStatus

internal fun VcsLogUiProperties.supportsColumnsReordering() = exists(CommonUiProperties.COLUMN_ID_ORDER)

internal fun VcsLogUiProperties.supportsColumnsToggling(): Boolean {
  val commitColumnProperties = VcsLogColumnManager.getInstance().getProperties(Commit)
  return exists(commitColumnProperties.visibility) && supportsColumnsReordering()
}

internal fun isValidColumnOrder(columnOrder: List<VcsLogColumn<*>>): Boolean {
  return Root in columnOrder && Commit in columnOrder
}

/**
 * Provides a list of visible [VcsLogColumn] ordered based on the saved state.
 * If [VcsLogColumn] is visible and its state were not saved, it will go after ordered columns.
 *
 * Columns visibility is checked using [isVisible] method.
 *
 * @see moveColumn
 * @see addColumn
 * @see removeColumn
 * @see updateOrder
 */
internal fun VcsLogUiProperties.getColumnsOrder(): List<VcsLogColumn<*>> {
  val currentColumns = VcsLogColumnManager.getInstance().getCurrentColumns().filter { it.isVisible(this) }
  val savedOrder = get(CommonUiProperties.COLUMN_ID_ORDER).mapNotNull { id -> currentColumns.find { it.id == id } }
  val visibleColumns = currentColumns - savedOrder
  return savedOrder + visibleColumns
}

internal fun VcsLogUiProperties.moveColumn(column: VcsLogColumn<*>, newIndex: Int) = updateOrder { order ->
  order.remove(column)
  order.add(newIndex, column)
}

internal fun VcsLogUiProperties.addColumn(column: VcsLogColumn<*>) {
  column.changeVisibility(this, true)
  updateOrder { order ->
    order.add(column)
  }
}

internal fun VcsLogUiProperties.removeColumn(column: VcsLogColumn<*>) {
  column.changeVisibility(this, false)
  updateOrder { order ->
    order.remove(column)
  }
}

internal fun VcsLogUiProperties.updateOrder(newOrder: List<VcsLogColumn<*>>) {
  set(CommonUiProperties.COLUMN_ID_ORDER, newOrder.map { it.id }.distinct())
}

@ApiStatus.Internal
fun VcsLogColumn<*>.isVisible(properties: VcsLogUiProperties): Boolean = withColumnProperties { columnProperties ->
  properties.getPropertyValue(columnProperties.visibility, true)
}

internal fun VcsLogColumn<*>.changeVisibility(properties: VcsLogUiProperties, value: Boolean) = withColumnProperties { columnProperties ->
  properties.changeProperty(columnProperties.visibility, value)
}

internal fun VcsLogColumn<*>.getWidth(properties: VcsLogUiProperties): Int = withColumnProperties { columnProperties ->
  properties.getPropertyValue(columnProperties.width, -1)
}

internal fun VcsLogColumn<*>.setWidth(properties: VcsLogUiProperties, value: Int) = withColumnProperties { columnProperties ->
  properties.changeProperty(columnProperties.width, value)
}


private fun VcsLogUiProperties.updateOrder(update: (MutableList<VcsLogColumn<*>>) -> Unit) {
  val order = getColumnsOrder().toMutableList()
  update(order)
  updateOrder(order)
}

private fun <T> VcsLogColumn<*>.withColumnProperties(block: (VcsLogColumnProperties) -> T): T {
  val properties = VcsLogColumnManager.getInstance().getProperties(this)
  return block(properties)
}

private fun <T : Any> VcsLogUiProperties.changeProperty(property: VcsLogUiProperty<T>, value: T) {
  if (exists(property)) {
    if (get(property) != value) {
      set(property, value)
    }
  }
}

private fun <T> VcsLogUiProperties.getPropertyValue(property: VcsLogUiProperty<T>, defaultValue: T): T {
  return if (exists(property)) {
    get(property)
  }
  else {
    defaultValue
  }
}