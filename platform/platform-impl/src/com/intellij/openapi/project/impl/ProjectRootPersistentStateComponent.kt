// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.components.*
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@State(name = "ProjectRoots")
@Storage(StoragePathMacros.WORKSPACE_FILE)
@ApiStatus.Internal
class ProjectRootPersistentStateComponent(val scope: CoroutineScope) :
  SerializablePersistentStateComponent<ProjectRootPersistentStateComponent.State>(State()) {

  var projectRootUrls: List<String>
    get() = state.projectRootUrls
    set(value) {
      updateState {
        it.copy(projectRootUrls = value)
      }
    }

  data class State(
    @JvmField val projectRootUrls: List<String> = emptyList(),
  )
}