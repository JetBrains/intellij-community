// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Query
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo.NonWorkspace
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

interface WorkspaceFileIndexEx : WorkspaceFileIndex {
  /**
   * An internal variant of [findFileSetWithCustomData] method which provides more information if [file] isn't included in the workspace
   * or if multiple file sets are associated with [file]. 
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
   * @param entityReferences references to entities which refer to files which were created, deleted, moved or renamed
   * @param filesToInvalidate files which were deleted or moved to other directories and was referenced from some entities
   */
  fun markDirty(entityReferences: Collection<EntityReference<WorkspaceEntity>>, filesToInvalidate: Collection<VirtualFile>)

  /**
   * Forces the index to update entities marked by [markDirty]. Must be called during execution of the same Write Action as [markDirty].
   */
  fun updateDirtyEntities()

  /**
   * Returns package name for [directory] if it's located under source root or classes root of Java library, or `null` otherwise.
   * This is an internal function, plugins must use [com.intellij.openapi.roots.PackageIndex.getPackageNameByDirectory] instead.
   */
  fun getPackageName(directory: VirtualFile): String?

  /**
   * Returns a query producing directories which correspond to [packageName]. 
   * This is an internal function, plugins must use [com.intellij.openapi.roots.PackageIndex.getDirsByPackageName] instead.
   */
  fun getDirectoriesByPackageName(packageName: String, includeLibrarySources: Boolean): Query<VirtualFile>

  /**
   * Returns a query producing directories from [scope] which correspond to [packageName].
   * This is an internal function, plugins must use [com.intellij.openapi.roots.PackageIndex.getDirsByPackageName] instead.
   */
  fun getDirectoriesByPackageName(packageName: String, scope: GlobalSearchScope): Query<VirtualFile>

  /**
   * Initialize the index data if it isn't done yet.
   */
  fun ensureInitialized()

  /**
   * This is a temporary method introduced to support unloading of modules until IDEA-298694 is implemented.
   */
  fun unloadModules(entities: List<ModuleEntity>)

  /**
   * This is a temporary method introduced to support loading back unloaded modules until IDEA-298694 is implemented.
   */
  fun loadModules(entities: List<ModuleEntity>)

  companion object {
    @JvmField
    val IS_ENABLED: Boolean = Registry.`is`("platform.projectModel.workspace.model.file.index")
  }
}

/**
 * A base interface for instances which may be returned from [WorkspaceFileIndexEx.getFileInfo]:
 * * [NonWorkspace] if no file set is associated;
 * * [WorkspaceFileSetWithCustomData] if there is a single file set;
 * * [MultipleWorkspaceFileSets] if there are several associated file sets.
 */
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

internal sealed interface MultipleWorkspaceFileSets : WorkspaceFileInternalInfo {
  val fileSets: List<WorkspaceFileSetWithCustomData<*>>
  fun find(acceptedCustomDataClass: Class<out WorkspaceFileSetData>?): WorkspaceFileSetWithCustomData<*>?
}
