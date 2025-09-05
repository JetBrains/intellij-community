// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.versionCatalogs

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.gradleModuleEntity

object GradleVersionCatalogUtil {
  fun getVersionCatalogEntity(moduleEntity: ModuleEntity, catalogName: String, storage: EntityStorage): GradleVersionCatalogEntity? {
    val build = getBuildEntity(moduleEntity, storage) ?: return null
    return build.versionCatalog(catalogName)
  }

  fun getVersionCatalogEntities(moduleEntity: ModuleEntity, storage: EntityStorage): List<GradleVersionCatalogEntity> {
    val build = getBuildEntity(moduleEntity, storage) ?: return emptyList()
    return build.versionCatalogs
  }

  /**
   * Please use [getVersionCatalogEntity] with ModuleEntity parameter, if possible
   */
  @ApiStatus.Obsolete
  fun getVersionCatalogEntity(module: Module, catalogName: String): GradleVersionCatalogEntity? {
    val moduleEntity = module.findModuleEntity() ?: return null
    val storage = module.project.workspaceModel.currentSnapshot
    return getVersionCatalogEntity(moduleEntity, catalogName, storage)
  }

  /**
   * Please use [getVersionCatalogEntities] with ModuleEntity parameter, if possible
   */
  @ApiStatus.Obsolete
  fun getVersionCatalogEntities(module: Module): List<GradleVersionCatalogEntity> {
    val moduleEntity = module.findModuleEntity() ?: return emptyList()
    val storage = module.project.workspaceModel.currentSnapshot
    return getVersionCatalogEntities(moduleEntity, storage)
  }

  private fun getBuildEntity(moduleEntity: ModuleEntity, storage: EntityStorage): GradleBuildEntity? {
    val gradleModuleEntity = moduleEntity.gradleModuleEntity?: return null
    return storage.resolve(gradleModuleEntity.gradleProject.buildId)
  }
}
