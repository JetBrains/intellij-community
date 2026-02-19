// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.bridge

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.WorkspaceDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.projectModel.GradleModuleEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity
import org.jetbrains.plugins.gradle.model.projectModel.gradleModuleEntity

@ApiStatus.Internal
class GradleBridgeModuleDataService : WorkspaceDataService<GradleBridgeModuleData> {

  override fun getTargetDataKey(): Key<GradleBridgeModuleData> = GradleBridgeModuleData.KEY

  override fun importData(
    toImport: Collection<DataNode<GradleBridgeModuleData>>,
    projectData: ProjectData?,
    project: Project,
    mutableStorage: MutableEntityStorage,
  ) {
    if (Registry.`is`("gradle.phased.sync.bridge.disabled")) return
    addGradleModuleEntities(mutableStorage)
  }

  private fun addGradleModuleEntities(mutableStorage: MutableEntityStorage) {
    val linkedProjectIdToModule = mutableStorage.entities<ModuleEntity>()
      .mapNotNull { it.exModuleOptions }
      .associate { it.linkedProjectId to it.module }

    for (gradleProjectEntity in mutableStorage.entities(GradleProjectEntity::class.java)) {
      val moduleEntity = linkedProjectIdToModule[gradleProjectEntity.linkedProjectId] ?: continue
      mutableStorage.modifyModuleEntity(moduleEntity) {
        gradleModuleEntity = GradleModuleEntity(gradleProjectEntity.symbolicId, GradleBridgeModuleEntitySource())
      }
    }
  }

  private class GradleBridgeModuleEntitySource : EntitySource
}