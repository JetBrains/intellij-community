// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import org.jetbrains.annotations.NonNls

/**
 * Provides access to the storage which holds workspace model entities.
 */
interface WorkspaceModel {
  /**
   * Returns snapshot of the workspace model storage. 
   * The returned value won't be affected by future changes in [WorkspaceModel], so it can be safely used without any locks from any thread.
   */
  val currentSnapshot: EntityStorageSnapshot
  
  val entityStorage: VersionedEntityStorage

  /**
   * Modifies the current model by calling [updater] and applying it to the storage. Requires write action.
   *
   * Use [description] to briefly describe what do you update. This message will be logged and can be used for debugging purposes.
   *   For testing there is an extension method that doesn't require a description [com.intellij.testFramework.workspaceModel.updateProjectModel].
   */
  fun <R> updateProjectModel(description: @NonNls String, updater: (MutableEntityStorage) -> R): R

  @Deprecated("Zhenja please use the update method with the debug message")
  fun <R> updateProjectModel(updater: (MutableEntityStorage) -> R): R {
    return updateProjectModel("Project model update (default description)", updater)
  }

  /**
   * Update project model without the notification to message bus and without resetting accumulated changes.
   *
   * This method doesn't require write action.
   */
  @Deprecated("Method will be removed from interface. Use WorkspaceModelImpl#updateProjectModelSilent only " +
              "if you are absolutely sure you need it")
  fun <R> updateProjectModelSilent(description: @NonNls String, updater: (MutableEntityStorage) -> R): R

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
   *   can be updated using [MutableEntityStorage.addDiff] or [MutableEntityStorage.replaceBySource], but you have be
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
    fun getInstance(project: Project): WorkspaceModel = project.service()
  }
}
