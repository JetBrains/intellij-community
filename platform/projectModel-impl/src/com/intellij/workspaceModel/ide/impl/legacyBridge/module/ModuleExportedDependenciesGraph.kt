// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.util.graph.Graph
import org.jetbrains.annotations.ApiStatus

/**
 * [Graph.getIn] returns [ModuleEntity] which depend on the given module taking into account exported dependencies.
 *
 * [Graph.getOut] returns dependencies of the given [ModuleEntity] both exported and not exported.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ModuleExportedDependenciesGraph(project: Project) {

  private val entityStore: VersionedEntityStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).entityStorage

  fun exportedDependentsGraph(): Graph<ModuleEntity> = entityStore.cachedValue(exportedDependentsGraph)

  private val exportedDependentsGraph = CachedValue { storage ->
    buildGraph(storage)
  }

  private data class DependencyEdge(
    val dependent: ModuleEntity,
    val exported: Boolean,
  )

  companion object {
    fun buildGraph(storage: EntityStorage): Graph<ModuleEntity> {
      return object : Graph<ModuleEntity> {
        private val directDependents: Map<ModuleId, List<DependencyEdge>>

        init {
          val dependentsMap = HashMap<ModuleId, MutableList<DependencyEdge>>()

          for (module in storage.entities(ModuleEntity::class.java)) {
            for (dep in module.dependencies) {
              if (dep is ModuleDependency) {
                dependentsMap
                  .computeIfAbsent(dep.module) { mutableListOf() }
                  .add(DependencyEdge(module, dep.exported))
              }
            }
          }

          this.directDependents = dependentsMap
        }

        override fun getNodes(): Collection<ModuleEntity> {
          return storage.entities(ModuleEntity::class.java).toList()
        }

        override fun getIn(node: ModuleEntity): Iterator<ModuleEntity> {
          val result = HashSet<ModuleEntity>()
          val queue = ArrayDeque<ModuleEntity>()
          queue.add(node)

          while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val edges = directDependents[current.symbolicId] ?: emptyList()

            for (edge in edges) {
              if (result.add(edge.dependent) && edge.exported) {
                queue.add(edge.dependent)
              }
            }
          }
          return result.iterator()
        }

        override fun getOut(node: ModuleEntity): Iterator<ModuleEntity> {
          return node.dependencies.filterIsInstance<ModuleDependency>().mapNotNull { it.module.resolve(storage) }.iterator()
        }
      }
    }

    @JvmStatic
    fun getInstance(project: Project): ModuleExportedDependenciesGraph = project.service()
  }
}
