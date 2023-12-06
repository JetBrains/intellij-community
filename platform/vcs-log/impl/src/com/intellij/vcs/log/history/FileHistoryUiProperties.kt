// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.VcsLogApplicationSettings
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.impl.isColumnVisible
import com.intellij.vcs.log.ui.table.column.*
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
    if (applicationLevelProperties.contains(property)) {
      return appSettings.get(property)
    }

    val result: Any = when (property) {
      is TableColumnWidthProperty -> _state.columnIdWidth[property.name] ?: -1
      is TableColumnVisibilityProperty -> isColumnVisible(_state.columnIdVisibility, property)
      CommonUiProperties.COLUMN_ID_ORDER -> getColumnOrder()
      CommonUiProperties.SHOW_DETAILS -> _state.isShowDetails
      SHOW_ALL_BRANCHES -> _state.isShowOtherBranches
      CommonUiProperties.SHOW_DIFF_PREVIEW -> _state.isShowDiffPreview
      CommonUiProperties.SHOW_ROOT_NAMES -> _state.isShowRootNames
      else -> throw UnsupportedOperationException("Unknown property $property")
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  private fun getColumnOrder(): List<String> {
    val order = _state.columnIdOrder
    if (order.isNullOrEmpty()) return listOf(Root, Author, Date, Commit).map { it.id }
    return order
  }

  private fun <T> onApplicationSettingChange(property: VcsLogUiProperty<T>) {
    if (applicationLevelProperties.contains(property)) {
      eventDispatcher.multicaster.onPropertyChanged(property)
    }
  }

  override fun <T : Any> set(property: VcsLogUiProperty<T>, value: T) {
    if (applicationLevelProperties.contains(property)) {
      appSettings.set(property, value)
      // listeners will be triggered via onApplicationSettingChange
      return
    }

    @Suppress("UNCHECKED_CAST")
    when (property) {
      CommonUiProperties.SHOW_DETAILS -> _state.isShowDetails = value as Boolean
      SHOW_ALL_BRANCHES -> _state.isShowOtherBranches = value as Boolean
      CommonUiProperties.COLUMN_ID_ORDER -> _state.columnIdOrder = value as List<String>
      is TableColumnWidthProperty -> _state.columnIdWidth[property.getName()] = value as Int
      is TableColumnVisibilityProperty -> _state.columnIdVisibility[property.getName()] = value as Boolean
      CommonUiProperties.SHOW_DIFF_PREVIEW -> _state.isShowDiffPreview = value as Boolean
      CommonUiProperties.SHOW_ROOT_NAMES -> _state.isShowRootNames = value as Boolean
      else -> throw UnsupportedOperationException("Unknown property $property")
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

  override fun addChangeListener(listener: PropertiesChangeListener, parent: Disposable) {
    if (!eventDispatcher.hasListeners()) {
      appSettings.addChangeListener(applicationSettingsListener)
      Disposer.register(parent) { removeAppSettingsListenerIfNeeded() }
    }
    eventDispatcher.addListener(listener, parent)
  }

  override fun removeChangeListener(listener: PropertiesChangeListener) {
    eventDispatcher.removeListener(listener)
    removeAppSettingsListenerIfNeeded()
  }

  private fun removeAppSettingsListenerIfNeeded() {
    if (!eventDispatcher.hasListeners()) {
      appSettings.removeChangeListener(applicationSettingsListener)
    }
  }

  class State {
    @get:OptionTag("SHOW_DETAILS")
    var isShowDetails = false

    @get:OptionTag("SHOW_OTHER_BRANCHES")
    var isShowOtherBranches = false

    @get:OptionTag("COLUMN_ID_WIDTH")
    var columnIdWidth: MutableMap<String, Int> = HashMap()

    @get:OptionTag("COLUMN_ID_ORDER")
    var columnIdOrder: List<String>? = ArrayList()

    @get:OptionTag("COLUMN_ID_VISIBILITY")
    var columnIdVisibility: MutableMap<String, Boolean> = HashMap()

    @get:OptionTag("SHOW_DIFF_PREVIEW")
    var isShowDiffPreview = true

    @get:OptionTag("SHOW_ROOT_NAMES")
    var isShowRootNames = false
  }

  companion object {
    @JvmField
    val SHOW_ALL_BRANCHES = VcsLogUiProperty<Boolean>("Table.ShowOtherBranches")
  }
}
