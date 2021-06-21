// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module

import com.intellij.openapi.components.service
import com.intellij.openapi.module.impl.ModulePath
import com.intellij.openapi.module.impl.UnloadedModuleDescriptionImpl
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage

interface AutomaticModuleUnloader {
  fun processNewModules(currentModules: Set<String>, storage: WorkspaceEntityStorage)
  fun setLoadedModules(modules: List<String>)

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<AutomaticModuleUnloader>()
  }
}

class DummyAutomaticModuleUnloader : AutomaticModuleUnloader {
  override fun processNewModules(currentModules: Set<String>, storage: WorkspaceEntityStorage) {}

  override fun setLoadedModules(modules: List<String>) {}
}

class UnloadedModulesListChange(val toLoad: List<ModulePath>, val toUnload: List<ModulePath>, val toUnloadDescriptions: List<UnloadedModuleDescriptionImpl>)