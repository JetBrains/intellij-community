// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.openapi.components.*

@State(name = "UpdateFromSourcesSettings", storages = [Storage("update.from.sources.xml")])
class UpdateFromSourcesSettings : PersistentStateComponent<UpdateFromSourcesSettings.UpdateFromSourcesSettingsState> {
  private var state = UpdateFromSourcesSettingsState()

  companion object {
    fun getState() = service<UpdateFromSourcesSettings>().state
  }

  override fun getState(): UpdateFromSourcesSettingsState? = state

  override fun loadState(state: UpdateFromSourcesSettingsState) {
    this.state = state
  }

  class UpdateFromSourcesSettingsState : BaseState() {
    var showSettings by property(true)
    var workIdePath by string()
    var buildDisabledPlugins by property(false)
    var pluginDirectoriesForDisabledPlugins by list<String>()
  }
}