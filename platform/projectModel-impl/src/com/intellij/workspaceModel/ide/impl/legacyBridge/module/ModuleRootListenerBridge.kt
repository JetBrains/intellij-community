// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.annotations.ApiStatus

/**
 * Implementation of this interface fires events to [com.intellij.openapi.roots.ModuleRootListener] based on changes in the workspace model.
 */
@ApiStatus.Internal
interface ModuleRootListenerBridge {
  fun fireBeforeRootsChanged(project: Project, event: VersionedStorageChange)
  fun fireRootsChanged(project: Project, event: VersionedStorageChange)
  
  companion object {
    val DUMMY: ModuleRootListenerBridge = object : ModuleRootListenerBridge {
      override fun fireBeforeRootsChanged(project: Project, event: VersionedStorageChange) {}
      override fun fireRootsChanged(project: Project, event: VersionedStorageChange) {}
    }
  }
}