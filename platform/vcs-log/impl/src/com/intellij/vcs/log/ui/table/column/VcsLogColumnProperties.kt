// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.table.VcsLogColumnDeprecated

data class VcsLogColumnProperties(
  val visibility: TableColumnVisibilityProperty,
  val width: TableColumnWidthProperty
) {
  companion object {
    fun create(column: VcsLogColumn<*>) = VcsLogColumnProperties(
      TableColumnVisibilityProperty(column),
      TableColumnWidthProperty(column)
    )
  }
}

class TableColumnVisibilityProperty(val column: VcsLogColumn<*>) : VcsLogUiProperty<Boolean>("Table.${column.id}.ColumnIdVisibility")

class TableColumnWidthProperty(val column: VcsLogColumn<*>) : VcsLogUiProperty<Int>("Table.${column.id}.ColumnIdWidth") {
  @Deprecated("Should be removed after some releases. Used only for moving old columns width")
  fun moveOldSettings(oldMapping: Map<Int, Int>, newMapping: MutableMap<String, Int>) {
    val oldValue = oldMapping.map { (column, width) -> VcsLogColumnDeprecated.getVcsLogColumnEx(column) to width }.toMap()[column]
    if (name !in newMapping && oldValue != null) {
      newMapping[name] = oldValue
    }
  }
}