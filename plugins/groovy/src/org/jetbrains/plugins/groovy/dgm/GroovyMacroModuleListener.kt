// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dgm

import com.intellij.openapi.components.service
import com.intellij.util.asSafely
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService

class GroovyMacroModuleListener : WorkspaceModelChangeListener {

  override fun changed(event: VersionedStorageChange) {
    val moduleChanges = event.getChanges(ModuleEntity::class.java)
    if (moduleChanges.isEmpty()) {
      return
    }
    for (moduleEntity in moduleChanges) {
      val entityToFlush = moduleEntity.oldEntity ?: continue
      val bridge = event.storageBefore.moduleMap.getDataByEntity(entityToFlush) ?: continue
      bridge.project.service<GroovyMacroRegistryService>().asSafely<GroovyMacroRegistryServiceImpl>()?.refreshModule(bridge)
    }
  }
}