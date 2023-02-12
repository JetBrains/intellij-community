// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.platform.workspaceModel.jps.JpsGlobalFileEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ExcludeUrlEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryPropertiesEntity
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