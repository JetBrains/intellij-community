// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.openapi.externalSystem.util.Order
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncProjectConfigurator.project
import java.nio.file.Path

@ApiStatus.Internal
@Order(GradleSyncContributor.Order.PROJECT_ROOT_CONTRIBUTOR)
class GradleProjectRootSyncContributor : GradleSyncContributor {

  override suspend fun onResolveProjectInfoStarted(context: ProjectResolverContext, storage: MutableEntityStorage) {
    if (context.isPhasedSyncEnabled) {
      configureProjectRoot(context, storage)
    }
  }

  private suspend fun configureProjectRoot(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project()
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val contentRootEntities = storage.entities<ContentRootEntity>()

    val projectRootPath = Path.of(context.projectPath)
    val projectRootUrl = projectRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val projectRootEntity = contentRootEntities.find { it.url == projectRootUrl }

    val entitySource = GradleBuildEntitySource(projectRootUrl)

    if (projectRootEntity == null) {
      addProjectRootEntity(storage, projectRootUrl, entitySource)
    }
  }

  private fun addProjectRootEntity(
    storage: MutableEntityStorage,
    projectRootUrl: VirtualFileUrl,
    entitySource: GradleEntitySource
  ) {
    storage addEntity ContentRootEntity(
      url = projectRootUrl,
      entitySource = entitySource,
      excludedPatterns = emptyList()
    ) {
      module = ModuleEntity(
        name = projectRootUrl.fileName,
        entitySource = entitySource,
        dependencies = emptyList()
      )
    }
  }
}