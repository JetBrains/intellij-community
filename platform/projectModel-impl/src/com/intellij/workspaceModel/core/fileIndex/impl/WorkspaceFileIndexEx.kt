// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFsConnectionListener
import com.intellij.platform.workspace.storage.EntityReference
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Query
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import org.jetbrains.annotations.ApiStatus

interface WorkspaceFileIndexEx : WorkspaceFileIndex {
  /**
   * An internal variant of [findFileSetWithCustomData] method which provides more information if [file] isn't included in the workspace
   * or if multiple file sets are associated with [file]. 
   */
  fun getFileInfo(file: VirtualFile,
                  honorExclusion: Boolean,
                  includeContentSets: Boolean,
                  includeExternalSets: Boolean,
                  includeExternalSourceSets: Boolean,
                  includeCustomKindSets: Boolean): WorkspaceFileInternalInfo

  /**
   * Holds references to the currently stored data.
   */
  val indexData: WorkspaceFileIndexData

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
   * Initialize the index data. The index must not be accessed before this function is called.
   */
  suspend fun initialize()

  /**
   * A blocking variant of [initialize]. It's temporary extracted to be used in CodeServer until suspending read actions are supported in it.
   */
  fun initializeBlocking()

  /**
   * There may be thousands of file sets in index, so visiting them all is generally discouraged.
   */
  @ApiStatus.Internal
  fun visitFileSets(visitor: WorkspaceFileSetVisitor)
  
  @ApiStatus.Internal
  fun reset()
}

internal class WorkspaceFileIndexCleaner: PersistentFsConnectionListener {
  override fun beforeConnectionClosed() {
    for (p in ProjectManager.getInstanceIfCreated()?.openProjects.orEmpty()) {
      val fileIndex = p.serviceIfCreated<WorkspaceFileIndex>() as WorkspaceFileIndexEx? ?: continue
      fileIndex.reset()
    }
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
  
  abstract override fun toString(): String
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
  val entitiesToReindex: Set<EntityReference<WorkspaceEntity>>
}