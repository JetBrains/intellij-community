// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.storage.WorkspaceEntity

interface WorkspaceFileIndexEx : WorkspaceFileIndex {
  /**
   * An internal variant of [findFileSetWithCustomData] method which provides more information if [file] isn't included in the workspace.
   */
  fun getFileInfo(file: VirtualFile,
                  honorExclusion: Boolean,
                  includeContentSets: Boolean,
                  includeExternalSets: Boolean,
                  includeExternalSourceSets: Boolean): WorkspaceFileInternalInfo

  /**
   * Reset caches which cannot be updated incrementally.
   */
  fun resetCustomContributors()

  /**
   * Notifies the index about changes in files associated with the entities. 
   * Must be called inside Write Action, and [updateDirtyEntities] must be called before that Write Action finishes.
   * It may happen that an implementation of [com.intellij.openapi.vfs.newvfs.BulkFileListener] will try to get information about changed
   * files synchronously during the same Write Action, in that case the index should recalculate the data to provide correct results.
   * @param entities entities which refer to files which were created, deleted, moved or renamed
   * @param filesToInvalidate files which were deleted or moved to other directories and was referenced from some entities
   */
  fun markDirty(entities: Collection<WorkspaceEntity>, filesToInvalidate: Collection<VirtualFile>)

  /**
   * Forces the index to update entities marked by [markDirty]. Must be called during execution of the same Write Action as [markDirty].
   */
  fun updateDirtyEntities()

  companion object {
    @JvmField
    val IS_ENABLED: Boolean = Registry.`is`("platform.projectModel.workspace.model.file.index")
  }
}

sealed interface WorkspaceFileInternalInfo {
  enum class NonWorkspace : WorkspaceFileInternalInfo {
    /** File or one of its parents is marked as 'ignored' */
    IGNORED,
    /** File or one of its parents is excluded */
    EXCLUDED, 
    /** File is not located under any registered workspace root */
    NOT_UNDER_ROOTS,

    /** File is invalid */
    INVALID
  }
}