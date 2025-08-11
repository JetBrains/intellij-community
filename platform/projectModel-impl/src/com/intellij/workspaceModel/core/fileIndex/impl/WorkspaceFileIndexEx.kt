// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFsConnectionListener
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Query
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import org.jetbrains.annotations.ApiStatus

interface WorkspaceFileIndexEx : WorkspaceFileIndex {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): WorkspaceFileIndexEx = WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexEx
  }
  /**
   * An internal variant of [findFileSetWithCustomData] method which provides more information if [file] isn't included in the workspace
   * or if multiple file sets are associated with [file]. 
   */
  fun getFileInfo(file: VirtualFile,
                  honorExclusion: Boolean,
                  includeContentSets: Boolean,
                  includeContentNonIndexableSets: Boolean,
                  includeExternalSets: Boolean,
                  includeExternalSourceSets: Boolean,
                  includeCustomKindSets: Boolean): WorkspaceFileInternalInfo

  /**
   * Searches for the first parent of [file] (or [file] itself), which has an associated [WorkspaceFileSet]s (taking into account
   * passed flags), and returns entities of type [E] from which these filesets were contributed.
   * 
   * Note that the result of this function depends on how exactly [com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor]
   * for entities of type [E] are implemented.
   * If the contributor is actually registered for a child entity of [E], the function will return nothing.
   */
  fun <E: WorkspaceEntity> findContainingEntities(file: VirtualFile,
                                                  entityClass: Class<E>,
                                                  honorExclusion: Boolean,
                                                  includeContentSets: Boolean,
                                                  includeContentNonIndexableSets: Boolean,
                                                  includeExternalSets: Boolean,
                                                  includeExternalSourceSets: Boolean,
                                                  includeCustomKindSets: Boolean): Collection<E>

  /**
   * Searches for the first parent of [file] (or [file] itself), which has an associated [WorkspaceFileSet]s (taking into account
   * passed flags), and returns all entities from which these filesets were contributed.
   */
  @ApiStatus.Experimental
  fun findContainingEntities(
    file: VirtualFile,
    honorExclusion: Boolean,
    includeContentSets: Boolean,
    includeContentNonIndexableSets: Boolean,
    includeExternalSets: Boolean,
    includeExternalSourceSets: Boolean,
    includeCustomKindSets: Boolean,
  ): Collection<WorkspaceEntity>

  /**
   * Holds references to the currently stored data.
   */
  val indexData: WorkspaceFileIndexData

  /**
   * Processes [indexable][com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind.isIndexable] files from the file sets located under
   * [fileOrDir] directory using [processor].
   * @param customFilter determines whether an individual file or directory should be processed;
   * @param fileSetFilter determines whether files belonging to a specific file set should be processed;
   * @return `true` if all files were processed, or `false` if processing was stopped because [processor] returned 
   * [STOP][com.intellij.util.containers.TreeNodeProcessingResult.STOP]. 
   */
  fun processIndexableFilesRecursively(fileOrDir: VirtualFile, processor: ContentIteratorEx, customFilter: VirtualFileFilter?,
                                       fileSetFilter: (WorkspaceFileSetWithCustomData<*>) -> Boolean): Boolean

  /**
   * Returns package name for [fileOrDir] if it's a single file source root, or a directory located under source root or 
   * classes root of a Java library. Returns `null` otherwise.
   * This is an internal function, plugins must use [com.intellij.openapi.roots.PackageIndex.getPackageNameByDirectory] instead.
   */
  fun getPackageName(fileOrDir: VirtualFile): String?

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
   * Returns a query producing single file source root files which correspond to [packageName].
   * This is an internal function, plugins must use [com.intellij.openapi.roots.PackageIndex.getFilesByPackageName] instead.
   */
  @ApiStatus.Experimental
  fun getFilesByPackageName(packageName: String): Query<VirtualFile>

  /**
   * Initialize the index data. The index must not be accessed before this function is called.
   */
  @ApiStatus.Internal
  suspend fun initialize()

  /**
   * A blocking variant of [initialize]. It's temporary extracted to be used in CodeServer until suspending read actions are supported in it.
   */
  @ApiStatus.Internal
  fun initializeBlocking()

  /**
   * There may be thousands of file sets in index, so visiting them all is generally discouraged.
   */
  @ApiStatus.Internal
  @RequiresReadLock
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

    override val fileSets: List<WorkspaceFileSetWithCustomData<*>> get() = emptyList()

    override fun findFileSet(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): WorkspaceFileSetWithCustomData<*>? = null
    override fun findFileSets(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): List<WorkspaceFileSetWithCustomData<*>> = emptyList()
  }

  /**
   * A list of file sets with custom data stored in this instance.
   */
  val fileSets: List<WorkspaceFileSetWithCustomData<*>>

  /**
   * Returns a file set stored in this instance which satisfies the given [condition], or `null` if no such file set found.
   */
  fun findFileSet(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): WorkspaceFileSetWithCustomData<*>?

  /**
   * Returns file sets stored in this instance which satisfies the given [condition]
   */
  @ApiStatus.Experimental
  fun findFileSets(condition: (WorkspaceFileSetWithCustomData<*>) -> Boolean): List<WorkspaceFileSetWithCustomData<*>>
  
  abstract override fun toString(): String
}

@ApiStatus.Internal
sealed interface MultipleWorkspaceFileSets : WorkspaceFileInternalInfo {
  override val fileSets: List<WorkspaceFileSetWithCustomData<*>>
  fun find(acceptedCustomDataClass: Class<out WorkspaceFileSetData>?): WorkspaceFileSetWithCustomData<*>?
}

@ApiStatus.Experimental
@ApiStatus.Internal
fun interface WorkspaceFileSetVisitor {
  fun visitIncludedRoot(fileSet: WorkspaceFileSet, entityPointer: EntityPointer<WorkspaceEntity>)
}

@ApiStatus.Internal
interface VfsChangeApplier: AsyncFileListener.ChangeApplier {
  val entitiesToReindex: Set<EntityPointer<WorkspaceEntity>>
}