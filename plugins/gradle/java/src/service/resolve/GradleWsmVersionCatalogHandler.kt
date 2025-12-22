// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.plugins.gradle.model.projectModel.gradleModuleEntity
import org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity
import org.jetbrains.plugins.gradle.model.versionCatalogs.versionCatalogs

/**
 * Provides version catalog data collected at Gradle sync and stored in the Workspace Model.
 */
internal class GradleWsmVersionCatalogHandler : GradleVersionCatalogHandler {
  override fun getVersionCatalogFiles(module: Module): Map<String, VirtualFile> {
    val result = HashMap<String, VirtualFile>()
    for (entity in getVersionCatalogEntities(module)) {
      val virtualFile = entity.url.virtualFile ?: continue
      result[entity.name] = virtualFile
    }
    return result
  }

  private fun getVersionCatalogEntities(module: Module): List<GradleVersionCatalogEntity> {
    val moduleEntity = module.findModuleEntity() ?: return emptyList()
    val gradleModuleEntity = moduleEntity.gradleModuleEntity ?: return emptyList()

    val storage = module.project.workspaceModel.currentSnapshot
    val build = storage.resolve(gradleModuleEntity.gradleProjectId.buildId) ?: return emptyList()
    return build.versionCatalogs
  }
}