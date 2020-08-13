// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.EventDispatcher
import java.util.*
import kotlin.collections.HashMap

@Service
internal class VcsLogColumnModelIndices : Disposable {
  companion object {
    private val defaultColumns = listOf(Root, Commit, Author, Date, Hash)

    @JvmStatic
    fun getInstance() = service<VcsLogColumnModelIndices>()
  }

  private val modelIndices = HashMap<String, Int>()

  private val currentColumns = ArrayList<VcsLogColumn<*>>()

  private val currentColumnIndices = HashMap<Int, VcsLogColumn<*>>()

  private val columnModelListeners = EventDispatcher.create(ColumnModelListener::class.java)

  private val currentColumnsListeners = EventDispatcher.create(CurrentColumnsListener::class.java)

  init {
    defaultColumns.forEach { column ->
      newColumn(column)
    }

    val customColumnListener = object : ExtensionPointListener<VcsLogCustomColumn<*>> {
      override fun extensionAdded(extension: VcsLogCustomColumn<*>, pluginDescriptor: PluginDescriptor) {
        newColumn(extension)
      }

      override fun extensionRemoved(extension: VcsLogCustomColumn<*>, pluginDescriptor: PluginDescriptor) {
        forgetColumn(extension)
      }
    }
    VcsLogCustomColumn.KEY.point.addExtensionPointListener(customColumnListener, true, this)
  }

  fun getModelIndex(column: VcsLogColumn<*>): Int = modelIndices[column.id]!!

  fun getColumn(modelIndex: Int): VcsLogColumn<*> = currentColumnIndices[modelIndex]!!

  fun getModelColumnsCount(): Int = modelIndices.size

  fun getCurrentColumns(): List<VcsLogColumn<*>> = currentColumns

  fun getCurrentDynamicColumns() = currentColumns.filter { it.isDynamic }

  private fun newColumn(column: VcsLogColumn<*>) {
    val newIndex = modelIndices.size
    val modelIndex = modelIndices.getOrPut(column.id) { newIndex }
    if (modelIndex !in currentColumnIndices) {
      currentColumns.add(column)
      currentColumnIndices[modelIndex] = column
      currentColumnsListeners.multicaster.columnAdded(column)
    }
    if (modelIndex == newIndex) {
      columnModelListeners.multicaster.newColumn(column, modelIndex)
    }
  }

  private fun forgetColumn(column: VcsLogColumn<*>) {
    val modelIndex = getModelIndex(column)
    currentColumns.remove(column)
    currentColumnIndices.remove(modelIndex)
    currentColumnsListeners.multicaster.columnRemoved(column)
  }

  fun addColumnModelListener(disposable: Disposable, listener: ColumnModelListener) {
    columnModelListeners.addListener(listener, disposable)
  }

  fun addCurrentColumnsListener(disposable: Disposable, listener: CurrentColumnsListener) {
    currentColumnsListeners.addListener(listener, disposable)
  }

  override fun dispose() {
  }

  interface ColumnModelListener : EventListener {
    fun newColumn(column: VcsLogColumn<*>, modelIndex: Int)
  }

  interface CurrentColumnsListener : EventListener {
    @JvmDefault
    fun columnAdded(column: VcsLogColumn<*>) {
    }

    @JvmDefault
    fun columnRemoved(column: VcsLogColumn<*>) {
    }
  }
}