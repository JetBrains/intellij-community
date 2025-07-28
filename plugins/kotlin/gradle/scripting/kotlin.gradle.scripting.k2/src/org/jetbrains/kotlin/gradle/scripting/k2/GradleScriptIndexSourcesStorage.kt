// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute

@Service(Service.Level.PROJECT)
@State(name = "GradleScriptIndexSourcesStorage", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GradleScriptIndexSourcesStorage(val project: Project) :
  SerializablePersistentStateComponent<GradleScriptIndexSourcesStorage.State>(State()) {

    data class State(@Attribute @JvmField val indexed: Boolean = false)

    fun saveProjectIndexed() {
        updateState {
            it.copy(indexed = true)
        }
    }

    fun sourcesShouldBeIndexed(): Boolean = state.indexed

    companion object {
        fun getInstance(project: Project): GradleScriptIndexSourcesStorage = project.service<GradleScriptIndexSourcesStorage>()
    }
}