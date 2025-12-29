// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.extensions

import com.intellij.openapi.externalSystem.util.Order
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.createEntityTreeCopy
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase

/**
 * Since dependencies are not expressed as entities, they are kept manually by this sync extension.
 *
 * By nature, this only preserves the already existing dependencies, and doesn't allow any cleanup of dependencies the entity source for
 * each dependency is not known. Cleanup is expected to be done by data services instead.
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
      val syncDependencies = moduleEntity.dependencies.toHashSet()
      val explicitSdk = moduleEntity.dependencies.find { it is SdkDependency }

      syncStorage.modifyModuleEntity(moduleEntity) {
        // Clean up all existing SDKs and set the explicitly set SDK if known already in the sync storage,
        // otherwise use the project's explicit SDK, and otherwise inherit.
        dependencies.removeAll { it is InheritedSdkDependency || it is SdkDependency }
        dependencies.add(explicitSdk ?: existingDependencies.find { it is SdkDependency } ?: InheritedSdkDependency)
        val dependenciesToAdd = existingDependencies.filterNot { syncDependencies.contains(it) || it is SdkDependency || it is InheritedSdkDependency }
        dependencies.addAll(dependenciesToAdd)
        // Add the corresponding library entities from project storage to sync storage as well.
        dependenciesToAdd
          .filterIsInstance<LibraryDependency>()
          .filterNot { syncStorage.contains(it.library) }
          .forEach {
            val existingProjectEntity = projectStorage.resolve(it.library)
            existingProjectEntity?.let { syncStorage.addEntity(it.createEntityTreeCopy())}
        }
      }
    }
  }

  companion object {

    const val ORDER: Int = GradleBaseSyncExtension.ORDER - 500
  }
}
