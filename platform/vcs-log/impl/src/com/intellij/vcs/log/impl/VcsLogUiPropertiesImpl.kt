// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.MainVcsLogUiProperties.VcsLogHighlighterProperty
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.table.column.TableColumnWidthProperty
import com.intellij.vcs.log.util.GraphOptionsUtil.toGraphOptions
import com.intellij.vcs.log.util.GraphOptionsUtil.toStringList
import java.util.*

/**
 * Stores UI configuration based on user activity and preferences.
 */
abstract class VcsLogUiPropertiesImpl<S : VcsLogUiPropertiesImpl.State>(private val appSettings: VcsLogApplicationSettings) : MainVcsLogUiProperties {
  private val eventDispatcher = EventDispatcher.create(PropertiesChangeListener::class.java)
  protected abstract val logUiState: S

  @OptIn(ExperimentalStdlibApi::class)
  override fun <T> get(property: VcsLogUiProperty<T>): T {
    if (appSettings.exists(property)) {
      return appSettings[property]
    }
    val state = logUiState
    val result: Any = when (property) {
      is VcsLogHighlighterProperty -> state.highlighters[property.id] ?: true
      is TableColumnWidthProperty -> state.columnIdWidth[property.name] ?: -1
      CommonUiProperties.SHOW_DETAILS -> state.isShowDetails
      MainVcsLogUiProperties.SHOW_LONG_EDGES -> state.isShowLongEdges
      CommonUiProperties.SHOW_ROOT_NAMES -> state.isShowRootNames
      MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES -> state.isShowOnlyAffectedChanges
      MainVcsLogUiProperties.GRAPH_OPTIONS -> {
        val bekSortType = state.bekSortType
        if (bekSortType != 0) {
          val migratedOptions = if (bekSortType == 2) PermanentGraph.Options.LinearBek
          else PermanentGraph.Options.Base(PermanentGraph.SortType.Bek)
          state.graphOptions = migratedOptions.toStringList()
          state.bekSortType = 0
          migratedOptions
        }
        else {
          state.graphOptions.toGraphOptions() ?: PermanentGraph.Options.Default
        }
      }
      MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE -> logUiState.textFilterSettings.isMatchCase
      MainVcsLogUiProperties.TEXT_FILTER_REGEX -> logUiState.textFilterSettings.isRegex
      else -> throw UnsupportedOperationException("Property $property does not exist")
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  override fun <T> set(property: VcsLogUiProperty<T>, value: T) {
    if (appSettings.exists(property)) {
      appSettings[property] = value
      return
    }

    when (property) {
      CommonUiProperties.SHOW_DETAILS -> logUiState.isShowDetails = value as Boolean
      MainVcsLogUiProperties.SHOW_LONG_EDGES -> logUiState.isShowLongEdges = value as Boolean
      CommonUiProperties.SHOW_ROOT_NAMES -> logUiState.isShowRootNames = value as Boolean
      MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES -> logUiState.isShowOnlyAffectedChanges = value as Boolean
      MainVcsLogUiProperties.GRAPH_OPTIONS -> logUiState.graphOptions = (value as PermanentGraph.Options).toStringList()
      MainVcsLogUiProperties.TEXT_FILTER_REGEX -> logUiState.textFilterSettings.isRegex = value as Boolean
      MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE -> logUiState.textFilterSettings.isMatchCase = value as Boolean
      is VcsLogHighlighterProperty -> logUiState.highlighters[(property as VcsLogHighlighterProperty).id] = value as Boolean
      is TableColumnWidthProperty -> logUiState.columnIdWidth[property.name] = value as Int
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
      logUiState.filters[filterName] = values
    }
    else {
      logUiState.filters.remove(filterName)
    }
  }

  override fun getFilterValues(filterName: String): List<String>? {
    return logUiState.filters[filterName]
  }

  override fun addChangeListener(listener: PropertiesChangeListener) {
    eventDispatcher.addListener(listener)
    appSettings.addChangeListener(listener)
  }

  override fun addChangeListener(listener: PropertiesChangeListener, parent: Disposable) {
    eventDispatcher.addListener(listener, parent)
    appSettings.addChangeListener(listener, parent)
  }

  override fun removeChangeListener(listener: PropertiesChangeListener) {
    eventDispatcher.removeListener(listener)
    appSettings.removeChangeListener(listener)
  }

  open class State {
    @get:OptionTag("SHOW_DETAILS_IN_CHANGES")
    var isShowDetails = true

    @get:OptionTag("LONG_EDGES_VISIBLE")
    var isShowLongEdges = false

    @get:OptionTag("BEK_SORT_TYPE")
    var bekSortType = 0

    @get:OptionTag("GRAPH_OPTIONS")
    var graphOptions: List<String> = PermanentGraph.Options.Default.toStringList()

    @get:OptionTag("SHOW_ROOT_NAMES")
    var isShowRootNames = false

    @get:OptionTag("SHOW_ONLY_AFFECTED_CHANGES")
    var isShowOnlyAffectedChanges = false

    @get:OptionTag("HIGHLIGHTERS")
    var highlighters: MutableMap<String, Boolean> = TreeMap()

    @get:OptionTag("FILTERS")
    var filters: MutableMap<String, List<String>> = TreeMap()

    @get:OptionTag("TEXT_FILTER_SETTINGS")
    var textFilterSettings: TextFilterSettings = TextFilterSettings()

    @get:OptionTag("COLUMN_ID_WIDTH")
    var columnIdWidth: MutableMap<String, Int> = HashMap()
  }

  class TextFilterSettings {
    @get:OptionTag("REGEX")
    var isRegex = false

    @get:OptionTag("MATCH_CASE")
    var isMatchCase = false
  }

  companion object {
    private val SUPPORTED_PROPERTIES = setOf(
      CommonUiProperties.SHOW_DETAILS,
      MainVcsLogUiProperties.SHOW_LONG_EDGES,
      MainVcsLogUiProperties.GRAPH_OPTIONS,
      CommonUiProperties.SHOW_ROOT_NAMES,
      MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES,
      MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE,
      MainVcsLogUiProperties.TEXT_FILTER_REGEX)
  }
}
