// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectModel

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleEntity
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
data class LibraryOrSdkDependencyEdge(
  val dependent: ModuleEntity,
  val orderNumber: Int,
)

@ApiStatus.Internal
/**
 * [getModuleDependants] returns [ModuleEntity] which depend on the given module taking into account exported dependencies.
 *
 * [getLibraryOrSdkDependants] returns a collection of [LibraryOrSdkDependencyEdge] which contains a dependent on give [SymbolicEntityId] module and its order number.
 */
interface ModuleDependenciesGraph {
  fun getModuleDependants(module: ModuleEntity): Collection<ModuleEntity>
  fun getLibraryOrSdkDependants(libraryOrSdk: SymbolicEntityId<*>): Collection<LibraryOrSdkDependencyEdge>
}