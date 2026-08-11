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
import com.intellij.projectModel.LibraryOrSdkDependencyEdge
import com.intellij.projectModel.ModuleDependenciesGraph
import com.intellij.projectModel.ModuleDependenciesGraphService

internal class ModuleDependenciesGraphServiceImpl(project: Project): ModuleDependenciesGraphService {

  private val workspaceModel: WorkspaceModelInternal = WorkspaceModel.getInstance(project) as WorkspaceModelInternal

  override fun getModuleDependenciesGraph(): ModuleDependenciesGraph {
    val loaded = workspaceModel.entityStorage.cachedValue(loadedDependentsGraph)
    val unloaded = workspaceModel.unloadedEntitiesStorage.cachedValue(unloadedDirectDependentsGraph)
    return CompositeModuleDependenciesGraph(loaded, unloaded)
  }

  private val loadedDependentsGraph = CachedValue { storage -> buildLoadedDependents(storage) }
  private val unloadedDirectDependentsGraph = CachedValue { storage -> buildUnloadedDirectDependents(storage) }

  private data class ModuleDependencyEdge(
    val dependent: ModuleEntity,
    val exported: Boolean,
  )

  private class LoadedDependents(
    val moduleDirectDependents: Map<ModuleId, List<ModuleDependencyEdge>>,
    val libraryDependents: Map<SymbolicEntityId<*>, List<LibraryOrSdkDependencyEdge>>,
  )

  private class UnloadedDirectDependents(
    val moduleDirectDependents: Map<ModuleId, List<ModuleEntity>>,
  )

  private class CompositeModuleDependenciesGraph(
    private val loaded: LoadedDependents,
    private val unloaded: UnloadedDirectDependents,
  ) : ModuleDependenciesGraph {

    override fun getLibraryOrSdkDependants(libraryOrSdk: SymbolicEntityId<*>): Collection<LibraryOrSdkDependencyEdge> {
      return loaded.libraryDependents[libraryOrSdk] ?: emptyList()
    }

    override fun getModuleDependants(module: ModuleEntity): Collection<ModuleEntity> {
      val result = HashSet<ModuleEntity>()
      val queue = ArrayDeque<ModuleEntity>()
      queue.add(module)

      while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val edges = loaded.moduleDirectDependents[current.symbolicId] ?: continue

        for (edge in edges) {
          if (result.add(edge.dependent) && edge.exported) {
            queue.add(edge.dependent)
          }
        }
      }
      return result
    }

    override fun getModuleUnloadedDependents(module: ModuleEntity): Collection<ModuleEntity> {
      val result = LinkedHashSet<ModuleEntity>()
      val visited = HashSet<ModuleEntity>()
      val queue = ArrayDeque<ModuleEntity>()

      queue.add(module)
      visited.add(module)
      while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        unloaded.moduleDirectDependents[current.symbolicId]?.let {
          result.addAll(it)
        }

        val edges = loaded.moduleDirectDependents[current.symbolicId] ?: continue
        for (edge in edges) {
          if (edge.exported && visited.add(edge.dependent)) {
            queue.add(edge.dependent)
          }
        }
      }
      return result
    }
  }

  companion object {
    private fun buildLoadedDependents(storage: EntityStorage): LoadedDependents {
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
            }
            is SdkDependency -> {
              libraryDependentsMap.computeIfAbsent(dep.sdk) { mutableListOf() }
                .add(LibraryOrSdkDependencyEdge(module, index))
            }
            else -> {}
          }
        }
      }

      return LoadedDependents(dependentsMap, libraryDependentsMap)
    }

    private fun buildUnloadedDirectDependents(storage: EntityStorage): UnloadedDirectDependents {
      val moduleDirectDependents = HashMap<ModuleId, MutableList<ModuleEntity>>()
      for (unloadedModule in storage.entities(ModuleEntity::class.java)) {
        for (dep in unloadedModule.dependencies) {
          if (dep is ModuleDependency) {
            moduleDirectDependents
              .computeIfAbsent(dep.module) { mutableListOf() }
              .add(unloadedModule)
          }
        }
      }
      return UnloadedDirectDependents(moduleDirectDependents)
    }
  }
}
