// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly

@Internal
interface ProjectIdManager {
  var id: @NonNls String?
}

@State(name = "ProjectId", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))], reportStatistic = false)
internal class ProjectIdManagerImpl : SerializablePersistentStateComponent<ProjectIdManagerImpl.State>(State()), ProjectIdManager {
  override var id: @NonNls String?
    get() = state.id
    set(value) {
      updateState { it }
      state.id = value
    }

  class State {
    @Attribute
    @JvmField
    var id: String? = null
  }
}

@TestOnly
private class MockProjectIdManager : ProjectIdManager {
  override var id: String? = null
}