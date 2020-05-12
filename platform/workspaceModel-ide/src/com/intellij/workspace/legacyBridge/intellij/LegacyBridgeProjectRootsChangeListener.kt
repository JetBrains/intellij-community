// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.workspace.api.EntityChange
import com.intellij.workspace.api.EntityStoreChanged
import com.intellij.workspace.api.LibraryEntity
import com.intellij.workspace.ide.WorkspaceModelChangeListener

internal class LegacyBridgeProjectRootsChangeListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun beforeChanged(event: EntityStoreChanged) {
    if (project.isDisposed || Disposer.isDisposing(project)) return
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is LegacyBridgeProjectRootManager) return
    val performUpdate = processChanges(event, project)
    if (performUpdate) projectRootManager.fireRootsChanged(true)
  }

  override fun changed(event: EntityStoreChanged) {
    if (project.isDisposed || Disposer.isDisposing(project)) return
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is LegacyBridgeProjectRootManager) return
    val performUpdate = processChanges(event, project)
    projectRootManager.markRootsForRefresh()
    if (performUpdate) projectRootManager.fireRootsChanged(false)
  }

  // Library changes should not fire any events if the library is not included in any of order entries
  private fun processChanges(events: EntityStoreChanged, project: Project): Boolean {
    val libraryChanges = events.getChanges(LibraryEntity::class.java)
    return if (libraryChanges.isNotEmpty() && libraryChanges.count() == events.getAllChanges().count()) {
      for (event in libraryChanges) {
        val res = when (event) {
          is EntityChange.Added -> libraryHasOrderEntry(event.entity.name, project)
          is EntityChange.Removed -> libraryHasOrderEntry(event.entity.name, project)
          is EntityChange.Replaced -> libraryHasOrderEntry(event.newEntity.name, project)
        }
        if (res) return true
      }
      return false
    }
    else true
  }

  private fun libraryHasOrderEntry(name: String, project: Project): Boolean {
    ModuleManager.getInstance(project).modules.forEach { module ->
      val exists = ModuleRootManager.getInstance(module).orderEntries.any { it is LibraryOrderEntry && it.libraryName == name }
      if (exists) return true
    }
    return false
  }
}