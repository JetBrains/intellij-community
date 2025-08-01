// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor.bridge

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleEntitySource

@ApiStatus.Internal
class GradleBridgeProjectDataService : AbstractProjectDataService<GradleBridgeData, Unit>() {

  override fun getTargetDataKey(): Key<GradleBridgeData> = GradleBridgeData.KEY

  override fun importData(
    toImport: Collection<DataNode<GradleBridgeData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    if (!Registry.`is`("gradle.phased.sync.bridge.disabled")) {
      removeModulesFromModelProvider(modelsProvider)
      removeEntitiesFromWorkspaceModel(modelsProvider)
    }
  }

  private fun removeModulesFromModelProvider(modelsProvider: IdeModifiableModelsProvider) {
    val moduleModel = modelsProvider.modifiableModuleModel
    val entityStorage = modelsProvider.actualStorageBuilder
    val moduleNames = entityStorage.entities<ModuleEntity>()
      .filter { it.entitySource is GradleEntitySource }
      .map { it.name }
      .toList()
    for (moduleName in moduleNames) {
      val module = moduleModel.findModuleByName(moduleName) ?: continue
      moduleModel.disposeModule(module)
    }
  }

  private fun removeEntitiesFromWorkspaceModel(modelsProvider: IdeModifiableModelsProvider) {
    val entityStorage = modelsProvider.actualStorageBuilder
    val entities = entityStorage.entitiesBySource { it is GradleEntitySource }
      .toList()
    for (entity in entities) {
      entityStorage.removeEntity(entity)
    }
  }
}