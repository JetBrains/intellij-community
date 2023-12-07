// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.ide.legacyBridge.GlobalSdkTableBridge

class GlobalWorkspaceModelSynchronizerListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    if (!GlobalLibraryTableBridge.isEnabled() && !GlobalSdkTableBridge.isEnabled()) return
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    // Avoid handling events if change was made by global workspace model
    if (globalWorkspaceModel.isFromGlobalWorkspaceModel) return

    if (isContainingGlobalEntities(event)) {
      globalWorkspaceModel.syncEntitiesWithProject(project)
    }
  }

  private fun isContainingGlobalEntities(event: VersionedStorageChange): Boolean {
    return event.getAllChanges().any {
      val entity = it.newEntity ?: it.oldEntity!!
      entity.entitySource is JpsGlobalFileEntitySource
    }
  }
}