// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.externalSystem

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.bridgeEntities.*

class ExternalSystemModulePropertyManagerBridge(private val module: Module) : ExternalSystemModulePropertyManager() {
  private fun findEntity(): ExternalSystemModuleOptionsEntity? {
    val storage = (module as ModuleBridge).entityStorage.current
    val moduleEntity = storage.findModuleEntity(module)
    return moduleEntity?.externalSystemOptions
  }

  private fun editEntity(action: ModifiableExternalSystemModuleOptionsEntity.() -> Unit) {
    val diff = (module as ModuleBridge).diff
    if (diff != null) {
      val moduleEntity = module.entityStorage.current.findModuleEntity(module) ?: return
      val options = diff.getOrCreateExternalSystemModuleOptions(moduleEntity, moduleEntity.entitySource)
      diff.modifyEntity(ModifiableExternalSystemModuleOptionsEntity::class.java, options, action)
    }
    else {
      WriteAction.runAndWait<RuntimeException> {
        WorkspaceModel.getInstance(module.project).updateProjectModel { builder ->
          val moduleEntity = builder.findModuleEntity(module) ?: return@updateProjectModel
          val options = builder.getOrCreateExternalSystemModuleOptions(moduleEntity, moduleEntity.entitySource)
          builder.modifyEntity(ModifiableExternalSystemModuleOptionsEntity::class.java, options, action)
        }
      }
    }
  }

  private fun updateSource() {
    WriteAction.runAndWait<RuntimeException> {
      val storage = (module as ModuleBridge).entityStorage.current
      val moduleEntity = storage.findModuleEntity(module) ?: return@runAndWait
      val externalSystemId = moduleEntity.externalSystemOptions?.externalSystem
      val entitySource = moduleEntity.entitySource
      if (externalSystemId == null && entitySource is JpsFileEntitySource ||
          externalSystemId != null && (entitySource as? JpsImportedEntitySource)?.externalSystemId == externalSystemId) {
        return@runAndWait
      }
      val newSource = if (externalSystemId == null) {
        (entitySource as JpsImportedEntitySource).internalFile
      }
      else {
        JpsImportedEntitySource(entitySource as JpsFileEntitySource, externalSystemId, module.project.isExternalStorageEnabled)
      }

      fun changeSources(diffBuilder: WorkspaceEntityStorageDiffBuilder, storage: WorkspaceEntityStorage) {
        val entitiesMap = storage.entitiesBySource { it == entitySource }
        entitiesMap.values.asSequence().flatMap { it.values.asSequence().flatten() }.forEach {
          if (it !is FacetEntity) {
            diffBuilder.changeSource(it, newSource)
          }
        }
      }

      val diff = module.diff
      if (diff != null) {
        changeSources(diff, storage)
      }
      else {
        WorkspaceModel.getInstance(module.project).updateProjectModel { builder ->
          changeSources(builder, builder)
        }
      }
    }
  }

  override fun getExternalSystemId(): String? = findEntity()?.externalSystem
  override fun getExternalModuleType(): String? = findEntity()?.externalSystemModuleType
  override fun getExternalModuleVersion(): String? = findEntity()?.externalSystemModuleVersion
  override fun getExternalModuleGroup(): String? = findEntity()?.externalSystemModuleGroup
  override fun getLinkedProjectId(): String? = findEntity()?.linkedProjectId
  override fun getRootProjectPath(): String? = findEntity()?.rootProjectPath
  override fun getLinkedProjectPath(): String? = findEntity()?.linkedProjectPath
  override fun isMavenized(): Boolean = getExternalSystemId() == ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID

  override fun setMavenized(mavenized: Boolean) {
    if (mavenized) {
      unlinkExternalOptions()
    }
    editEntity {
      externalSystem = if (mavenized) ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID else null
    }
    updateSource()
  }

  override fun unlinkExternalOptions() {
    editEntity {
      externalSystem = null
      externalSystemModuleVersion = null
      externalSystemModuleGroup = null
      linkedProjectId = null
      linkedProjectPath = null
      rootProjectPath = null
    }
    updateSource()
  }

  override fun setExternalOptions(id: ProjectSystemId, moduleData: ModuleData, projectData: ProjectData?) {
    editEntity {
      externalSystem = id.toString()
      linkedProjectId = moduleData.id
      linkedProjectPath = moduleData.linkedExternalProjectPath
      rootProjectPath = projectData?.linkedExternalProjectPath ?: ""

      externalSystemModuleGroup = moduleData.group
      externalSystemModuleVersion = moduleData.version
    }
    updateSource()
  }

  override fun setExternalId(id: ProjectSystemId) {
    editEntity {
      externalSystem = id.id
    }
    updateSource()
  }

  override fun setLinkedProjectPath(path: String?) {
    editEntity {
      linkedProjectPath = path
    }
  }

  override fun setRootProjectPath(path: String?) {
    editEntity {
      rootProjectPath = path
    }
  }

  override fun setExternalModuleType(type: String?) {
    editEntity {
      externalSystemModuleType = type
    }
  }

  override fun swapStore() {
  }
}