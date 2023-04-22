// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Query
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo.NonWorkspace
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

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
   * Processes [content][com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind.isContent] files from the file sets located under 
   * [fileOrDir] directory using [processor].
   * @param customFilter determines whether an individual file or directory should be processed;
   * @param fileSetFilter determines whether files belonging to a specific file set should be processed;
   * @return `true` if all files were processed, or `false` if processing was stopped because [processor] returned 
   * [STOP][com.intellij.util.containers.TreeNodeProcessingResult.STOP]. 
   */
  fun processContentFilesRecursively(fileOrDir: VirtualFile, processor: ContentIteratorEx, customFilter: VirtualFileFilter?,
                                     fileSetFilter: (WorkspaceFileSetWithCustomData<*>) -> Boolean): Boolean

  /**
   * Forces the index to update entities marked by [markDirty]. Must be called during execution of the same Write Action as [markDirty].
   */
  fun updateDirtyEntities()

  /**
   * Analyzes changes in VFS and determines how the index must be updated.
   */
  @RequiresReadLock
  fun analyzeVfsChanges(events: List<VFileEvent>): VfsChangeApplier? 

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
  suspend fun ensureInitialized()

  /**
   * There may be thousands of file sets in index, so visiting them all is generally discouraged.
   */
  @ApiStatus.Internal
  fun visitFileSets(visitor: WorkspaceFileSetVisitor)
  
  @TestOnly
  fun reset()

  companion object {
    @JvmField
    val IS_ENABLED: Boolean = Registry.`is`("platform.projectModel.workspace.model.file.index", true)
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
    INVALID;

    override fun findFileSet(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): WorkspaceFileSetWithCustomData<*>? = null
  }

  /**
   * Returns a file set stored in this instance which satisfies the given [condition], or `null` if no such file set found.
   */
  fun findFileSet(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): WorkspaceFileSetWithCustomData<*>?
}

internal sealed interface MultipleWorkspaceFileSets : WorkspaceFileInternalInfo {
  val fileSets: List<WorkspaceFileSetWithCustomData<*>>
  fun find(acceptedCustomDataClass: Class<out WorkspaceFileSetData>?): WorkspaceFileSetWithCustomData<*>?
}

@ApiStatus.Experimental
@ApiStatus.Internal
interface WorkspaceFileSetVisitor {
  fun visitIncludedRoot(fileSet: WorkspaceFileSet)
}

@ApiStatus.Internal
interface VfsChangeApplier: AsyncFileListener.ChangeApplier {
  val entitiesToReindex: List<EntityReference<WorkspaceEntity>>
}