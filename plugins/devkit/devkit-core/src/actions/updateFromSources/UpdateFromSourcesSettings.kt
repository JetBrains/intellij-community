// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions.updateFromSources

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus

@State(name = "UpdateFromSourcesSettings", storages = [Storage("update.from.sources.xml", roamingType = RoamingType.DISABLED)])
@ApiStatus.Internal
class UpdateFromSourcesSettings : SimplePersistentStateComponent<UpdateFromSourcesSettingsState>(UpdateFromSourcesSettingsState()) {
  companion object {
    fun getState(): UpdateFromSourcesSettingsState = service<UpdateFromSourcesSettings>().state
  }
}

@ApiStatus.Internal
class UpdateFromSourcesSettingsState : BaseState() {
  var showSettings: Boolean by property(true)
  var workIdePath: String? by string()
  var buildDisabledPlugins: Boolean by property(false)
  var pluginDirectoriesForDisabledPlugins: MutableList<String> by list<String>()
  var restartAutomatically: Boolean by property(false)
  @get:XCollection(style = XCollection.Style.v2)
  val workIdePathsHistory: MutableList<String> by list()
}

internal val UpdateFromSourcesSettingsState.actualIdePath: String
  get() = workIdePath ?: PathManager.getHomePath()