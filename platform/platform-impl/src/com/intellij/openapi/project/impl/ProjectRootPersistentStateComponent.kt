// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.XCollection
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@State(name = "ProjectRoots", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
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
    @Property(surroundWithTag = false)
    @XCollection(elementName = "project-root", valueAttributeName = "url", style = XCollection.Style.v2)
    @JvmField val projectRootUrls: List<String> = emptyList(),
  )
}