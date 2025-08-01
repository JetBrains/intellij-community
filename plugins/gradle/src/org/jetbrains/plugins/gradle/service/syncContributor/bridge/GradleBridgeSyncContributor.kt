// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor.bridge

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.plugins.gradle.service.project.DefaultProjectResolverContext
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleEntitySource
import org.jetbrains.plugins.gradle.service.syncContributor.entitites.GradleProjectEntitySource


/**
 * This is a sync contributor that runs after the platform's content root contributor to fix-up any issues caused by it and makes sure
 * everything still works fine even when the bridge data service is removed.
 *
 * Long term plan is to fix all issues upstream and remove it, but I've opted for this implementation for the flexibility it provides until
 * the issues are fixed. It's worth pointing that there is a longer term plan to remove the bridge data service on the platform.
 */
@Order(GradleSyncContributor.Order.CONTENT_ROOT_CONTRIBUTOR + 1)
class GradleBridgeSyncContributor : GradleSyncContributor {

  override suspend fun onModelFetchPhaseCompleted(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    phase: GradleModelFetchPhase,
  ) {
    if (!context.isPhasedSyncEnabled || !Registry.`is`("gradle.phased.sync.bridge.disabled")) {
      // If data bridge is not disabled, everything that was set up by phased sync will be removed, so no need to do anything.
      return
    }

    if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {

      // Keep the root module as an iml based entity, because many things go wrong if there isn't at least one .iml based module
      removeGradleBasedEntitiesForRootModule(storage)
    }
  }

  // Invoked after all the phases are complete
  override suspend fun onModelFetchCompleted(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    if (!context.isPhasedSyncEnabled || !Registry.`is`("gradle.phased.sync.bridge.disabled")) {
      // If data bridge is not disabled, everything that was set up by phased sync will be removed, so no need to do anything.
      return
    }

    // Remove all modules that were just populated in certain scenarios (i.e. do what the bridge data service would have done)
    removeAllModulesForUnsupportedFlows(context, storage)
  }

  /**
   * Remove all modules that were just populated (i.e. do what the bridge data service would have done) if:
   *   - From a buildSrc project. With older Gradle versions, buildSrc has its own separate resolve (as opposed to being a composite build)
   *     and this causes issues
   *
   * As of now, it's simpler to just skip the sync contributors in these cases. Ideally this should be controlled at the
   * [org.jetbrains.plugins.gradle.service.project.GradleProjectResolver] level, but it's currently not possible via the existing APIs.
   *
   * TODO(b/384022658): Add switch in the platform to be able to disable all sync contributors in certain cases / scenarios
   */
  private fun removeAllModulesForUnsupportedFlows(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    if ((context as DefaultProjectResolverContext).isBuildSrcProject) {
      storage.entities<ModuleEntity>()
        .filter { it.entitySource is GradleEntitySource }
        .forEach { storage.removeEntity(it) }
    }
  }

  /**
   * We keep the root module as an iml based entity, because a lot of things go wrong if we don't.
   * Specifically [com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleBridgeLoaderService] currently disregards
   * the entire workspace model if there are no iml based entities at all.
   *
   * Also see for more context:
   * [com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer.hasNoSerializedJpsModules]
   *
   * TODO(b/384022658): We should aim to delete this in the long term, but for now it should be fine to keep.
   */
  private fun removeGradleBasedEntitiesForRootModule(storage: MutableEntityStorage) {
    storage.entities<ModuleEntity>().filter {
      it.entitySource is GradleProjectEntitySource
      && (it.entitySource as GradleProjectEntitySource).buildEntitySource.linkedProjectEntitySource.projectRootUrl ==
      (it.entitySource as GradleProjectEntitySource).projectRootUrl
    }.forEach {
      storage.removeEntity(it)
    }
  }
}
