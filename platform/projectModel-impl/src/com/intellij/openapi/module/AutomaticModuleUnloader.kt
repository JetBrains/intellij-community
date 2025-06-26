// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
interface AutomaticModuleUnloader {
  fun calculateNewModules(currentModules: Set<String>, builder: MutableEntityStorage, unloadedEntityBuilder: MutableEntityStorage): Pair<List<String>, List<String>>

  fun updateUnloadedStorage(modulesToLoad: List<String>, modulesToUnload: List<String>)
  /**
   * The list of modules should be empty if all modules are loaded.
   */
  fun setLoadedModules(modules: Collection<String>)

  @TestOnly
  fun getLoadedModules(): Collection<String>

  companion object {
    suspend fun getInstance(project: Project): AutomaticModuleUnloader = project.serviceAsync<AutomaticModuleUnloader>()
  }
}

internal class DummyAutomaticModuleUnloader : AutomaticModuleUnloader {
  override fun calculateNewModules(currentModules: Set<String>, builder: MutableEntityStorage, unloadedEntityBuilder: MutableEntityStorage): Pair<List<String>, List<String>> {
    return Pair(emptyList(), emptyList())
  }

  override fun updateUnloadedStorage(modulesToLoad: List<String>, modulesToUnload: List<String>) {}

  override fun setLoadedModules(modules: Collection<String>) {}
  override fun getLoadedModules(): Collection<String> = emptyList()
}