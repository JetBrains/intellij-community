// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.referrers
import com.intellij.util.graph.CachingSemiGraph
import com.intellij.util.graph.Graph
import com.intellij.util.graph.GraphGenerator
import com.intellij.util.graph.InboundSemiGraph
import org.jetbrains.annotations.ApiStatus

/**
 * [Graph.getIn] returns [ModuleEntity] which depend on the given module taking into account exported dependencies.
 *
 * [Graph.getOut] returns [ModuleEntity] on which the given module depends taking into account exported dependencies.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ModuleExportedDependenciesGraph(project: Project) {

  private val entityStore: VersionedEntityStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).entityStorage

  fun exportedDependentsGraph(): Graph<ModuleEntity> = entityStore.cachedValue(exportedDependentsGraph)

  private val exportedDependentsGraph = CachedValue { storage ->
    buildGraph(storage)
  }

  private fun buildGraph(storage: EntityStorage): Graph<ModuleEntity> {
    return GraphGenerator.generate(CachingSemiGraph.cache(object : InboundSemiGraph<ModuleEntity> {
      override fun getNodes(): Collection<ModuleEntity> =
        storage.entities(ModuleEntity::class.java).toList()

      override fun getIn(module: ModuleEntity): Iterator<ModuleEntity> {
        return collectDependentModules(module, storage).iterator()
      }
    }))
  }

  private fun collectDependentModules(module: ModuleEntity, storage: EntityStorage): Set<ModuleEntity> {
    val queue = ArrayDeque<ModuleEntity>()
    val result = HashSet<ModuleEntity>()
    queue.add(module)
    while (queue.isNotEmpty()) {
      val currentModule = queue.removeFirst()
      for (referrer in storage.referrers<ModuleEntity>(currentModule.symbolicId)) {
        val dependency =
          referrer.dependencies.filterIsInstance<ModuleDependency>().find { it.module == currentModule.symbolicId } ?: continue
        if (result.add(referrer) && dependency.exported) {
          queue.add(referrer)
        }
      }
    }
    return result
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ModuleExportedDependenciesGraph = project.service()
  }
}
