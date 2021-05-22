// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.XCollection

@State(name = "UpdateFromSourcesSettings", storages = [Storage("update.from.sources.xml")])
internal class UpdateFromSourcesSettings : SimplePersistentStateComponent<UpdateFromSourcesSettingsState>(UpdateFromSourcesSettingsState()) {
  companion object {
    fun getState() = service<UpdateFromSourcesSettings>().state
  }
}

internal class UpdateFromSourcesSettingsState : BaseState() {
  var showSettings by property(true)
  var workIdePath: String? by string()
  var buildDisabledPlugins by property(false)
  var pluginDirectoriesForDisabledPlugins by list<String>()
  var restartAutomatically by property(false)
  @get:XCollection(style = XCollection.Style.v2)
  val workIdePathsHistory: MutableList<String> by list()
}

internal val UpdateFromSourcesSettingsState.actualIdePath: String
  get() = workIdePath ?: PathManager.getHomePath()