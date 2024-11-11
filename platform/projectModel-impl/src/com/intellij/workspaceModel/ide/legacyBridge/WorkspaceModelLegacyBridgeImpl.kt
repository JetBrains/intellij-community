// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Obsolete
internal class WorkspaceModelLegacyBridgeImpl(private val project: Project): WorkspaceModelLegacyBridge {
  override fun findModuleEntity(module: Module): ModuleEntity? =
    module.findModuleEntity(project.workspaceModel.currentSnapshot)

  override fun findLegacyModule(moduleEntity: ModuleEntity): Module? =
    moduleEntity.findModule(project.workspaceModel.currentSnapshot)

}
