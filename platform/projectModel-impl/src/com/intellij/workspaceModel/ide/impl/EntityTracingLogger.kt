// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityTypeId
import com.intellij.platform.workspace.jps.entities.FacetId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EntityTracingLogger {
  /** specifies ID of an entity which changes should be printed to the log */
  private val entityToTrace = System.getProperty("idea.workspace.model.track.entity.id")?.let {
    val tokens = it.split('/')
    when (tokens.size) {
      3 -> {
        val (moduleName, facetTypeId, facetName) = tokens
        FacetId(facetName, FacetEntityTypeId(facetTypeId), ModuleId(moduleName))
      }
      1 -> ModuleId(tokens.first())
      else -> null
    }
  }

  fun subscribe(project: Project, cs: CoroutineScope) {
    if (entityToTrace != null) {
      cs.launch {
        WorkspaceModel.getInstance(project).eventLog.collect { event ->
          (event as VersionedStorageChangeInternal).getAllChanges().forEach {
            when (it) {
              is EntityChange.Added -> printInfo("added", it.newEntity)
              is EntityChange.Removed -> printInfo("removed", it.oldEntity)
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
