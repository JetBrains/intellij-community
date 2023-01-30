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
    if (property is CustomBooleanProperty) {
      var value = _state.CUSTOM_BOOLEAN_PROPERTIES[property.getName()]
      if (value == null) {
        value = (property as CustomBooleanProperty).defaultValue()
      }
      return value as T
    }
    if (property is TableColumnVisibilityProperty) {
      val visibilityProperty = property as TableColumnVisibilityProperty
      val isVisible = _state.COLUMN_ID_VISIBILITY[visibilityProperty.name]
      if (isVisible != null) {
        return isVisible as T
      }

      // visibility is not set, so we will get it from current/default order
      // otherwise column will be visible but not exist in order
      val column = visibilityProperty.column
      if (get(CommonUiProperties.COLUMN_ID_ORDER).contains(column.id)) {
        return true as T
      }
      if (column is VcsLogCustomColumn<*>) {
        return column.isEnabledByDefault() as T
      }
      else return false as T
    }
    return property.match()
      .ifEq(CommonUiProperties.COMPACT_REFERENCES_VIEW).then(_state.COMPACT_REFERENCES_VIEW)
      .ifEq(CommonUiProperties.SHOW_TAG_NAMES).then(_state.SHOW_TAG_NAMES)
      .ifEq(CommonUiProperties.LABELS_LEFT_ALIGNED).then(_state.LABELS_LEFT_ALIGNED)
      .ifEq(MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS).then(_state.SHOW_CHANGES_FROM_PARENTS)
      .ifEq(CommonUiProperties.SHOW_DIFF_PREVIEW).then(_state.SHOW_DIFF_PREVIEW)
      .ifEq(MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT).then(_state.DIFF_PREVIEW_VERTICAL_SPLIT)
      .ifEq(CommonUiProperties.PREFER_COMMIT_DATE).then(_state.PREFER_COMMIT_DATE)
      .ifEq(CommonUiProperties.COLUMN_ID_ORDER).thenGet {
        val order = _state.COLUMN_ID_ORDER
        if (!order.isNullOrEmpty()) {
          return@thenGet order
        }
        listOf(Root, Commit, Author, Date).map { it.id }
      }
      .get()
  }

  override fun <T : Any> set(property: VcsLogUiProperty<T>, value: T) {
    if (property is CustomBooleanProperty) {
      _state.CUSTOM_BOOLEAN_PROPERTIES[property.getName()] = value as Boolean
    }
    else if (CommonUiProperties.COMPACT_REFERENCES_VIEW == property) {
      _state.COMPACT_REFERENCES_VIEW = value as Boolean
    }
    else if (CommonUiProperties.SHOW_TAG_NAMES == property) {
      _state.SHOW_TAG_NAMES = value as Boolean
    }
    else if (CommonUiProperties.LABELS_LEFT_ALIGNED == property) {
      _state.LABELS_LEFT_ALIGNED = value as Boolean
    }
    else if (MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS == property) {
      _state.SHOW_CHANGES_FROM_PARENTS = value as Boolean
    }
    else if (CommonUiProperties.SHOW_DIFF_PREVIEW == property) {
      _state.SHOW_DIFF_PREVIEW = value as Boolean
    }
    else if (MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT == property) {
      _state.DIFF_PREVIEW_VERTICAL_SPLIT = value as Boolean
    }
    else if (CommonUiProperties.PREFER_COMMIT_DATE == property) {
      _state.PREFER_COMMIT_DATE = value as Boolean
    }
    else if (CommonUiProperties.COLUMN_ID_ORDER == property) {
      _state.COLUMN_ID_ORDER = value as List<String>
    }
    else if (property is TableColumnVisibilityProperty) {
      _state.COLUMN_ID_VISIBILITY[property.getName()] = value as Boolean
    }
    else {
      throw UnsupportedOperationException("Property $property does not exist")
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
