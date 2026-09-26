// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectModel

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.SymbolicEntityId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ModuleDependenciesGraphService {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ModuleDependenciesGraphService = project.service()
  }

  fun getModuleDependenciesGraph(): ModuleDependenciesGraph
}

@ApiStatus.Internal
interface ModuleDependenciesGraph {
  /**
   * Returns [ModuleEntity]s which depend on the given module taking into account exported dependencies.
   */
  fun getModuleDependants(module: ModuleEntity): Collection<ModuleEntity>

  /**
   * Returns each module that depends on [libraryOrSdk], with the order number of that dependency.
   */
  fun getLibraryOrSdkDependants(libraryOrSdk: SymbolicEntityId<*>): Map<ModuleId, Int>

  /**
   * Returns unloaded [ModuleEntity]s that transitively depend on [module].
   */
  fun getModuleUnloadedDependents(module: ModuleEntity): Collection<ModuleEntity>
}