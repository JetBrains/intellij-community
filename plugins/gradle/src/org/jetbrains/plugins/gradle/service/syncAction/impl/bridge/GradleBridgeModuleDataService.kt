// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.bridge

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity
import org.jetbrains.plugins.gradle.model.projectModel.gradleModuleEntity
import org.jetbrains.plugins.gradle.model.projectModel.modifyGradleProjectEntity

@ApiStatus.Internal
class GradleBridgeModuleDataService : AbstractProjectDataService<GradleBridgeModuleData, Unit>() {

  override fun getTargetDataKey(): Key<GradleBridgeModuleData> = GradleBridgeModuleData.KEY

  override fun importData(
    toImport: Collection<DataNode<GradleBridgeModuleData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider,
  ) {
    val storage = modelsProvider.actualStorageBuilder
    val linkedProjectIdToModule = storage.entities(ModuleEntity::class.java)
      .mapNotNull { it.exModuleOptions }
      .associate { it.linkedProjectId to it.module }

    val projectEntities = storage.entities(GradleProjectEntity::class.java)

    for (projectEntity in projectEntities) {
      // GradleModuleEntity could be already created. For example, by GradleContentRootSyncContributor.
      if (projectEntity.gradleModuleEntity != null) continue

      val moduleEntity = linkedProjectIdToModule[projectEntity.linkedProjectId] ?: continue
      addGradleModuleEntity(storage, projectEntity, moduleEntity)
    }
  }

  private fun addGradleModuleEntity(
    storage: MutableEntityStorage,
    projectEntity: GradleProjectEntity,
    moduleEntity: ModuleEntity,
  ) {
    storage.modifyGradleProjectEntity(projectEntity) {
      storage.modifyModuleEntity(moduleEntity) {
        storage addEntity GradleModuleEntity(projectEntity.entitySource) {
          module = this@modifyModuleEntity
          gradleProject = this@modifyGradleProjectEntity
        }
      }
    }
  }
}