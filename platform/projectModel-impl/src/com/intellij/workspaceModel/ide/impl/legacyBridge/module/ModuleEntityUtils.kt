// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleEntityUtils")
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.annotations.ApiStatus.Obsolete

/**
 * Consider rewriting your code to use [ModuleEntity] directly. This method was introduced to simplify the first
 * step of migration to [WorkspaceModel][com.intellij.platform.backend.workspace.WorkspaceModel] and lately will
 * be removed.
 *
 * @return corresponding [com.intellij.openapi.module.Module] or `null` if this entity isn't added to the project model yet.
 */
@Obsolete
fun ModuleEntity.findModule(snapshot: EntityStorage): ModuleBridge? {
  return snapshot.moduleMap.getDataByEntity(this)
}

/**
 * Returns all module-level libraries defined in this module.
 */
fun ModuleEntity.getModuleLevelLibraries(snapshot: EntityStorage): Sequence<LibraryEntity> {
  return snapshot.referrers(symbolicId, LibraryEntity::class.java)
}