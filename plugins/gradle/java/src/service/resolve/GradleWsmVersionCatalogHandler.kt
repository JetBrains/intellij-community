// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import org.jetbrains.plugins.gradle.model.versionCatalogs.GradleVersionCatalogEntity
import org.jetbrains.plugins.gradle.model.versionCatalogs.versionCatalogs
import org.jetbrains.plugins.gradle.util.getGradleBuild

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

  private fun getVersionCatalogEntities(module: Module): List<GradleVersionCatalogEntity> =
    module.getGradleBuild()?.versionCatalogs ?: emptyList()
}