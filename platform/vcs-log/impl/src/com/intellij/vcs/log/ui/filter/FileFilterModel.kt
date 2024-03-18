// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogRootFilter
import com.intellij.vcs.log.VcsLogStructureFilter
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.NonNls

class FileFilterModel(val roots: Set<VirtualFile>, uiProperties: MainVcsLogUiProperties, filters: VcsLogFilterCollection?) :
  FilterModel.PairFilterModel<VcsLogStructureFilter, VcsLogRootFilter>(VcsLogFilterCollection.STRUCTURE_FILTER,
                                                                       VcsLogFilterCollection.ROOT_FILTER,
                                                                       uiProperties, filters) {

  override fun getFilter1Values(filter1: VcsLogStructureFilter): List<String> = getFilterValues(filter1)
  override fun getFilter2Values(filter2: VcsLogRootFilter): List<String> = getRootFilterValues(filter2)

  override fun createFilter1(values: List<String>): VcsLogStructureFilter = createStructureFilter(values)
  override fun createFilter2(values: List<String>): VcsLogRootFilter? {
    val selectedRoots: MutableList<VirtualFile> = ArrayList()
    for (path in values) {
      val root = LocalFileSystem.getInstance().findFileByPath(path)
      if (root != null) {
        if (roots.contains(root)) {
          selectedRoots.add(root)
        }
        else {
          LOG.warn("Can not find VCS root for filtering $root")
        }
      }
      else {
        LOG.warn("Can not filter by root that does not exist $path")
      }
    }
    if (selectedRoots.isEmpty()) return null
    return VcsLogFilterObject.fromRoots(selectedRoots)
  }

  val rootFilter: VcsLogRootFilter?
    get() = filter2

  var structureFilter: VcsLogStructureFilter?
    get() = filter1
    private set(filter) {
      setFilter(FilterPair(filter, null))
    }

  companion object {
    private const val DIR: @NonNls String = "dir:"
    private const val FILE: @NonNls String = "file:"
    private val LOG = Logger.getInstance(FileFilterModel::class.java)

    fun getRootFilterValues(filter: VcsLogRootFilter): List<String> {
      return filter.roots.map { it.path }
    }

    @JvmStatic
    fun getFilterValues(filter: VcsLogStructureFilter): List<String> {
      return filter.files.map { path -> (if (path.isDirectory) DIR else FILE) + path.path }
    }

    @JvmStatic
    fun createStructureFilter(values: List<String>): VcsLogStructureFilter {
      return VcsLogFilterObject.fromPaths(values.map { path -> extractPath(path) })
    }

    fun extractPath(path: String): FilePath {
      if (path.startsWith(DIR)) {
        return VcsUtil.getFilePath(path.substring(DIR.length), true)
      }
      else if (path.startsWith(FILE)) {
        return VcsUtil.getFilePath(path.substring(FILE.length), false)
      }
      return VcsUtil.getFilePath(path)
    }
  }
}