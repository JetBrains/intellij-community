// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleBridgeUtils")
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.annotations.ApiStatus

/**
 * Use [com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity] from API instead.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun ModuleBridge.findModuleEntity(entityStorage: EntityStorage): ModuleEntity? {
  return entityStorage.moduleMap.getEntities(this).firstOrNull() as ModuleEntity?
}
