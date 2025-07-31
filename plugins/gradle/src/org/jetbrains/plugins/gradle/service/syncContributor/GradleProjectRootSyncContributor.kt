// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.externalSystem.service.project.nameGenerator.ModuleNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleLinkedProjectEntitySource
import java.nio.file.Path
import kotlin.io.path.name

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
      if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
        removeProjectRoot(context, storage)
      }
    }
  }

  private suspend fun configureProjectRoot(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    val project = context.project
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    val contentRoots = storage.entities<ContentRootEntity>()
      .mapTo(LinkedHashSet()) { it.url }

    val linkedProjectRootPath = Path.of(context.projectPath)
    val linkedProjectRootUrl = linkedProjectRootPath.toVirtualFileUrl(virtualFileUrlManager)
    val linkedProjectEntitySource = GradleLinkedProjectEntitySource(linkedProjectRootUrl)

    val projectRootData = GradleProjectRootData(Path.of(context.projectPath), linkedProjectEntitySource)

    if (isUnloadedModule(context, projectRootData)) {
      return
    }

    val moduleEntity = createModuleEntity(context, storage, projectRootData)
    val moduleContentRoots = moduleEntity.contentRoots.map { it.url }

    if (moduleContentRoots.none { it in contentRoots }) {
      contentRoots.addAll(moduleContentRoots)

      storage addEntity moduleEntity
    }
  }

  private suspend fun isUnloadedModule(
    context: ProjectResolverContext,
    projectRootData: GradleProjectRootData,
  ): Boolean {
    val unloadedModulesListStorage = context.project.serviceAsync<UnloadedModulesListStorage>()
    val unloadedModuleNameHolder = unloadedModulesListStorage.unloadedModuleNameHolder
    for (moduleName in generateModuleNames(projectRootData)) {
      if (unloadedModuleNameHolder.isUnloaded(moduleName)) {
        return true
      }
    }
    return false
  }

  private fun createModuleEntity(
    context: ProjectResolverContext,
    storage: EntityStorage,
    projectRootData: GradleProjectRootData,
  ): ModuleEntity.Builder {
    val virtualFileUrlManager = context.project.workspaceModel.getVirtualFileUrlManager()
    return ModuleEntity(
      name = resolveUniqueModuleName(storage, projectRootData),
      entitySource = projectRootData.entitySource,
      dependencies = listOf(
        InheritedSdkDependency,
        ModuleSourceDependency
      )
    ) {
      contentRoots = listOf(
        ContentRootEntity(
          url = projectRootData.projectRoot.toVirtualFileUrl(virtualFileUrlManager),
          entitySource = entitySource,
          excludedPatterns = emptyList()
        )
      )
    }
  }

  private fun resolveUniqueModuleName(
    storage: EntityStorage,
    projectRootData: GradleProjectRootData,
  ): String {
    for (moduleNameCandidate in generateModuleNames(projectRootData)) {
      val moduleId = ModuleId(moduleNameCandidate)
      if (storage.resolve(moduleId) == null) {
        return moduleNameCandidate
      }
    }
    throw IllegalStateException("Too many duplicated module names")
  }

  private fun generateModuleNames(
    projectRootData: GradleProjectRootData,
  ): Iterable<String> {
    val moduleName = projectRootData.projectRoot.name
    val modulePath = projectRootData.projectRoot
    return ModuleNameGenerator.generate(null, moduleName, modulePath, ".")
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

  private class GradleProjectRootData(
    val projectRoot: Path,
    val entitySource: GradleLinkedProjectEntitySource
  )
}