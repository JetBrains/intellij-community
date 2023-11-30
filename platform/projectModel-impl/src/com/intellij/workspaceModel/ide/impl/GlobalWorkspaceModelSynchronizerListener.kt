// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import kotlin.reflect.KClass

class GlobalWorkspaceModelSynchronizerListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    if (!GlobalLibraryTableBridge.isEnabled()) return
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    // Avoid handling events if change was made by global workspace model
    if (globalWorkspaceModel.isFromGlobalWorkspaceModel) return

    if (isContainingGlobalEntities(event, LibraryEntity::class)
        || isContainingGlobalEntities(event, LibraryPropertiesEntity::class)
        || isContainingGlobalEntities(event, ExcludeUrlEntity::class)) {
      globalWorkspaceModel.syncEntitiesWithProject(project)
    }
  }

  private fun isContainingGlobalEntities(event: VersionedStorageChange, entityKClass: KClass<out WorkspaceEntity>): Boolean {
    return event.getChanges(entityKClass.java).any { change ->
      val entity = when (change) {
        is EntityChange.Added -> change.newEntity
        is EntityChange.Replaced -> change.newEntity
        is EntityChange.Removed -> change.oldEntity
      }
      entity.entitySource is JpsGlobalFileEntitySource
    }
  }
}