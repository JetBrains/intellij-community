// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogApplicationSettings
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties.Companion.addRecentGroup
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties.Companion.getRecentGroup
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties.RecentGroup
import com.intellij.vcs.log.impl.VcsLogTabsProperties
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl
import java.util.*

@State(name = "Git.Log.External.Tabs.Properties",
       storages = [Storage(value = "git.external.log.tabs.xml", roamingType = RoamingType.DISABLED)])
class GitExternalLogTabsProperties : PersistentStateComponent<GitExternalLogTabsProperties.State>, VcsLogTabsProperties {
  private var _state = State()

  override fun getState(): State = _state

  override fun loadState(state: State) {
    _state = state
  }

  override fun createProperties(id: String): MainVcsLogUiProperties {
    if (!_state.TAB_STATES.containsKey(id)) {
      _state.TAB_STATES[id] = TabState()
    }
    return MyVcsLogUiProperties(id)
  }

  class State {
    @Suppress("PropertyName")
    @XMap
    var TAB_STATES: MutableMap<String, TabState> = TreeMap()
  }

  class TabState : VcsLogUiPropertiesImpl.State() {
    @Suppress("PropertyName")
    @XCollection
    var RECENT_FILTERS: MutableMap<String, MutableList<RecentGroup>> = HashMap()
  }

  private inner class MyVcsLogUiProperties(private val id: String) :
    VcsLogUiPropertiesImpl<TabState>(ApplicationManager.getApplication().getService(VcsLogApplicationSettings::class.java)) {

    override val logUiState get() = _state.TAB_STATES[id]!!

    override fun addRecentlyFilteredGroup(filterName: String, values: Collection<String>) {
      addRecentGroup(logUiState.RECENT_FILTERS, filterName, values)
    }

    override fun getRecentlyFilteredGroups(filterName: String): List<List<String>> {
      return getRecentGroup(logUiState.RECENT_FILTERS, filterName)
    }
  }
}
