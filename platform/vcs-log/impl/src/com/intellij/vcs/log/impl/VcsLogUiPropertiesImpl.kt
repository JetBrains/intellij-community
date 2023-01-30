// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.util.EventDispatcher
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.MainVcsLogUiProperties.VcsLogHighlighterProperty
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.table.column.TableColumnWidthProperty
import java.util.*

/**
 * Stores UI configuration based on user activity and preferences.
 */
abstract class VcsLogUiPropertiesImpl<S : VcsLogUiPropertiesImpl.State>(private val appSettings: VcsLogApplicationSettings) : MainVcsLogUiProperties {
  private val eventDispatcher = EventDispatcher.create(PropertiesChangeListener::class.java)
  protected abstract val logUiState: S

  override fun <T : Any> get(property: VcsLogUiProperty<T>): T {
    if (appSettings.exists(property)) {
      return appSettings.get(property)
    }
    val state = logUiState
    val result: Any = when (property) {
      is VcsLogHighlighterProperty -> state.HIGHLIGHTERS[property.id] ?: true
      is TableColumnWidthProperty -> state.COLUMN_ID_WIDTH[property.name] ?: -1
      CommonUiProperties.SHOW_DETAILS -> state.SHOW_DETAILS_IN_CHANGES
      MainVcsLogUiProperties.SHOW_LONG_EDGES -> state.LONG_EDGES_VISIBLE
      CommonUiProperties.SHOW_ROOT_NAMES -> state.SHOW_ROOT_NAMES
      MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES -> state.SHOW_ONLY_AFFECTED_CHANGES
      MainVcsLogUiProperties.BEK_SORT_TYPE -> PermanentGraph.SortType.values()[state.BEK_SORT_TYPE]
      MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE -> logUiState.TEXT_FILTER_SETTINGS.MATCH_CASE
      MainVcsLogUiProperties.TEXT_FILTER_REGEX -> logUiState.TEXT_FILTER_SETTINGS.REGEX
      else -> throw UnsupportedOperationException("Property $property does not exist")
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  override fun <T : Any> set(property: VcsLogUiProperty<T>, value: T) {
    if (appSettings.exists(property)) {
      appSettings.set(property, value)
      return
    }

    when (property) {
      CommonUiProperties.SHOW_DETAILS -> logUiState.SHOW_DETAILS_IN_CHANGES = value as Boolean
      MainVcsLogUiProperties.SHOW_LONG_EDGES -> logUiState.LONG_EDGES_VISIBLE = value as Boolean
      CommonUiProperties.SHOW_ROOT_NAMES -> logUiState.SHOW_ROOT_NAMES = value as Boolean
      MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES -> logUiState.SHOW_ONLY_AFFECTED_CHANGES = value as Boolean
      MainVcsLogUiProperties.BEK_SORT_TYPE -> logUiState.BEK_SORT_TYPE = (value as PermanentGraph.SortType).ordinal
      MainVcsLogUiProperties.TEXT_FILTER_REGEX -> logUiState.TEXT_FILTER_SETTINGS.REGEX = value as Boolean
      MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE -> logUiState.TEXT_FILTER_SETTINGS.MATCH_CASE = value as Boolean
      is VcsLogHighlighterProperty -> logUiState.HIGHLIGHTERS[(property as VcsLogHighlighterProperty).id] = value as Boolean
      is TableColumnWidthProperty -> logUiState.COLUMN_ID_WIDTH[property.getName()] = value as Int
      else -> throw UnsupportedOperationException("Property $property does not exist")
    }
    onPropertyChanged(property)
  }

  protected fun <T> onPropertyChanged(property: VcsLogUiProperty<T>) {
    eventDispatcher.multicaster.onPropertyChanged(property)
  }

  override fun <T> exists(property: VcsLogUiProperty<T>): Boolean {
    return appSettings.exists(property) ||
           SUPPORTED_PROPERTIES.contains(property) ||
           property is VcsLogHighlighterProperty ||
           property is TableColumnWidthProperty
  }

  override fun saveFilterValues(filterName: String, values: List<String>?) {
    if (values != null) {
      logUiState.FILTERS[filterName] = values
    }
    else {
      logUiState.FILTERS.remove(filterName)
    }
  }

  override fun getFilterValues(filterName: String): List<String>? {
    return logUiState.FILTERS[filterName]
  }

  override fun addChangeListener(listener: PropertiesChangeListener) {
    eventDispatcher.addListener(listener)
    appSettings.addChangeListener(listener)
  }

  override fun removeChangeListener(listener: PropertiesChangeListener) {
    eventDispatcher.removeListener(listener)
    appSettings.removeChangeListener(listener)
  }

  open class State {
    var SHOW_DETAILS_IN_CHANGES = true
    var LONG_EDGES_VISIBLE = false
    var BEK_SORT_TYPE = 0
    var SHOW_ROOT_NAMES = false
    var SHOW_ONLY_AFFECTED_CHANGES = false
    var HIGHLIGHTERS: MutableMap<String, Boolean> = TreeMap()
    var FILTERS: MutableMap<String, List<String>> = TreeMap()
    var TEXT_FILTER_SETTINGS: TextFilterSettings = TextFilterSettings()
    var COLUMN_ID_WIDTH: MutableMap<String, Int> = HashMap()
  }

  class TextFilterSettings {
    var REGEX = false
    var MATCH_CASE = false
  }

  companion object {
    private val SUPPORTED_PROPERTIES = setOf(
      CommonUiProperties.SHOW_DETAILS,
      MainVcsLogUiProperties.SHOW_LONG_EDGES,
      MainVcsLogUiProperties.BEK_SORT_TYPE,
      CommonUiProperties.SHOW_ROOT_NAMES,
      MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES,
      MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE,
      MainVcsLogUiProperties.TEXT_FILTER_REGEX)
  }
}
