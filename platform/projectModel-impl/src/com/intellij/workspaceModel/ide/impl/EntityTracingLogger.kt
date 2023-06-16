// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.FacetId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class EntityTracingLogger {
  /** specifies ID of an entity which changes should be printed to the log */
  private val entityToTrace = System.getProperty("idea.workspace.model.track.entity.id")?.let {
    val tokens = it.split('/')
    when (tokens.size) {
      3 -> {
        val (moduleName, facetTypeId, facetName) = tokens
        FacetId(facetName, facetTypeId, ModuleId(moduleName))
      }
      1 -> ModuleId(tokens.first())
      else -> null
    }
  }

  fun subscribe(project: Project, cs: CoroutineScope) {
    if (entityToTrace != null) {
      cs.launch {
        WorkspaceModel.getInstance(project).changesEventFlow.collect{ event ->
          event.getAllChanges().forEach {
            when (it) {
              is EntityChange.Added -> printInfo("added", it.entity)
              is EntityChange.Removed -> printInfo("removed", it.entity)
              is EntityChange.Replaced -> {
                printInfo("replaced from", it.oldEntity)
                printInfo("replaced to", it.newEntity)
              }
            }
          }
        }
      }
    }
  }

  fun printInfoAboutTracedEntity(storage: EntityStorage, storageDescription: String) {
    if (entityToTrace != null) {
      LOG.info("Traced entity from $storageDescription: ${storage.resolve(entityToTrace)?.toDebugString()}")
    }
  }

  private fun printInfo(action: String, entity: WorkspaceEntity) {
    if ((entity as? WorkspaceEntityWithSymbolicId)?.symbolicId == entityToTrace) {
      LOG.info("$action: ${entity.toDebugString()}", Throwable())
    }
  }

  companion object {
    private val LOG = logger<EntityTracingLogger>()

    private fun WorkspaceEntity.toDebugString(): String? = when (this) {
      is FacetEntity -> "Facet: $configurationXmlTag"
      is ModuleEntity -> "Module $name"
      else -> javaClass.simpleName
    }
  }
}
