// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column.storage

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.table.VcsLogColumnDeprecated
import com.intellij.vcs.log.ui.table.column.VcsLogColumn

@Service
internal class VcsLogColumnsWidthStorage : Disposable {
  companion object {
    @JvmStatic
    fun getInstance() = service<VcsLogColumnsWidthStorage>()
  }

  private val propertyStorage = VcsLogColumnsPropertyStorage(this, -1) {
    TableColumnWidthProperty(it)
  }

  fun saveColumnWidth(properties: VcsLogUiProperties, column: VcsLogColumn<*>, width: Int) {
    propertyStorage.changeProperty(properties, column, width)
  }

  fun getColumnWidth(properties: VcsLogUiProperties, column: VcsLogColumn<*>): Int = propertyStorage.getPropertyValue(properties, column)

  override fun dispose() {
  }

  class TableColumnWidthProperty(val column: VcsLogColumn<*>) : VcsLogUiProperty<Int>("Table.${column.id}.ColumnIdWidth") {
    fun moveOldSettings(oldMapping: Map<Int, Int>, newMapping: MutableMap<String, Int>) {
      val oldValue = oldMapping.map { (column, width) -> VcsLogColumnDeprecated.getVcsLogColumnEx(column) to width }.toMap()[column]
      if (name !in newMapping && oldValue != null) {
        newMapping[name] = oldValue
      }
    }
  }
}