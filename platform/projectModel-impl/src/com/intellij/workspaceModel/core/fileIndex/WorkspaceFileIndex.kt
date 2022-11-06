// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides access to the information collected from [WorkspaceFileIndexContributor]s.
 * If `platform.projectModel.workspace.model.file.index` registry option is enabled, this index is used instead of [com.intellij.openapi.roots.impl.DirectoryIndex]
 * in [com.intellij.openapi.roots.ProjectFileIndex] and [com.intellij.openapi.roots.ModuleFileIndex].
 */
interface WorkspaceFileIndex {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): WorkspaceFileIndex = project.service()
  }

  /**
   * Searches for the first parent of [file] (or [file] itself) which has an associated [WorkspaceFileSet] taking into account the passed
   * flags. 
   * If there are several instances of [WorkspaceFileSet] associated with the found file, any of them is returned. If you need to get a
   * specific file set in such case, use [findFileSetWithCustomData] instead.
   * 
   * @param honorExclusion if `true` the function will return `null` if [file] is excluded from the found file set
   * @param includeContentSets if `true` file sets of [WorkspaceFileKind.CONTENT] kind will be processed
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
