// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.VersionedEntityStorage

/**
 * Provides access to the storage which holds workspace model entities.
 */
interface WorkspaceModel {
  val entityStorage: VersionedEntityStorage

  /**
   * Modifies the current model by calling [updater] and applying it to the storage. Requires write action.
   */
  fun <R> updateProjectModel(updater: (WorkspaceEntityStorageBuilder) -> R): R

  /** Update project model without the notification to message bus */
  fun <R> updateProjectModelSilent(updater: (WorkspaceEntityStorageBuilder) -> R): R

  companion object {
    @JvmStatic
    fun getInstance(project: Project): WorkspaceModel = ServiceManager.getService(project, WorkspaceModel::class.java)
  }
}
