// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.EventDispatcher
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.table.column.*
import org.jetbrains.annotations.NonNls

@State(name = "Vcs.Log.App.Settings", storages = [Storage("vcs.xml")], category = SettingsCategory.TOOLS)
class VcsLogApplicationSettings : PersistentStateComponent<VcsLogApplicationSettings.State?>, VcsLogUiProperties {
  private val eventDispatcher = EventDispatcher.create(PropertiesChangeListener::class.java)
  private var _state = State()

  override fun getState(): State = _state

  override fun loadState(state: State) {
    _state = state
  }

  override fun <T : Any> get(property: VcsLogUiProperty<T>): T {
    val result: Any = when (property) {
      is CustomBooleanProperty -> _state.CUSTOM_BOOLEAN_PROPERTIES[property.name] ?: property.defaultValue()
      is TableColumnVisibilityProperty -> isColumnVisible(property)
      CommonUiProperties.COLUMN_ID_ORDER -> getColumnOrder()
      CommonUiProperties.COMPACT_REFERENCES_VIEW -> _state.COMPACT_REFERENCES_VIEW
      CommonUiProperties.SHOW_TAG_NAMES -> _state.SHOW_TAG_NAMES
      CommonUiProperties.LABELS_LEFT_ALIGNED -> _state.LABELS_LEFT_ALIGNED
      MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS -> _state.SHOW_CHANGES_FROM_PARENTS
      CommonUiProperties.SHOW_DIFF_PREVIEW -> _state.SHOW_DIFF_PREVIEW
      MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT -> _state.DIFF_PREVIEW_VERTICAL_SPLIT
      CommonUiProperties.PREFER_COMMIT_DATE -> _state.PREFER_COMMIT_DATE
      else -> throw UnsupportedOperationException("Property $property does not exist")
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  private fun isColumnVisible(visibilityProperty: TableColumnVisibilityProperty): Boolean {
    val isVisible = _state.COLUMN_ID_VISIBILITY[visibilityProperty.name]
    if (isVisible != null) return isVisible

    // visibility is not set, so we will get it from current/default order
    // otherwise column will be visible but not exist in order
    val column = visibilityProperty.column
    if (get(CommonUiProperties.COLUMN_ID_ORDER).contains(column.id)) return true
    if (column is VcsLogCustomColumn<*>) return column.isEnabledByDefault()
    return false
  }

  private fun getColumnOrder(): List<String> {
    val order = _state.COLUMN_ID_ORDER
    if (order.isNullOrEmpty()) return listOf(Root, Commit, Author, Date).map { it.id }
    return order
  }

  override fun <T : Any> set(property: VcsLogUiProperty<T>, value: T) {
    @Suppress("UNCHECKED_CAST")
    when (property) {
      is CustomBooleanProperty -> _state.CUSTOM_BOOLEAN_PROPERTIES[property.getName()] = value as Boolean
      CommonUiProperties.COMPACT_REFERENCES_VIEW -> _state.COMPACT_REFERENCES_VIEW = value as Boolean
      CommonUiProperties.SHOW_TAG_NAMES -> _state.SHOW_TAG_NAMES = value as Boolean
      CommonUiProperties.LABELS_LEFT_ALIGNED -> _state.LABELS_LEFT_ALIGNED = value as Boolean
      MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS -> _state.SHOW_CHANGES_FROM_PARENTS = value as Boolean
      CommonUiProperties.SHOW_DIFF_PREVIEW -> _state.SHOW_DIFF_PREVIEW = value as Boolean
      MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT -> _state.DIFF_PREVIEW_VERTICAL_SPLIT = value as Boolean
      CommonUiProperties.PREFER_COMMIT_DATE -> _state.PREFER_COMMIT_DATE = value as Boolean
      CommonUiProperties.COLUMN_ID_ORDER -> _state.COLUMN_ID_ORDER = value as List<String>
      is TableColumnVisibilityProperty -> _state.COLUMN_ID_VISIBILITY[property.name] = value as Boolean
      else -> throw UnsupportedOperationException("Property $property does not exist")
    }
    eventDispatcher.multicaster.onPropertyChanged(property)
  }

  override fun <T> exists(property: VcsLogUiProperty<T>): Boolean {
    return property is CustomBooleanProperty ||
           CommonUiProperties.COMPACT_REFERENCES_VIEW == property ||
           CommonUiProperties.SHOW_TAG_NAMES == property ||
           CommonUiProperties.LABELS_LEFT_ALIGNED == property ||
           CommonUiProperties.SHOW_DIFF_PREVIEW == property ||
           MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT == property ||
           MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS == property ||
           CommonUiProperties.COLUMN_ID_ORDER == property ||
           CommonUiProperties.PREFER_COMMIT_DATE == property ||
           property is TableColumnVisibilityProperty
  }

  override fun addChangeListener(listener: PropertiesChangeListener) {
    eventDispatcher.addListener(listener)
  }

  override fun removeChangeListener(listener: PropertiesChangeListener) {
    eventDispatcher.removeListener(listener)
  }

  class State {
    var COMPACT_REFERENCES_VIEW = true
    var SHOW_TAG_NAMES = false
    var LABELS_LEFT_ALIGNED = false
    var SHOW_CHANGES_FROM_PARENTS = false
    var SHOW_DIFF_PREVIEW = false
    var DIFF_PREVIEW_VERTICAL_SPLIT = true
    var PREFER_COMMIT_DATE = false
    var COLUMN_ID_ORDER: List<String>? = ArrayList()
    var COLUMN_ID_VISIBILITY: MutableMap<String, Boolean> = HashMap()
    var CUSTOM_BOOLEAN_PROPERTIES: MutableMap<String, Boolean> = HashMap()
  }

  open class CustomBooleanProperty(name: @NonNls String) : VcsLogUiProperty<Boolean>(name) {
    open fun defaultValue() = false
  }
}
