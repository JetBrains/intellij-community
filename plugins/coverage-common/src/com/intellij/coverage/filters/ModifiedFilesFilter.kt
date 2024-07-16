// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.filters

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
open class ModifiedFilesFilter(private val project: Project) {

  @Volatile
  var hasFilteredFiles: Boolean = false
    private set

  fun resetFilteredFiles() {
    hasFilteredFiles = false
  }

  fun isFileModified(file: VirtualFile): Boolean {
    val isModified = isModifiedLocally(file) || isInModifiedScope(file)
    return isModified.also {
      if (!isModified && !hasFilteredFiles) {
        hasFilteredFiles = true
      }
    }
  }

  open fun isInModifiedScope(file: VirtualFile) = false

  open fun getBranchName(): @Nls String? = null

  private fun isModifiedLocally(file: VirtualFile): Boolean {
    val status = FileStatusManager.getInstance(project).getStatus(file)
    val isCurrentlyChanged = status === FileStatus.MODIFIED || status === FileStatus.ADDED || status === FileStatus.UNKNOWN
    return isCurrentlyChanged
  }

  companion object {
    @JvmStatic
    fun create(project: Project): ModifiedFilesFilter {
      val filter = ModifiedFilesFilterFactory.EP_NAME.computeSafeIfAny {
        it.createFilter(project)
      }
      if (filter != null) return filter
      return ModifiedFilesFilter(project)
    }
  }
}
