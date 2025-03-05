// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler

import com.intellij.openapi.components.*
import com.intellij.util.application

@State(name = "IdeaDecompilerSettings", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
@Service(Service.Level.APP)
internal class IdeaDecompilerSettings() : PersistentStateComponent<IdeaDecompilerSettings.State?> {
  private var state = State()

  companion object {
    @JvmStatic
    fun getInstance(): IdeaDecompilerSettings = application.service()
  }

  override fun getState(): State {
    return state
  }

  override fun loadState(state: State) {
    this.state = state
  }

  class State {
    @JvmField
    var preset: DecompilerPreset = DecompilerPreset.HIGH
    var options: Map<String, String> = DecompilerPreset.HIGH.options

    companion object {
      @JvmStatic
      fun fromPreset(preset: DecompilerPreset): State {
        val newState = State()
        newState.preset = preset
        newState.options = preset.options
        return newState
      }
    }
  }
}
