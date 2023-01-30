// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.EventDispatcher
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.VcsLogApplicationSettings
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.table.column.*
import com.intellij.vcs.log.ui.table.column.Date
import java.util.*
import kotlin.collections.set

@State(name = "Vcs.Log.History.Properties", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class FileHistoryUiProperties : VcsLogUiProperties, PersistentStateComponent<FileHistoryUiProperties.State> {
  private val eventDispatcher = EventDispatcher.create(PropertiesChangeListener::class.java)
  private val appSettings = ApplicationManager.getApplication().getService(VcsLogApplicationSettings::class.java)
  private val applicationSettingsListener: PropertiesChangeListener = object : PropertiesChangeListener {
    override fun <T> onPropertyChanged(property: VcsLogUiProperty<T>) {
      onApplicationSettingChange(property)
    }
  }
  private val applicationLevelProperties = setOf<VcsLogUiProperty<*>>(CommonUiProperties.PREFER_COMMIT_DATE,
                                                                      CommonUiProperties.COMPACT_REFERENCES_VIEW,
                                                                      CommonUiProperties.SHOW_TAG_NAMES,
                                                                      CommonUiProperties.LABELS_LEFT_ALIGNED)
  private var _state = State()

  override fun <T : Any> get(property: VcsLogUiProperty<T>): T {
    if (property is TableColumnWidthProperty) {
      val savedWidth = _state.COLUMN_ID_WIDTH[property.getName()] ?: return -1 as T
      return savedWidth as T
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
      return false as T
    }
    return if (applicationLevelProperties.contains(property)) {
      appSettings.get(property)
    }
    else property.match()
      .ifEq(CommonUiProperties.SHOW_DETAILS).then(_state.SHOW_DETAILS)
      .ifEq(SHOW_ALL_BRANCHES).then(_state.SHOW_OTHER_BRANCHES)
      .ifEq(CommonUiProperties.SHOW_DIFF_PREVIEW).then(_state.SHOW_DIFF_PREVIEW)
      .ifEq(CommonUiProperties.SHOW_ROOT_NAMES).then(_state.SHOW_ROOT_NAMES)
      .ifEq(CommonUiProperties.COLUMN_ID_ORDER).thenGet {
        val order = _state.COLUMN_ID_ORDER
        if (!order.isNullOrEmpty()) {
          return@thenGet order
        }
        listOf(Root, Author, Date, Commit).map { it.id }
      }
      .get()
  }

  private fun <T> onApplicationSettingChange(property: VcsLogUiProperty<T>) {
    if (applicationLevelProperties.contains(property)) {
      eventDispatcher.multicaster.onPropertyChanged(property)
    }
  }

  override fun <T : Any> set(property: VcsLogUiProperty<T>, value: T) {
    if (CommonUiProperties.SHOW_DETAILS == property) {
      _state.SHOW_DETAILS = value as Boolean
    }
    else if (SHOW_ALL_BRANCHES == property) {
      _state.SHOW_OTHER_BRANCHES = value as Boolean
    }
    else if (CommonUiProperties.COLUMN_ID_ORDER == property) {
      _state.COLUMN_ID_ORDER = value as List<String>
    }
    else if (property is TableColumnWidthProperty) {
      _state.COLUMN_ID_WIDTH[property.getName()] = value as Int
    }
    else if (property is TableColumnVisibilityProperty) {
      _state.COLUMN_ID_VISIBILITY[property.getName()] = value as Boolean
    }
    else if (CommonUiProperties.SHOW_DIFF_PREVIEW == property) {
      _state.SHOW_DIFF_PREVIEW = value as Boolean
    }
    else if (CommonUiProperties.SHOW_ROOT_NAMES == property) {
      _state.SHOW_ROOT_NAMES = value as Boolean
    }
    else if (applicationLevelProperties.contains(property)) {
      appSettings.set(property, value)
      // listeners will be triggered via onApplicationSettingChange
      return
    }
    else {
      throw UnsupportedOperationException("Unknown property $property")
    }
    eventDispatcher.multicaster.onPropertyChanged(property)
  }

  override fun <T> exists(property: VcsLogUiProperty<T>): Boolean {
    return CommonUiProperties.SHOW_DETAILS == property ||
           SHOW_ALL_BRANCHES == property ||
           CommonUiProperties.COLUMN_ID_ORDER == property ||
           CommonUiProperties.SHOW_DIFF_PREVIEW == property ||
           CommonUiProperties.SHOW_ROOT_NAMES == property ||
           applicationLevelProperties.contains(property) ||
           property is TableColumnWidthProperty ||
           property is TableColumnVisibilityProperty
  }

  override fun getState(): State = _state

  override fun loadState(state: State) {
    _state = state
  }

  override fun addChangeListener(listener: PropertiesChangeListener) {
    if (!eventDispatcher.hasListeners()) {
      appSettings.addChangeListener(applicationSettingsListener)
    }
    eventDispatcher.addListener(listener)
  }

  override fun removeChangeListener(listener: PropertiesChangeListener) {
    eventDispatcher.removeListener(listener)
    if (!eventDispatcher.hasListeners()) {
      appSettings.removeChangeListener(applicationSettingsListener)
    }
  }

  class State {
    var SHOW_DETAILS = false
    var SHOW_OTHER_BRANCHES = false
    var COLUMN_ID_WIDTH: MutableMap<String, Int> = HashMap()
    var COLUMN_ID_ORDER: List<String>? = ArrayList()
    var COLUMN_ID_VISIBILITY: MutableMap<String, Boolean> = HashMap()
    var SHOW_DIFF_PREVIEW = true
    var SHOW_ROOT_NAMES = false
  }

  companion object {
    @JvmField
    val SHOW_ALL_BRANCHES = VcsLogUiProperty<Boolean>("Table.ShowOtherBranches")
  }
}
