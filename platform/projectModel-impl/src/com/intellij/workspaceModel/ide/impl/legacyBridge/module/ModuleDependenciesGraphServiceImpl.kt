// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.projectModel.LibraryOrSdkDependencyEdge
import com.intellij.projectModel.ModuleDependenciesGraph
import com.intellij.projectModel.ModuleDependenciesGraphService

internal class ModuleDependenciesGraphServiceImpl(project: Project): ModuleDependenciesGraphService {

  private val entityStore: VersionedEntityStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).entityStorage

  fun exportedDependentsGraph(): ModuleDependenciesGraph = entityStore.cachedValue(dependentsGraph)

  override fun getModuleDependenciesGraph(): ModuleDependenciesGraph {
    return exportedDependentsGraph()
  }

  private val dependentsGraph = CachedValue { storage ->
    buildGraph(storage)
  }

  private data class ModuleDependencyEdge(
    val dependent: ModuleEntity,
    val exported: Boolean,
  )

  companion object {
    fun buildGraph(storage: EntityStorage): ModuleDependenciesGraph {
      return object : ModuleDependenciesGraph {
        private val moduleDirectDependents: Map<ModuleId, List<ModuleDependencyEdge>>
        private val libraryDependents: Map<SymbolicEntityId<*>, List<LibraryOrSdkDependencyEdge>>

        init {
          val dependentsMap = HashMap<ModuleId, MutableList<ModuleDependencyEdge>>()
          val libraryDependentsMap = HashMap<SymbolicEntityId<*>, MutableList<LibraryOrSdkDependencyEdge>>()

          for (module in storage.entities(ModuleEntity::class.java)) {
            module.dependencies.forEachIndexed { index, dep ->
              when (dep) {
                  is ModuleDependency -> {
                    dependentsMap
                      .computeIfAbsent(dep.module) { mutableListOf() }
                      .add(ModuleDependencyEdge(module, dep.exported))
                  }
                is LibraryDependency -> {
                  libraryDependentsMap.computeIfAbsent(dep.library) { mutableListOf() }
                    .add(LibraryOrSdkDependencyEdge(module, index))
                  dep.library
                }
                is SdkDependency -> {
                  libraryDependentsMap.computeIfAbsent(dep.sdk) { mutableListOf() }
                    .add(LibraryOrSdkDependencyEdge(module, index))
                }
                else -> {}
              }
            }
          }

          this.libraryDependents = libraryDependentsMap
          this.moduleDirectDependents = dependentsMap
        }

        override fun getLibraryOrSdkDependants(libraryOrSdk: SymbolicEntityId<*>): Collection<LibraryOrSdkDependencyEdge> {
          return libraryDependents[libraryOrSdk] ?: emptyList()
        }

        override fun getModuleDependants(module: ModuleEntity): Collection<ModuleEntity> {
          val result = HashSet<ModuleEntity>()
          val queue = ArrayDeque<ModuleEntity>()
          queue.add(module)

          while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val edges = moduleDirectDependents[current.symbolicId] ?: emptyList()

            for (edge in edges) {
              if (result.add(edge.dependent) && edge.exported) {
                queue.add(edge.dependent)
              }
            }
          }
          return result
        }
      }
    }
  }
}
