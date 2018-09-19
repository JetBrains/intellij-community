// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.util.EventDispatcher
import java.util.*

@State(name = "GithubPullRequestsUISettings", storages = [Storage("github_settings.xml")])
class GithubPullRequestsUISettings : PersistentStateComponentWithModificationTracker<GithubPullRequestsUISettings.SettingsState> {
  private var state: SettingsState = SettingsState()
  private val changeEventDispatcher = EventDispatcher.create(SettingsChangedListener::class.java)

  var showDetails: Boolean
    get() = state.showDetails
    set(value) {
      state.showDetails = value
      changeEventDispatcher.multicaster.settingsChanged()
    }

  fun addChangesListener(listener: SettingsChangedListener, disposable: Disposable) =
    changeEventDispatcher.addListener(listener, disposable)

  class SettingsState : BaseState() {
    var showDetails by property(true)
  }

  override fun getState() = state

  override fun loadState(state: GithubPullRequestsUISettings.SettingsState) {
    this.state = state
  }

  override fun getStateModificationCount() = state.modificationCount

  companion object {
    @JvmStatic
    fun getInstance() = service<GithubPullRequestsUISettings>()
  }

  interface SettingsChangedListener : EventListener {
    fun settingsChanged()
  }
}
