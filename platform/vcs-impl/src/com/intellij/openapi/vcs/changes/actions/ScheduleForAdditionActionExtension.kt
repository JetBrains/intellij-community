// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile

interface ScheduleForAdditionActionExtension {
  companion object {
    val EP_NAME = ExtensionPointName<ScheduleForAdditionActionExtension>("com.intellij.vcs.actions.ScheduleForAdditionActionExtension")
  }

  fun getSupportedVcs(project: Project): AbstractVcs

  /**
   * Additional to [FileStatus.UNKNOWN] statuses for files which can be scheduled for addition
   */
  fun isStatusForAddition(status: FileStatus): Boolean

  fun isStatusForDirectoryAddition(status: FileStatus): Boolean = status != FileStatus.IGNORED

  @Throws(VcsException::class)
  fun doAddFiles(project: Project, vcsRoot: VirtualFile, paths: List<FilePath>, containsIgnored: Boolean)
}