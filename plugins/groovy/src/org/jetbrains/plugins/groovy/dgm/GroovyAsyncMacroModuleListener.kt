// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dgm

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.util.asSafely
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService

class GroovyAsyncMacroModuleListener(private val project: Project, private val cs: CoroutineScope) {
  internal fun subscribe() {
    cs.launch {
      WorkspaceModel.getInstance(project).changesEventFlow.collect { event ->
        val moduleChanges = event.getChanges(ModuleEntity::class.java)
        if (moduleChanges.none()) {
          return@collect
        }
        for (moduleEntity in moduleChanges) {
          val entityToFlush = moduleEntity.oldEntity ?: continue
          val bridge = event.storageBefore.moduleMap.getDataByEntity(entityToFlush) ?: continue
          project.service<GroovyMacroRegistryService>().asSafely<GroovyMacroRegistryServiceImpl>()?.refreshModule(bridge)
        }
      }
    }
  }
}