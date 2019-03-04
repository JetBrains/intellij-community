// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import java.util.*

@State(name = "GithubPullRequestsUISettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GithubPullRequestsProjectUISettings : PersistentStateComponentWithModificationTracker<GithubPullRequestsProjectUISettings.SettingsState> {
  private var state: SettingsState = SettingsState()

  class SettingsState : BaseState() {
    var zipChanges by property(false)
  }

  private val changesEventDispatcher = EventDispatcher.create(ChangesEventDispatcher::class.java)

  fun addChangesListener(disposable: Disposable, listener: () -> Unit) {
    changesEventDispatcher.addListener(object : ChangesEventDispatcher {
      override fun stateChanged() {
        listener()
      }
    }, disposable)
  }

  var zipChanges: Boolean
    get() = state.zipChanges
    set(value) {
      state.zipChanges = value
      changesEventDispatcher.multicaster.stateChanged()
    }

  override fun getStateModificationCount() = state.modificationCount
  override fun getState() = state
  override fun loadState(state: GithubPullRequestsProjectUISettings.SettingsState) {
    this.state = state
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<GithubPullRequestsProjectUISettings>()

    private interface ChangesEventDispatcher : EventListener {
      fun stateChanged()
    }
  }
}
