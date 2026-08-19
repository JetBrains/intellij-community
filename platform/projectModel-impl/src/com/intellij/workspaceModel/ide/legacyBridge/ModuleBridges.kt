// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleBridges")

package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.moduleEntityNotResolved
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete

/**
 * Searches for corresponding [ModuleEntity]
 * in the [current snapshot][com.intellij.platform.backend.workspace.WorkspaceModel.currentSnapshot].
 *
 * @return corresponding [ModuleEntity] or `null` if module isn't associated with entity yet
 */
fun Module.findSnapshotModuleEntity(): ModuleEntity? {
  return findModuleEntity(project.workspaceModel.currentSnapshot)
}

/**
 * In most cases prefer [findModuleEntityIfNotDisposed]
 * @return corresponding [ModuleEntity] or null if module isn't associated with entity yet
 */
@Internal
fun Module.findModuleEntity(): ModuleEntity? {
  return findModuleEntity(findEntityStorage())
}

/**
 * For disposed modules throws [com.intellij.serviceContainer.AlreadyDisposedException] that stops current activity.
 * Returns [ModuleEntity] otherwise.
 */
@Internal
fun Module.findModuleEntityIfNotDisposed(): ModuleEntity {
  val storage = findEntityStorage()
  return findModuleEntity(storage) ?: moduleEntityNotResolved(storage)
}

/**
 * @return corresponding [ModuleEntity] or null if module isn't associated with entity yet
 */
fun Module.findModuleEntity(entityStorage: EntityStorage): ModuleEntity? {
  return entityStorage.moduleMap.getEntities(this as ModuleBridge).firstOrNull() as ModuleEntity?
}

/**
 * Consider rewriting your code to use [ModuleEntity] directly. This method was introduced to simplify the first
 * step of migration to [WorkspaceModel][com.intellij.platform.backend.workspace.WorkspaceModel] and will be removed later.
 *
 * @return corresponding [com.intellij.openapi.module.Module] or `null` if this entity isn't added to the project model yet.
 */
@Obsolete
fun ModuleEntity.findModule(snapshot: EntityStorage): Module? {
  return snapshot.moduleMap.getDataByEntity(this)
}

private fun Module.findEntityStorage() = (this as ModuleBridge).diff ?: project.workspaceModel.currentSnapshot
