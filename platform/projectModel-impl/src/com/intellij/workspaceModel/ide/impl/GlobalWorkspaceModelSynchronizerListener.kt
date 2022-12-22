// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity

class GlobalWorkspaceModelSynchronizerListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    if (!GlobalLibraryTableBridge.isEnabled()) return
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    // Avoid handling events if change was made by global workspace model
    if (globalWorkspaceModel.isFromGlobalWorkspaceModel) return

    if (isContainingGlobalEntities(event)) {
      globalWorkspaceModel.syncEntitiesWithProject(project)
    }
  }

  private fun isContainingGlobalEntities(event: VersionedStorageChange): Boolean {
    return event.getChanges(LibraryEntity::class.java).any { change ->
      val libraryEntity = when (change) {
        is EntityChange.Added -> change.newEntity
        is EntityChange.Replaced -> change.newEntity
        is EntityChange.Removed -> change.oldEntity
      }
      libraryEntity.entitySource is JpsFileEntitySource.ExactGlobalFile
    }
  }
}