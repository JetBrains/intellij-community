// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleBridgeUtils")
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

/**
 * @return corresponding [com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity] or null if module isn't associated with entity yet
 */
fun ModuleBridge.findModuleEntity(entityStorage: EntityStorage): ModuleEntity? {
  return entityStorage.moduleMap.getEntities(this).firstOrNull() as ModuleEntity?
}
