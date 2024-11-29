// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Obsolete
interface WorkspaceModelLegacyBridge {
  fun findModuleEntity(module: Module): ModuleEntity?
  
  fun findLegacyModule(moduleEntity: ModuleEntity): Module?
}
