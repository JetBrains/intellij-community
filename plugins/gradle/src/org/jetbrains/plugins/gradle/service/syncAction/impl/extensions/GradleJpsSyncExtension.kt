// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.extensions

import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.openapi.externalSystem.service.project.nameGenerator.ModuleNameGenerator
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.module.impl.UnloadedModulesListStorage
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

@ApiStatus.Internal
@Order(GradleJpsSyncExtension.ORDER)
class GradleJpsSyncExtension : GradleSyncExtension {

  override fun updateProjectModel(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: MutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    removeModulesWithUsedContentRoots(context, syncStorage, projectStorage, phase)
    removeUnloadedModules(context, syncStorage, phase)
    removeBridgeModules(context, syncStorage, projectStorage, phase)
    renameDuplicatedModules(context, syncStorage, projectStorage, phase)
    removeDuplicatedContentRoots(context, syncStorage, projectStorage, phase)
  }

  private fun removeModulesWithUsedContentRoots(
    context: ProjectResolverContext,
    syncStorage: EntityStorage,
    projectStorage: MutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    val usedContentRoots = syncStorage.entitiesToReplace<ContentRootEntity>(context, phase)
      .mapTo(HashSet()) { it.url }
    val entitiesToRemove = projectStorage.entitiesToSkip<ModuleEntity>(context, phase)
      .filter { module -> module.exModuleOptions?.externalSystem == null }
      .filter { module -> module.contentRoots.any { it.url in usedContentRoots } }
      .toList()
    projectStorage.removeAllEntities(entitiesToRemove)
  }

  private fun removeUnloadedModules(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    val unloadedModulesListStorage = UnloadedModulesListStorage.getInstance(context.project)
    val unloadedModuleNameHolder = unloadedModulesListStorage.unloadedModuleNameHolder
    val entitiesToRemove = ArrayList<WorkspaceEntity>()
    for (moduleEntity in syncStorage.entitiesToReplace<ModuleEntity>(context, phase)) {
      for (moduleName in generateModuleNames(context, moduleEntity)) {
        if (unloadedModuleNameHolder.isUnloaded(moduleName)) {
          entitiesToRemove.add(moduleEntity)
        }
      }
    }
    syncStorage.removeAllEntities(entitiesToRemove)
  }

  private fun removeBridgeModules(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: EntityStorage,
    phase: GradleSyncPhase,
  ) {
    val entitiesToRemove = ArrayList<WorkspaceEntity>()
    for (syncModuleEntity in syncStorage.entitiesToReplace<ModuleEntity>(context, phase)) {
      val entitySource = projectStorage.resolve(syncModuleEntity.symbolicId)?.entitySource ?: continue
      if (entitySource is JpsImportedEntitySource && entitySource.externalSystemId == GradleConstants.SYSTEM_ID.id) {
        entitiesToRemove.add(syncModuleEntity)
      }
    }
    syncStorage.removeAllEntities(entitiesToRemove)
  }

  private fun removeDuplicatedContentRoots(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: EntityStorage,
    phase: GradleSyncPhase,
  ) {
    if (isSharedSourceSupportEnabled(context.project)) {
      return
    }
    val contentRootUrls = projectStorage.entitiesToSkip<ContentRootEntity>(context, phase)
      .mapTo(HashSet()) { it.url }
    val entitiesToRemove = ArrayList<WorkspaceEntity>()
    for (contentRootEntity in syncStorage.entitiesToReplace<ContentRootEntity>(context, phase)) {
      if (!contentRootUrls.add(contentRootEntity.url)) {
        entitiesToRemove.add(contentRootEntity)
      }
    }
    syncStorage.removeAllEntities(entitiesToRemove)
  }

  private fun renameDuplicatedModules(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: EntityStorage,
    phase: GradleSyncPhase,
  ) {
    val moduleNames = projectStorage.entitiesToSkip<ModuleEntity>(context, phase)
      .mapTo(HashSet()) { it.name }
    for (moduleEntity in syncStorage.entitiesToReplace<ModuleEntity>(context, phase)) {
      if (!moduleNames.add(moduleEntity.name)) {
        for (moduleName in generateModuleNames(context, moduleEntity)) {
          if (moduleNames.add(moduleName)) {
            syncStorage.modifyModuleEntity(moduleEntity) {
              name = moduleName
            }
            break
          }
        }
      }
    }
  }

  private fun generateModuleNames(
    context: ProjectResolverContext,
    moduleEntity: ModuleEntity,
  ): Iterable<String> {
    val moduleName = moduleEntity.name
    val modulePath = Path.of(context.projectPath)
    return ModuleNameGenerator.generate(null, moduleName, modulePath, ".")
  }

  private fun MutableEntityStorage.removeAllEntities(entities: List<WorkspaceEntity>) {
    entities.forEach { removeEntity(it) }
  }

  companion object {

    const val ORDER: Int = GradleBaseSyncExtension.ORDER - 1000
  }
}