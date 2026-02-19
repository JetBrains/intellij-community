// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogRootFilter
import com.intellij.vcs.log.VcsLogStructureFilter
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.NonNls

class FileFilterModel(val roots: Set<VirtualFile>, uiProperties: MainVcsLogUiProperties, filters: VcsLogFilterCollection?) :
  FilterModel.MultipleFilterModel(listOf(VcsLogFilterCollection.STRUCTURE_FILTER, VcsLogFilterCollection.ROOT_FILTER),
                                  uiProperties, filters) {

  private var loggedRootError: Boolean = false

  override fun getFilterValues(filter: VcsLogFilter): List<String>? {
    return when (filter) {
      is VcsLogStructureFilter -> getStructureFilterValues(filter)
      is VcsLogRootFilter -> getRootFilterValues(filter)
      else -> null
    }
  }

  override fun createFilter(key: VcsLogFilterCollection.FilterKey<*>, values: List<String>): VcsLogFilter? {
    return when (key) {
      VcsLogFilterCollection.STRUCTURE_FILTER -> createStructureFilter(values)
      VcsLogFilterCollection.ROOT_FILTER -> {
        val selectedRoots: MutableList<VirtualFile> = ArrayList()
        for (path in values) {
          val root = LocalFileSystem.getInstance().findFileByPath(path)
          if (root != null) {
            if (roots.contains(root)) {
              selectedRoots.add(root)
            }
            else {
              if (!loggedRootError) {
                LOG.warn("Can not find VCS root for filtering $root")
                loggedRootError = true
              }
            }
          }
          else {
            if (!loggedRootError) {
              LOG.warn("Can not filter by root that does not exist $path")
              loggedRootError = true
            }
          }
        }
        if (selectedRoots.isEmpty()) return null
        return VcsLogFilterObject.fromRoots(selectedRoots)
      }
      else -> null
    }
  }

  var rootFilter by filterProperty(VcsLogFilterCollection.ROOT_FILTER, exclusive = true)
  var structureFilter by filterProperty(VcsLogFilterCollection.STRUCTURE_FILTER, exclusive = true)

  companion object {
    private const val DIR: @NonNls String = "dir:"
    private const val FILE: @NonNls String = "file:"
    private val LOG = Logger.getInstance(FileFilterModel::class.java)

    fun getRootFilterValues(filter: VcsLogRootFilter): List<String> {
      return filter.roots.map { it.path }
    }

    @JvmStatic
    fun getStructureFilterValues(filter: VcsLogStructureFilter): List<String> {
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