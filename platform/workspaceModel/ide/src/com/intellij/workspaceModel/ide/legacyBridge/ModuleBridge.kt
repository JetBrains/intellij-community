package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.VersionedEntityStorage

interface ModuleBridge: ModuleEx {
  val moduleEntityId: ModuleId

  /**
   * Entity store used by this module and related components like root manager.
   * It may change on module transition from modifiable module model to regular module in ModuleManager.
   */
  val entityStorage: VersionedEntityStorage

  /**
   * Specifies a diff where module related changes should be written (like root changes).
   * If it's null related changes should written directly with updateProjectModel.
   * It may change on module transition from modifiable module model to regular module in ModuleManager.
   */
  val diff: WorkspaceEntityStorageDiffBuilder?
}
