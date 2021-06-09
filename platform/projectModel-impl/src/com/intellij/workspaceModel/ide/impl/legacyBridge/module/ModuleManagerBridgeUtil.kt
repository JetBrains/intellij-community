// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.ExternalEntityMapping
import com.intellij.workspaceModel.storage.MutableExternalEntityMapping
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

/**
 * This class should be removed after [ModuleManagerComponentBridge] migration to the `intellij.platform.projectModel.impl` module
 */
class ModuleManagerBridgeUtil {
  companion object {
    private const val MODULE_BRIDGE_MAPPING_ID = "intellij.modules.bridge"

    val WorkspaceEntityStorage.moduleMap: ExternalEntityMapping<ModuleBridge>
      get() = getExternalMapping(MODULE_BRIDGE_MAPPING_ID)
    val WorkspaceEntityStorageDiffBuilder.mutableModuleMap: MutableExternalEntityMapping<ModuleBridge>
      get() = getMutableExternalMapping(MODULE_BRIDGE_MAPPING_ID)

    fun WorkspaceEntityStorage.findModuleEntity(module: ModuleBridge) =
      moduleMap.getEntities(module).firstOrNull() as ModuleEntity?

    fun WorkspaceEntityStorage.findModuleByEntity(entity: ModuleEntity): ModuleBridge? = moduleMap.getDataByEntity(entity)
  }
}