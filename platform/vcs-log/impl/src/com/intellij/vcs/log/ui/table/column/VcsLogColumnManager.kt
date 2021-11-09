// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.EventDispatcher
import com.intellij.vcs.log.ui.frame.VcsCommitExternalStatusProvider
import java.util.*

/**
 * Service stores information about the currently available [VcsLogColumn]s.
 * Allows to get:
 *   * [VcsLogColumn] model index,
 *   * [VcsLogColumn] by index
 *   * [VcsLogColumnProperties] by [VcsLogColumn]
 *
 * Model indices in the service are only incremented, i.e. each new [VcsLogColumn] will have an index greater than the previous ones.
 *
 * This model is used for all Logs (Log, FileHistory, Log tabs).
 *
 * [VcsLogColumn] indices are automatically updated on plugins loading/unloading.
 *
 * @see VcsLogColumnUtilKt with useful column operations
 */
@Service
internal class VcsLogColumnManager : Disposable {
  companion object {
    private val defaultColumns = listOf(Root, Commit, Author, Date, Hash)

    @JvmStatic
    fun getInstance() = service<VcsLogColumnManager>()
  }

  private val modelIndices = HashMap<String, Int>()

  private val currentColumns = ArrayList<VcsLogColumn<*>>()

  private val currentColumnIndices = HashMap<Int, VcsLogColumn<*>>()

  private val columnModelListeners = EventDispatcher.create(ColumnModelListener::class.java)

  private val currentColumnsListeners = EventDispatcher.create(CurrentColumnsListener::class.java)

  private val currentColumnsProperties = HashMap<VcsLogColumn<*>, VcsLogColumnProperties>()

  init {
    defaultColumns.forEach { column ->
      newColumn(column)
    }

    registerCustomColumns()
    registerProvidersColumns()
  }

  private fun registerCustomColumns() {
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

  private fun registerProvidersColumns() {
    val customColumnListener = object : ExtensionPointListener<VcsCommitExternalStatusProvider<*>> {
      override fun extensionAdded(extension: VcsCommitExternalStatusProvider<*>, pluginDescriptor: PluginDescriptor) {
        if (extension is VcsCommitExternalStatusProvider.WithColumn)
          newColumn(extension.logColumn)
      }

      override fun extensionRemoved(extension: VcsCommitExternalStatusProvider<*>, pluginDescriptor: PluginDescriptor) {
        if (extension is VcsCommitExternalStatusProvider.WithColumn)
          forgetColumn(extension.logColumn)
      }
    }
    VcsCommitExternalStatusProvider.EP.point.addExtensionPointListener(customColumnListener, true, this)
  }

  fun getModelIndex(column: VcsLogColumn<*>): Int = modelIndices[column.id]!!

  fun getColumn(modelIndex: Int): VcsLogColumn<*> = currentColumnIndices[modelIndex]!!

  fun getModelColumnsCount(): Int = modelIndices.size

  /**
   * @return currently available columns (default + enabled plugin columns).
   */
  fun getCurrentColumns(): List<VcsLogColumn<*>> = currentColumns

  fun getCurrentDynamicColumns() = currentColumns.filter { it.isDynamic }

  fun getProperties(column: VcsLogColumn<*>): VcsLogColumnProperties = currentColumnsProperties[column]!!

  private fun newColumn(column: VcsLogColumn<*>) {
    val newIndex = modelIndices.size
    val modelIndex = modelIndices.getOrPut(column.id) { newIndex }
    if (modelIndex !in currentColumnIndices) {
      currentColumns.add(column)
      currentColumnIndices[modelIndex] = column
      currentColumnsProperties[column] = VcsLogColumnProperties.create(column)
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
    currentColumnsProperties.remove(column)
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

  /**
   * Allows to handle model update
   */
  interface ColumnModelListener : EventListener {
    fun newColumn(column: VcsLogColumn<*>, modelIndex: Int)
  }

  /**
   * Allows to handle currently available columns
   */
  interface CurrentColumnsListener : EventListener {
    @JvmDefault
    fun columnAdded(column: VcsLogColumn<*>) {
    }

    @JvmDefault
    fun columnRemoved(column: VcsLogColumn<*>) {
    }
  }
}