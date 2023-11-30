// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.EventDispatcher
import com.intellij.util.xmlb.annotations.OptionTag
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
      is CustomBooleanProperty -> _state.customBooleanProperties[property.name] ?: property.defaultValue()
      is TableColumnVisibilityProperty -> isColumnVisible(_state.columnIdVisibility, property)
      CommonUiProperties.COLUMN_ID_ORDER -> getColumnOrder()
      CommonUiProperties.COMPACT_REFERENCES_VIEW -> _state.isCompactReferenceView
      CommonUiProperties.SHOW_TAG_NAMES -> _state.isShowTagNames
      CommonUiProperties.LABELS_LEFT_ALIGNED -> _state.isLabelsLeftAligned
      MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS -> _state.isShowChangesFromParents
      CommonUiProperties.SHOW_DIFF_PREVIEW -> _state.isShowDiffPreview
      MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT -> _state.isDiffPreviewVerticalSplit
      CommonUiProperties.PREFER_COMMIT_DATE -> _state.isPreferCommitDate
      else -> throw UnsupportedOperationException("Property $property does not exist")
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  private fun getColumnOrder(): List<String> {
    val order = _state.columnIdOrder
    if (order.isNullOrEmpty()) return listOf(Root, Commit, Author, Date).map { it.id }
    return order
  }

  override fun <T : Any> set(property: VcsLogUiProperty<T>, value: T) {
    @Suppress("UNCHECKED_CAST")
    when (property) {
      is CustomBooleanProperty -> _state.customBooleanProperties[property.getName()] = value as Boolean
      CommonUiProperties.COMPACT_REFERENCES_VIEW -> _state.isCompactReferenceView = value as Boolean
      CommonUiProperties.SHOW_TAG_NAMES -> _state.isShowTagNames = value as Boolean
      CommonUiProperties.LABELS_LEFT_ALIGNED -> _state.isLabelsLeftAligned = value as Boolean
      MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS -> _state.isShowChangesFromParents = value as Boolean
      CommonUiProperties.SHOW_DIFF_PREVIEW -> _state.isShowDiffPreview = value as Boolean
      MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT -> _state.isDiffPreviewVerticalSplit = value as Boolean
      CommonUiProperties.PREFER_COMMIT_DATE -> _state.isPreferCommitDate = value as Boolean
      CommonUiProperties.COLUMN_ID_ORDER -> _state.columnIdOrder = value as List<String>
      is TableColumnVisibilityProperty -> _state.columnIdVisibility[property.name] = value as Boolean
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

  override fun addChangeListener(listener: PropertiesChangeListener, parent: Disposable) {
    eventDispatcher.addListener(listener, parent)
  }

  override fun removeChangeListener(listener: PropertiesChangeListener) {
    eventDispatcher.removeListener(listener)
  }

  class State {
    @get:OptionTag("COMPACT_REFERENCES_VIEW")
    var isCompactReferenceView = true

    @get:OptionTag("SHOW_TAG_NAMES")
    var isShowTagNames = false

    @get:OptionTag("LABELS_LEFT_ALIGNED")
    var isLabelsLeftAligned = false

    @get:OptionTag("SHOW_CHANGES_FROM_PARENTS")
    var isShowChangesFromParents = false

    @get:OptionTag("SHOW_DIFF_PREVIEW")
    var isShowDiffPreview = false

    @get:OptionTag("DIFF_PREVIEW_VERTICAL_SPLIT")
    var isDiffPreviewVerticalSplit = true

    @get:OptionTag("PREFER_COMMIT_DATE")
    var isPreferCommitDate = false

    @get:OptionTag("COLUMN_ID_ORDER")
    var columnIdOrder: List<String>? = ArrayList()

    @get:OptionTag("COLUMN_ID_VISIBILITY")
    var columnIdVisibility: MutableMap<String, Boolean> = HashMap()

    @get:OptionTag("CUSTOM_BOOLEAN_PROPERTIES")
    var customBooleanProperties: MutableMap<String, Boolean> = HashMap()
  }

  open class CustomBooleanProperty(name: @NonNls String) : VcsLogUiProperty<Boolean>(name) {
    open fun defaultValue() = false
  }
}

internal fun VcsLogUiProperties.isColumnVisible(columnIdVisibility: Map<String, Boolean>, visibilityProperty: TableColumnVisibilityProperty): Boolean {
  val isVisible = columnIdVisibility[visibilityProperty.name]
  if (isVisible != null) return isVisible

  // visibility is not set, so we will get it from current/default order
  // otherwise column will be visible but not exist in order
  val column = visibilityProperty.column
  if (get(CommonUiProperties.COLUMN_ID_ORDER).contains(column.id)) return true
  if (column is VcsLogCustomColumn<*>) return column.isEnabledByDefault()
  return false
}
