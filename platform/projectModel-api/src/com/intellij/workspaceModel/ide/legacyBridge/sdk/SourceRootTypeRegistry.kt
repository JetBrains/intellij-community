// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

/**
 * Provides a way to find source roots types by their IDs in the Workspace Model
 */
interface SourceRootTypeRegistry {
  companion object {
    @JvmStatic
    fun getInstance(): SourceRootTypeRegistry = ApplicationManager.getApplication().getService(SourceRootTypeRegistry::class.java)
  }

  fun findTypeById(rootTypeId: SourceRootTypeId): JpsModuleSourceRootType<*>?
}