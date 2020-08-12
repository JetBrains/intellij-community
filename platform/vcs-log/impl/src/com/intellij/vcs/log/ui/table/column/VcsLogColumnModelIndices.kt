// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.vcs.log.ui.table.VcsLogColumn

@Service
internal class VcsLogColumnModelIndices {
  companion object {
    private val defaultColumns = listOf(VcsLogColumn.ROOT, VcsLogColumn.COMMIT, VcsLogColumn.AUTHOR, VcsLogColumn.DATE, VcsLogColumn.HASH)

    @JvmField
    val DEFAULT_DYNAMIC_COLUMNS = defaultColumns.filter { it.isDynamic }

    @JvmStatic
    fun getInstance() = service<VcsLogColumnModelIndices>()
  }

  private val modelIndices = HashMap<String, Int>()

  private val currentColumns = ArrayList<VcsLogColumn>()

  private val currentColumnIndices = HashMap<Int, VcsLogColumn>()

  init {
    defaultColumns.forEach { column ->
      newColumn(column)
    }
  }

  fun getModelIndex(column: VcsLogColumn): Int = modelIndices[column.id]!!

  fun getColumn(modelIndex: Int): VcsLogColumn = currentColumnIndices[modelIndex]!!

  fun getModelColumnsCount(): Int = modelIndices.size

  fun getDynamicColumns() = currentColumns.filter { it.isDynamic }

  private fun newColumn(column: VcsLogColumn) {
    val newIndex = modelIndices.size
    val modelIndex = modelIndices.getOrPut(column.id) { newIndex }
    if (modelIndex !in currentColumnIndices) {
      currentColumns.add(column)
      currentColumnIndices[modelIndex] = column
    }
  }
}