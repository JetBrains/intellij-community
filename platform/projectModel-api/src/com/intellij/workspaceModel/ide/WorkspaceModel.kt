// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.*
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
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
   * Flow of changes from workspace model. It has to be used for asynchronous event handling. To start receiving
   * emitted events, you need to call one of the terminal operations on it.
   */
  @get:ApiStatus.Experimental
  val changesEventFlow: Flow<VersionedStorageChange>

  /**
   * Returns a snapshot of the storage containing unloaded entities. 
   * Unloaded entities must be ignored by almost all code in the IDE, so this property isn't supposed for general use.
   * 
   * Currently, unloaded entities correspond to modules which are unloaded using 'Load/Unload Modules' action. 
   */
  val currentSnapshotOfUnloadedEntities: EntityStorageSnapshot

  /**
   * Modifies the current model by calling [updater] and applying it to the storage. Requires write action.
   *
   * Use [description] to briefly describe what do you update. This message will be logged and can be used for debugging purposes.
   *   For testing there is an extension method that doesn't require a description [com.intellij.testFramework.workspaceModel.updateProjectModel].
   */
  fun updateProjectModel(description: @NonNls String, updater: (MutableEntityStorage) -> Unit)

  /**
   * Modifies the current model of unloaded entities by calling [updater] and applying it to the storage.
   * @param description describes the reason for the change, used for logging purposes only.
   */
  fun updateUnloadedEntities(description: @NonNls String, updater: (MutableEntityStorage) -> Unit)

  /**
   * **Asynchronous** modification of the current model by calling [updater] and applying it to the storage.
   *
   * Use [description] to briefly describe what do you update. This message will be logged and can be used for debugging purposes.
   */
  @ApiStatus.Experimental
  suspend fun updateProjectModelAsync(description: @NonNls String, updater: (MutableEntityStorage) -> Unit)

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

/**
 * Extension property for syntax sugar
 */
val Project.workspaceModel: WorkspaceModel
  get() = WorkspaceModel.getInstance(this)
