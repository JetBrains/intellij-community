// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder

/**
 * Provides access to the storage which holds workspace model entities.
 */
interface WorkspaceModel {
  val entityStorage: VersionedEntityStorage

  val cache: WorkspaceModelCache?

  /**
   * Modifies the current model by calling [updater] and applying it to the storage. Requires write action.
   */
  fun <R> updateProjectModel(updater: (WorkspaceEntityStorageBuilder) -> R): R

  /**
   * Update project model without the notification to message bus and without resetting accumulated changes.
   *
   * This method doesn't require write action.
   */
  fun <R> updateProjectModelSilent(updater: (WorkspaceEntityStorageBuilder) -> R): R

  /**
   * Get builder that can be updated in background and applied later and a project model.
   *
   * @see [WorkspaceModel.replaceProjectModel]
   */
  fun getBuilderSnapshot(): BuilderSnapshot

  /**
   * Replace current project model with the new version from storage snapshot.
   *
   * This operation required write lock.
   * The snapshot replacement is performed using positive lock. If the project model was updated since [getCurrentBuilder], snapshot
   *   won't be applied and this method will return false. In this case client should get a newer version of snapshot builder, apply changes
   *   and try to call [replaceProjectModel].
   *   Keep in mind that you may not need to start the full builder update process (e.g. gradle sync) and the newer version of the builder
   *   can be updated using [WorkspaceEntityStorageBuilder.addDiff] or [WorkspaceEntityStorageBuilder.replaceBySource], but you have be
   *   sure that the changes will be applied to the new builder correctly.
   *
   * The calculation of changes will be performed during [BuilderSnapshot.getStorageReplacement]. This method only replaces the project model
   *   and sends corresponding events.
   *
   * Example:
   * ```
   *   val builderSnapshot = projectModel.getBuilderSnapshot()
   *
   *   update(builderSnapshot)
   *
   *   val storageSnapshot = builderSnapshot.getStorageReplacement()
   *   val updated = writeLock { projectModel.replaceProjectModel(storageSnapshot) }
   *
   *   if (!updated) error("Project model updates too fast")
   * ```
   *
   * Future plans: add some kind of ordering for async updates of the project model
   *
   * @see [WorkspaceModel.getBuilderSnapshot]
   */
  fun replaceProjectModel(replacement: StorageReplacement): Boolean

  companion object {
    @JvmStatic
    val isEnabled: Boolean
      get() = !Registry.`is`("ide.old.project.model")

    @JvmStatic
    fun getInstance(project: Project): WorkspaceModel = project.service()
  }
}
