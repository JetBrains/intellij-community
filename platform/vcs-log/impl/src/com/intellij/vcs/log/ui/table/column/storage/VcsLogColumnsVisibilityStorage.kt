// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column.storage

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.table.column.VcsLogColumn

@Service
internal class VcsLogColumnsVisibilityStorage : Disposable {
  companion object {
    @JvmStatic
    fun getInstance() = service<VcsLogColumnsVisibilityStorage>()
  }

  private val propertyStorage = VcsLogColumnsPropertyStorage(this, true) {
    TableColumnVisibilityProperty(it)
  }

  fun changeVisibility(properties: VcsLogUiProperties, column: VcsLogColumn<*>, visible: Boolean) {
    propertyStorage.changeProperty(properties, column, visible)
  }

  fun isVisible(properties: VcsLogUiProperties, column: VcsLogColumn<*>): Boolean = propertyStorage.getPropertyValue(properties, column)

  override fun dispose() {
  }

  class TableColumnVisibilityProperty(val column: VcsLogColumn<*>) : VcsLogUiProperty<Boolean>("Table.${column.id}.ColumnIdVisibility")
}