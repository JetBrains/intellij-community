// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Provides access to the information collected from [WorkspaceFileIndexContributor]s.
 * This interfaces supersedes [ProjectFileIndex][com.intellij.openapi.roots.ProjectFileIndex] and [ModuleFileIndex][com.intellij.openapi.roots.ModuleFileIndex], 
 * and provides more generic API which isn't bound to concepts like 'module' and 'source root', which are actually specific for Java projects.
 * Implementations of [ProjectFileIndex][com.intellij.openapi.roots.ProjectFileIndex] and 
 * [ModuleFileIndex][com.intellij.openapi.roots.ModuleFileIndex] delegate to this interface.
 * 
 * See [the package documentation](psi_element://com.intellij.workspaceModel.core.fileIndex) for more details.
 */
interface WorkspaceFileIndex {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): WorkspaceFileIndex = project.service()
  }

  /**
   * Returns `true` if [file] is included to the workspace. 
   * I.e., it's located under a registered file set of any [kind][WorkspaceFileKind], and isn't excluded or ignored.
   * Currently, this function is equivalent to [com.intellij.openapi.roots.ProjectFileIndex.isInProject].
   */
  @RequiresReadLock
  fun isInWorkspace(file: VirtualFile): Boolean

  /**
   * Returns `true` if [file] is included to the workspace with [content][WorkspaceFileKind.isContent] kind assigned to it.
   * Currently, this function is equivalent to [com.intellij.openapi.roots.ProjectFileIndex.isInContent].
   */
  @RequiresReadLock
  fun isInContent(file: VirtualFile): Boolean

  /**
   * Return the root file of a file set of [content][WorkspaceFileKind.isContent] kind containing [file]. 
   * If [file] doesn't belong to such a file set, `null` is returned.
   * 
   * This function is similar to [com.intellij.openapi.roots.ProjectFileIndex.getContentRootForFile], but it processes custom file sets as 
   * well, not only content roots of the project's modules.
   * @param honorExclusion determines whether exclusions should be taken into account when searching for the file set.
   */
  @RequiresReadLock
  fun getContentFileSetRoot(file: VirtualFile, honorExclusion: Boolean): VirtualFile?

  /**
   * Checks whether a file identified by [url] will belong to a file set of [content][WorkspaceFileKind.isContent] kind. This function
   * is supposed to be used only if the file and its possible parent file sets aren't created yet, in other cases [isInContent] should be 
   * used instead.
   */
  @RequiresReadLock
  fun isUrlInContent(url: String): ThreeState

  /**
   * Searches for the first parent of [file] (or [file] itself) which has an associated [WorkspaceFileSet] taking into account the passed
   * flags. 
   * If there are several instances of [WorkspaceFileSet] associated with the found file, any of them is returned. If you need to get a
   * specific file set in such case, use [findFileSetWithCustomData] instead.
   * 
   * @param honorExclusion if `true` the function will return `null` if [file] is excluded from the found file set
   * @param includeContentSets if `true` file sets of [content][WorkspaceFileKind.isContent] kind will be processed
   * @param includeExternalSets if `true` file sets of [WorkspaceFileKind.EXTERNAL] kind will be processed
   * @param includeExternalSourceSets if `true` file sets of [WorkspaceFileKind.EXTERNAL_SOURCE] kind will be processed
   */
  fun findFileSet(file: VirtualFile,
                  honorExclusion: Boolean,
                  includeContentSets: Boolean,
                  includeExternalSets: Boolean,
                  includeExternalSourceSets: Boolean
  ): WorkspaceFileSet?

  /**
   * The same as [findFileSet], but returns a file set which has custom data of type [customDataClass] associated with the found file or
   * `null` if no such file set is found.
   */
  fun <D: WorkspaceFileSetData> findFileSetWithCustomData(
    file: VirtualFile,
    honorExclusion: Boolean,
    includeContentSets: Boolean,
    includeExternalSets: Boolean,
    includeExternalSourceSets: Boolean,
    customDataClass: Class<out D>
  ): WorkspaceFileSetWithCustomData<D>?
}

/**
 * Describes a set of files registered by [WorkspaceFileSetRegistrar.registerFileSet] function.
 */
interface WorkspaceFileSet {
  val root: VirtualFile
  val kind: WorkspaceFileKind
}

/**
 * Base interface for custom data which may be associated with [WorkspaceFileSet].
 */
interface WorkspaceFileSetData

/**
 * Describes a set of files registered by [WorkspaceFileSetRegistrar.registerFileSet] function with provided custom data. 
 * Use [WorkspaceFileIndex.findFileSetWithCustomData] to retrieve the data from the index.
 */
interface WorkspaceFileSetWithCustomData<D : WorkspaceFileSetData> : WorkspaceFileSet {
  val data: D
}