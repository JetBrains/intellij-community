// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.externalSystem.service.project.nameGenerator.ModuleNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl
import org.jetbrains.plugins.gradle.service.syncContributor.bridge.GradleBridgeEntitySource
import java.nio.file.Path
import kotlin.io.path.name

@ApiStatus.Internal
@Order(GradleSyncContributor.Order.PROJECT_ROOT_CONTRIBUTOR)
class GradleProjectRootSyncContributor : GradleSyncContributor {

  override val phase: GradleSyncPhase = GradleSyncPhase.INITIAL_PHASE

  override suspend fun configureProjectModel(context: ProjectResolverContext, storage: MutableEntityStorage) {
    if (!hasNonPreviewEntities(context, storage)) {
      configureProjectRoot(context, storage)
    }
  }

  private suspend fun configureProjectRoot(
    context: ProjectResolverContext,
    storage: MutableEntityStorage
  ) {
    val contentRoots = storage.entities<ContentRootEntity>()
      .mapTo(LinkedHashSet()) { it.url }

    val entitySource = GradleProjectRootEntitySource(context.projectPath)
    val projectRootData = GradleProjectRootData(Path.of(context.projectPath), entitySource)

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
          url = context.virtualFileUrl(projectRootData.projectRoot),
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

  private class GradleProjectRootData(
    val projectRoot: Path,
    val entitySource: EntitySource,
  )

  class Bridge : GradleSyncContributor {

    override val phase: GradleSyncPhase = GradleSyncPhase.PROJECT_MODEL_PHASE

    override suspend fun configureProjectModel(context: ProjectResolverContext, storage: MutableEntityStorage) {
      if (!context.isPhasedSyncEnabled) return

      removeProjectRoot(context, storage)
    }
  }
}

private data class GradleProjectRootEntitySource(
  override val projectPath: String,
) : GradleBridgeEntitySource

/**
 * The [GradleContentRootSyncContributor] has the complete information to configure the accurate build roots.
 * They will be used as project roots in the result project model.
 */
@ApiStatus.Internal // Visible for GradleDeclarativeSyncContributor
fun removeProjectRoot(context: ProjectResolverContext, storage: MutableEntityStorage) {
  val entitySource = GradleProjectRootEntitySource(context.projectPath)
  val entities = storage.entitiesBySource { it == entitySource }
  for (entity in entities.toList()) {
    storage.removeEntity(entity)
  }
}

@ApiStatus.Internal // Visible for GradleDeclarativeSyncContributor
fun hasNonPreviewEntities(context: ProjectResolverContext, storage: MutableEntityStorage): Boolean {
  return storage.entities<ModuleEntity>()
    .filter { it.entitySource !is GradleProjectRootEntitySource }
    .filter { it.exModuleOptions?.rootProjectPath == context.projectPath }
    .any()
}