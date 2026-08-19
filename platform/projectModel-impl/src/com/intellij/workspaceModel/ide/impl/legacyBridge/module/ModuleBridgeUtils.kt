// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ModuleBridgeUtils")

package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.serviceContainer.AlreadyDisposedException
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

/**
 * Use [com.intellij.workspaceModel.ide.legacyBridge.findModuleEntityIfNotDisposed] from API instead.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun ModuleBridge.findModuleEntityIfNotDisposedLegacy(entityStorage: EntityStorage): ModuleEntity =
  findModuleEntity(entityStorage) ?: moduleEntityNotResolved(entityStorage)


/**
 * Reports that the entity of this module cannot be resolved anymore.
 *
 * `ModuleBridgeCleaner` pins the storage of a removed module to the snapshot taken before the removal, so the entity of such a module is
 * still resolvable. Hence, an unresolvable entity means the module is gone for good, and reporting "no data" instead (no facets, no roots,
 * no options) would trick the client into treating a removed module as an empty one. The client should either check the module existence in
 * advance (in a `readAction`), or stop all the activities for the module, which is what [AlreadyDisposedException] does.
 *
 *[store] for oinformation about the place the entity was looked up in
 */
internal fun Module.moduleEntityNotResolved(store: EntityStorage): Nothing {
  val id = (this as? ModuleBridge)?.moduleEntityId ?: name
  val message = "Cannot resolve entity of module $id (store = $store)"
  if (isDisposed) {
    throw AlreadyDisposedException("$message : module already disposed")
  }
  else {
    throw IllegalStateException("$message : save module first")
  }
}
