// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.table.VcsLogColumn
import java.util.*

@Service
internal class VcsLogColumnsWidthStorage {
  companion object {
    @JvmStatic
    fun getInstance() = service<VcsLogColumnsWidthStorage>()
  }

  private val columnsWidth = EnumMap<VcsLogColumn, VcsLogUiProperty<Int>>(VcsLogColumn::class.java)

  fun saveColumnWidth(properties: VcsLogUiProperties, column: VcsLogColumn, width: Int): Unit = properties.run {
    val property = getProperty(column)
    if (exists(property)) {
      if (get(property) != width) {
        set(property, width)
      }
    }
  }

  fun getColumnWidth(properties: VcsLogUiProperties, column: VcsLogColumn): Int = properties.run {
    val property = getProperty(column)
    return if (exists(property)) {
      get(property)
    }
    else {
      -1
    }
  }

  private fun getProperty(column: VcsLogColumn) = columnsWidth.getOrPut(column) { TableColumnProperty(column) }

  class TableColumnProperty(val column: VcsLogColumn) : VcsLogUiProperty<Int>("Table.${column.getName()}ColumnWidth") {
    val columnIndex = column.ordinal
  }
}