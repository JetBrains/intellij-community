// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleEntityUtils")
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Obsolete

/**
 * Use [com.intellij.workspaceModel.ide.legacyBridge.findModule] from API instead.
 */
@Obsolete
@ApiStatus.Internal
fun ModuleEntity.findModule(snapshot: EntityStorage): ModuleBridge? {
  return snapshot.moduleMap.getDataByEntity(this)
}

/**
 * Returns all module-level libraries defined in this module.
 */
fun ModuleEntity.getModuleLevelLibraries(snapshot: EntityStorage): Sequence<LibraryEntity> {
  return snapshot.referrers(symbolicId, LibraryEntity::class.java)
}