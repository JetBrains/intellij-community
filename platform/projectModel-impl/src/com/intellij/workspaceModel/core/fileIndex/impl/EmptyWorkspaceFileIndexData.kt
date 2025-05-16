// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EmptyWorkspaceFileIndexData private constructor(private val debugName: String): WorkspaceFileIndexData {
  companion object {
    val NOT_INITIALIZED: EmptyWorkspaceFileIndexData = EmptyWorkspaceFileIndexData("not initialized")
    val RESET: EmptyWorkspaceFileIndexData = EmptyWorkspaceFileIndexData("reset")
  }
  
  override fun getFileInfo(
    file: VirtualFile,
    honorExclusion: Boolean,
    includeContentSets: Boolean,
    includeContentNonIndexableSets: Boolean,
    includeExternalSets: Boolean,
    includeExternalSourceSets: Boolean,
    includeCustomKindSets: Boolean
  ): WorkspaceFileInternalInfo {
    return WorkspaceFileInternalInfo.NonWorkspace.NOT_UNDER_ROOTS
  }

  override fun visitFileSets(visitor: WorkspaceFileSetVisitor) {}
  override fun markDirty(entityPointers: Collection<EntityPointer<WorkspaceEntity>>, filesToInvalidate: Collection<VirtualFile>) {}
  override fun onEntitiesChanged(event: VersionedStorageChange, storageKind: EntityStorageKind) {}
  override fun updateDirtyEntities() {}
  override fun getPackageName(dir: VirtualFile): String? = null
  override fun getDirectoriesByPackageName(packageName: String, includeLibrarySources: Boolean): Query<VirtualFile> = EmptyQuery.getEmptyQuery()
  override fun getFilesByPackageName(packageName: String): Query<VirtualFile> = EmptyQuery.getEmptyQuery()
  override fun resetCustomContributors() {}
  override fun getNonExistentFileSetKinds(url: VirtualFileUrl): Set<NonExistingFileSetKind> = emptySet()

  override fun analyzeVfsChanges(events: List<VFileEvent>): VfsChangeApplier? = null
  override fun onLowMemory() {}
  override fun clearPackageDirectoryCache() {}
  override fun resetFileCache() {}

  override fun toString(): String {
    return "EmptyWorkspaceFileIndexData: $debugName"
  }
}