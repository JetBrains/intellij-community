// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.openapi.components.*

@State(name = "UpdateFromSourcesSettings", storages = [Storage("update.from.sources.xml")])
internal class UpdateFromSourcesSettings : SimplePersistentStateComponent<UpdateFromSourcesSettingsState>(UpdateFromSourcesSettingsState()) {
  companion object {
    fun getState() = service<UpdateFromSourcesSettings>().state
  }
}

internal class UpdateFromSourcesSettingsState : BaseState() {
  var showSettings by property(true)
  var workIdePath by string()
  var buildDisabledPlugins by property(false)
  var pluginDirectoriesForDisabledPlugins by list<String>()
}