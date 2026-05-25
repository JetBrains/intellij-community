// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "IdeStarterRunSettings", storages = [Storage("ideStarterRunModes.xml")])
internal class IdeStarterRunSettings : PersistentStateComponent<IdeStarterRunSettings.State> {
  internal data class State(
    var useInstaller: Boolean = false,
    var useSplitMode: Boolean = false,
  )

  private var myState = State()

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }

  var useInstaller: Boolean
    get() = myState.useInstaller
    set(value) {
      myState.useInstaller = value
    }

  var useSplitMode: Boolean
    get() = myState.useSplitMode
    set(value) {
      myState.useSplitMode = value
    }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): IdeStarterRunSettings = project.service()
  }
}
