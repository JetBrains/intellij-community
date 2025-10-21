// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(name = "BytecodeViewerSettings", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
internal class BytecodeViewerSettings : PersistentStateComponent<BytecodeViewerSettings.State> {
  data class State(
    var showDebugInfo: Boolean = true,
    var syncWithEditor: Boolean = true,
  )

  private var myState = State()

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }

  companion object {
    fun getInstance(): BytecodeViewerSettings = service<BytecodeViewerSettings>()
  }
}