// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.filters

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ModifiedFilesFilter(private val project: Project) {

  @Volatile
  var hasFilteredFiles: Boolean = false
    private set

  fun resetFilteredFiles() {
    hasFilteredFiles = false
  }

  fun isFileModified(file: VirtualFile): Boolean {
    val isModified = isModifiedLocally(file)
    return isModified.also {
      if (!isModified && !hasFilteredFiles) {
        hasFilteredFiles = true
      }
    }
  }

  private fun isModifiedLocally(file: VirtualFile): Boolean {
    val status = FileStatusManager.getInstance(project).getStatus(file)
    val isCurrentlyChanged = status === FileStatus.MODIFIED || status === FileStatus.ADDED || status === FileStatus.UNKNOWN
    return isCurrentlyChanged
  }

  companion object {
    @JvmStatic
    fun create(project: Project): ModifiedFilesFilter {
      return ModifiedFilesFilter(project)
    }
  }
}
