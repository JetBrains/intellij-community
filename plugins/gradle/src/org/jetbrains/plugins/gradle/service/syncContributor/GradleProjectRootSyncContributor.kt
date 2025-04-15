// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleLinkedProjectEntitySource
import java.nio.file.Path

@ApiStatus.Internal
@Order(GradleSyncContributor.Order.PROJECT_ROOT_CONTRIBUTOR)
class GradleProjectRootSyncContributor : GradleSyncContributor {

  override suspend fun onResolveProjectInfoStarted(context: ProjectResolverContext, storage: MutableEntityStorage) {
    if (context.isPhasedSyncEnabled) {
      configureProjectRoot(context, storage)
    }
  }

  override suspend fun onModelFetchPhaseCompleted(context: ProjectResolverContext, storage: MutableEntityStorage, phase: GradleModelFetchPhase) {
    if (context.isPhasedSyncEnabled) {
      if (phase == GradleModelFetchPhase.PROJECT_LOADED_PHASE) {
        removeProjectRoot(context, storage)
      }
    }
  }

  private fun configureProjectRoot(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val linkedProjectRootPath = Path.of(context.projectPath)
    val linkedProjectRootUrl = linkedProjectRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val linkedProjectEntitySource = GradleLinkedProjectEntitySource(linkedProjectRootUrl)

    val contentRootEntities = storage.entities<ContentRootEntity>()
    if (contentRootEntities.any { isConflictedContentRootEntity(it, linkedProjectEntitySource) }) {
      return
    }
    if (isUnloadedModule(project, linkedProjectEntitySource)) {
      return
    }

    configureProjectRoot(storage, linkedProjectEntitySource)
  }

  private fun isConflictedContentRootEntity(
    contentRootEntity: ContentRootEntity,
    entitySource: GradleLinkedProjectEntitySource,
  ): Boolean {
    return contentRootEntity.entitySource == entitySource ||
           contentRootEntity.url == entitySource.projectRootUrl
  }

  private fun isUnloadedModule(
    project: Project,
    entitySource: GradleLinkedProjectEntitySource,
  ): Boolean {
    val unloadedModulesListStorage = UnloadedModulesListStorage.getInstance(project)
    val unloadedModuleNameHolder = unloadedModulesListStorage.unloadedModuleNameHolder
    val moduleName = resolveModuleName(entitySource)
    return unloadedModuleNameHolder.isUnloaded(moduleName)
  }

  private fun configureProjectRoot(
    storage: MutableEntityStorage,
    entitySource: GradleLinkedProjectEntitySource,
  ) {
    val moduleEntity = addModuleEntity(storage, entitySource)
    addContentRootEntity(storage, entitySource, moduleEntity)
  }

  private fun addModuleEntity(
    storage: MutableEntityStorage,
    entitySource: GradleLinkedProjectEntitySource,
  ): ModuleEntity.Builder {
    val moduleName = resolveModuleName(entitySource)
    val moduleEntity = ModuleEntity(
      name = moduleName,
      entitySource = entitySource,
      dependencies = listOf(
        InheritedSdkDependency,
        ModuleSourceDependency
      )
    )
    storage addEntity moduleEntity
    return moduleEntity
  }

  private fun addContentRootEntity(
    storage: MutableEntityStorage,
    entitySource: GradleLinkedProjectEntitySource,
    moduleEntity: ModuleEntity.Builder,
  ) {
    storage addEntity ContentRootEntity(
      url = entitySource.projectRootUrl,
      entitySource = entitySource,
      excludedPatterns = emptyList()
    ) {
      module = moduleEntity
    }
  }

  private fun resolveModuleName(entitySource: GradleLinkedProjectEntitySource): String {
    return entitySource.projectRootUrl.fileName
  }

  /**
   * The [GradleContentRootSyncContributor] has the complete information to configure the accurate build roots.
   * They will be used as project roots in the result project model.
   */
  private fun removeProjectRoot(context: ProjectResolverContext, storage: MutableEntityStorage) {
    val project = context.project
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val linkedProjectRootPath = Path.of(context.projectPath)
    val linkedProjectRootUrl = linkedProjectRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val linkedProjectEntitySource = GradleLinkedProjectEntitySource(linkedProjectRootUrl)

    val linkedProjectEntities = storage.entitiesBySource { it == linkedProjectEntitySource }
    for (linkedProjectEntity in linkedProjectEntities.toList()) {
      storage.removeEntity(linkedProjectEntity)
    }
  }
}