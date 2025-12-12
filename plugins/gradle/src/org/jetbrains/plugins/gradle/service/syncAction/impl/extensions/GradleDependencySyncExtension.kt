// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.extensions

import com.intellij.openapi.externalSystem.util.Order
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

/**
 * Since dependencies are not expressed as entities, they are kept manually by this sync extension. It's implemented in a constrained way
 * because the current representation doesn't allow complex merging logic.
 *
 * It currently allows:
 *  - Overriding the SDK
 *  - Appending dependencies at the end of the existing list.
 *
 * It doesn't allow:
 * - Cleanup of dependencies, as the entity source for each dependency is not known.
 *   Cleanup is expected to be done by data services instead.
 */
@Order(GradleDependencySyncExtension.ORDER)
class GradleDependencySyncExtension : GradleSyncExtension {
  override fun updateProjectModel(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: MutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    syncStorage.entitiesToReplace<ModuleEntity>(context, phase).forEach { moduleEntity ->
      val existingDependencies = projectStorage.resolve(moduleEntity.symbolicId)?.dependencies ?: return@forEach
      val dependenciesToAdd = LinkedHashSet(moduleEntity.dependencies)
      val explicitSdk = moduleEntity.dependencies.find { it is SdkDependency }
      val existingProjectSdkDependency = existingDependencies.find { it is SdkDependency }

      syncStorage.modifyModuleEntity(moduleEntity) {
        // Start with the existing dependencies to ensure ordering
        // remains intact if there are no new dependencies added
        dependencies = existingDependencies.map {
          // Replace existing SDK with the explicitly set SDK if known already in the sync storage,
          // otherwise use the existing SDK from project storage, otherwise inherit.
          if (it.isSdkDependency()) {
            explicitSdk ?: existingProjectSdkDependency ?: InheritedSdkDependency
          } else {
            it
          }
        }.toMutableList()
        // No need to add the SDK dependency as it's already handled
        dependenciesToAdd.removeIf { it.isSdkDependency() }
        // No need to add existing dependencies
        dependenciesToAdd.removeAll(dependencies.toSet())
        dependencies.addAll(dependenciesToAdd)
      }
    }
  }

  private fun ModuleDependencyItem.isSdkDependency() = this is InheritedSdkDependency || this is SdkDependency

  companion object {
    const val ORDER: Int = GradleBaseSyncExtension.ORDER - 500
  }
}
