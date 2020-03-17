package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.workspace.api.ModuleId
import com.intellij.workspace.api.TypedEntityStorageDiffBuilder
import com.intellij.workspace.api.TypedEntityStore

interface LegacyBridgeModule: ModuleEx {
  val moduleEntityId: ModuleId

  /**
   * Entity store used by this module and related components like root manager.
   * It may change on module transition from modifiable module model to regular module in ModuleManager.
   */
  val entityStore: TypedEntityStore

  /**
   * Specifies a diff where module related changes should be written (like root changes).
   * If it's null related changes should written directly with updateProjectModel.
   * It may change on module transition from modifiable module model to regular module in ModuleManager.
   */
  val diff: TypedEntityStorageDiffBuilder?
}
