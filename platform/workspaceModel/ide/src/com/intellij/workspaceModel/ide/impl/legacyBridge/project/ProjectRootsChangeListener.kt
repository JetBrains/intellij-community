// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChanged
import com.intellij.workspaceModel.storage.bridgeEntities.*

internal class ProjectRootsChangeListener(private val project: Project) {
  fun beforeChanged(event: VersionedStorageChanged) {
    if (project.isDisposed || Disposer.isDisposing(project)) return
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is ProjectRootManagerBridge) return
    val performUpdate = shouldFireRootsChanged(event, project)
    if (performUpdate) projectRootManager.fireRootsChanged(true)
  }

  fun changed(event: VersionedStorageChanged) {
    if (project.isDisposed || Disposer.isDisposing(project)) return
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is ProjectRootManagerBridge) return
    val performUpdate = shouldFireRootsChanged(event, project)
    if (performUpdate) projectRootManager.fireRootsChanged(false)
  }

  private fun shouldFireRootsChanged(events: VersionedStorageChanged, project: Project): Boolean {
    return events.getAllChanges().any {
      val entity = when (it) {
        is EntityChange.Added -> it.entity
        is EntityChange.Removed -> it.entity
        is EntityChange.Replaced -> it.newEntity
      }
      when (entity) {
        // Library changes should not fire any events if the library is not included in any of order entries
        is LibraryEntity -> libraryHasOrderEntry(entity, project)
        is LibraryPropertiesEntity -> libraryHasOrderEntry(entity.library, project)
        is ModuleEntity, is JavaModuleSettingsEntity, is ModuleCustomImlDataEntity, is ModuleGroupPathEntity,
          is SourceRootEntity, is JavaSourceRootEntity, is JavaResourceRootEntity, is CustomSourceRootPropertiesEntity,
          is ContentRootEntity -> true
        else -> false
      }
    }
  }

  private fun libraryHasOrderEntry(library: LibraryEntity, project: Project): Boolean {
    if (library.tableId is LibraryTableId.ModuleLibraryTableId) {
      return true
    }
    val libraryName = library.name
    ModuleManager.getInstance(project).modules.forEach { module ->
      val exists = ModuleRootManager.getInstance(module).orderEntries.any { it is LibraryOrderEntry && it.libraryName == libraryName }
      if (exists) return true
    }
    return false
  }
}