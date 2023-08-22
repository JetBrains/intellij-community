// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.Comparing
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import org.jetbrains.annotations.NonNls
import java.util.*

@State(name = "Vcs.Log.Tabs.Properties", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class VcsLogProjectTabsProperties : PersistentStateComponent<VcsLogProjectTabsProperties.State?>, VcsLogTabsProperties {
  private val appSettings = ApplicationManager.getApplication().getService(VcsLogApplicationSettings::class.java)
  private var _state = State()

  override fun getState(): State = _state

  override fun loadState(state: State) {
    _state = state
  }

  override fun createProperties(id: String): MainVcsLogUiProperties {
    _state.tabStates.putIfAbsent(id, MyState())
    return MyVcsLogUiPropertiesImpl(id)
  }

  fun addTab(tabId: String, location: VcsLogTabLocation) {
    _state.openTabs[tabId] = location
  }

  fun removeTab(tabId: String) {
    _state.openTabs.remove(tabId)
    resetState(tabId)
  }

  fun resetState(tabId: String) {
    _state.tabStates.remove(tabId)
  }

  val tabs: Map<String, VcsLogTabLocation>
    get() = _state.openTabs

  fun getRecentlyFilteredGroups(filterName: String): List<List<String>> {
    return getRecentGroup(_state.recentFilters, filterName)
  }

  fun addRecentlyFilteredGroup(filterName: String, values: Collection<String>) {
    addRecentGroup(_state.recentFilters, filterName, values)
  }

  class State {
    @get:OptionTag("TAB_STATES")
    var tabStates: MutableMap<String, MyState> = TreeMap()

    @get:OptionTag("OPEN_GENERIC_TABS")
    var openTabs = LinkedHashMap<String, VcsLogTabLocation>()

    @get:OptionTag("RECENT_FILTERS")
    var recentFilters: MutableMap<String, MutableList<RecentGroup>> = HashMap()
  }

  class RecentGroup(values: Collection<String>) {
    @Suppress("PropertyName")
    @XCollection
    var FILTER_VALUES: MutableList<String> = values.toMutableList()

    constructor() : this(emptyList())

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false
      val group = other as RecentGroup
      return Comparing.haveEqualElements(FILTER_VALUES, group.FILTER_VALUES)
    }

    override fun hashCode(): Int = Comparing.unorderedHashcode(FILTER_VALUES)
  }

  private inner class MyVcsLogUiPropertiesImpl(private val id: String) : VcsLogUiPropertiesImpl<MyState>(appSettings) {
    override val logUiState = _state.tabStates.getOrPut(id) { MyState() }

    override fun <T : Any> get(property: VcsLogUiProperty<T>): T {
      if (property is CustomBooleanTabProperty) {
        @Suppress("UNCHECKED_CAST")
        return (logUiState.customBooleanProperties[property.getName()] ?: property.defaultValue(id)) as T
      }
      return super.get(property)
    }

    override fun <T : Any> set(property: VcsLogUiProperty<T>, value: T) {
      if (property is CustomBooleanTabProperty) {
        logUiState.customBooleanProperties[property.getName()] = value as Boolean
        onPropertyChanged(property)
        return
      }
      super.set(property, value)
    }

    override fun <T> exists(property: VcsLogUiProperty<T>): Boolean {
      return super.exists(property) || property is CustomBooleanTabProperty
    }

    override fun addRecentlyFilteredGroup(filterName: String, values: Collection<String>) {
      addRecentGroup(_state.recentFilters, filterName, values)
    }

    override fun getRecentlyFilteredGroups(filterName: String): List<List<String>> {
      return getRecentGroup(_state.recentFilters, filterName)
    }
  }

  @Tag("State")
  class MyState : VcsLogUiPropertiesImpl.State() {
    @get:OptionTag("CUSTOM_BOOLEAN_PROPERTIES")
    var customBooleanProperties: MutableMap<String, Boolean> = HashMap()
  }

  open class CustomBooleanTabProperty(name: @NonNls String) : VcsLogUiProperty<Boolean>(name) {
    open fun defaultValue(logId: String) = false
  }

  companion object {
    private const val RECENTLY_FILTERED_VALUES_LIMIT = 10

    @JvmStatic
    fun addRecentGroup(stateField: MutableMap<String, MutableList<RecentGroup>>,
                       filterName: String,
                       values: Collection<String>) {
      val recentGroups = stateField.getOrPut(filterName) { ArrayList() }
      val group = RecentGroup(values)
      recentGroups.remove(group)
      recentGroups.add(0, group)
      while (recentGroups.size > RECENTLY_FILTERED_VALUES_LIMIT) {
        recentGroups.removeAt(recentGroups.size - 1)
      }
    }

    @JvmStatic
    fun getRecentGroup(stateField: Map<String, MutableList<RecentGroup>>, filterName: String): List<List<String>> {
      val values = stateField[filterName] ?: return emptyList()
      return values.map { it.FILTER_VALUES }
    }
  }
}