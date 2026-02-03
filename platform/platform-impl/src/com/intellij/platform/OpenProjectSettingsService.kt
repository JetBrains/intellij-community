// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.openapi.components.*

@Service(Service.Level.PROJECT)
@State(name = "OpenProjectSettingsState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class OpenProjectSettingsService : PersistentStateComponent<OpenProjectSettingsState> {
  private var currentSettings: OpenProjectSettingsState = OpenProjectSettingsState()

  override fun getState(): OpenProjectSettingsState {
    return currentSettings
  }

  override fun loadState(state: OpenProjectSettingsState) {
    currentSettings = state
  }
}

internal class OpenProjectSettingsState : BaseState() {
  var isLocatedInTempDirectory: Boolean = false
}
