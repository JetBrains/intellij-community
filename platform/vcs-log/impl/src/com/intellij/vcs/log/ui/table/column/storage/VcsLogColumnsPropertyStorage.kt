// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column.storage

import com.intellij.openapi.Disposable
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.table.column.VcsLogColumn
import com.intellij.vcs.log.ui.table.column.VcsLogColumnModelIndices
import java.util.*

internal class VcsLogColumnsPropertyStorage<T>(
  parent: Disposable,
  private val defaultValue: T,
  private val createProperty: (VcsLogColumn<*>) -> VcsLogUiProperty<T>
) {
  private val columnsProperty = HashMap<VcsLogColumn<*>, VcsLogUiProperty<T>>()

  init {
    VcsLogColumnModelIndices.getInstance().addCurrentColumnsListener(parent, object : VcsLogColumnModelIndices.CurrentColumnsListener {
      override fun columnRemoved(column: VcsLogColumn<*>) {
        columnsProperty.remove(column)
      }
    })
  }

  fun changeProperty(properties: VcsLogUiProperties, column: VcsLogColumn<*>, value: T): Unit = properties.run {
    val property = getProperty(column)
    if (exists(property)) {
      if (get(property) != value) {
        set(property, value)
      }
    }
  }

  fun getPropertyValue(properties: VcsLogUiProperties, column: VcsLogColumn<*>): T = properties.run {
    val property = getProperty(column)
    return if (exists(property)) {
      get(property)
    }
    else {
      defaultValue
    }
  }

  private fun getProperty(column: VcsLogColumn<*>) = columnsProperty.getOrPut(column) { createProperty(column) }
}