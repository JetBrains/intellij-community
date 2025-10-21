// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.GlobalStorageEntitySource
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal

internal class GlobalWorkspaceModelSynchronizerListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    val eelMachine = project.getEelDescriptor().machine
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(eelMachine)
    // Avoid handling events if change was made by global workspace model
    if (globalWorkspaceModel.isFromGlobalWorkspaceModel) return

    if (isContainingGlobalEntities(event)) {
      globalWorkspaceModel.syncEntitiesWithProject(project)
    }
  }

  private fun isContainingGlobalEntities(event: VersionedStorageChange): Boolean {
    return (event as VersionedStorageChangeInternal).getAllChanges().any {
      val entity = it.newEntity ?: it.oldEntity!!
      entity.entitySource is GlobalStorageEntitySource
    }
  }
}