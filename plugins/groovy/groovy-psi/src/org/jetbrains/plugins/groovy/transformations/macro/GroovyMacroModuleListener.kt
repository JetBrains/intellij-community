// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.openapi.components.service
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity

class GroovyMacroModuleListener : WorkspaceModelChangeListener {

  override fun changed(event: VersionedStorageChange) {
    val moduleChanges = event.getChanges(ModuleEntity::class.java)
    if (moduleChanges.isEmpty()) {
      return
    }
    for (moduleEntity in moduleChanges) {
      val entitiesToFlush = when (moduleEntity) {
        is EntityChange.Added -> listOf(moduleEntity.entity)
        is EntityChange.Removed -> listOf(moduleEntity.entity)
        is EntityChange.Replaced -> listOf(moduleEntity.oldEntity, moduleEntity.newEntity)
      }
      for (entity in entitiesToFlush) {
        val bridge = event.storageBefore.moduleMap.getDataByEntity(entity) ?: continue
        bridge.project.service<GroovyMacroRegistryService>().refreshModule(bridge)
      }
    }
  }
}