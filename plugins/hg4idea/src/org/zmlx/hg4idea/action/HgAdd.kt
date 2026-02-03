// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.action

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionActionExtension
import com.intellij.openapi.vfs.VirtualFile
import org.zmlx.hg4idea.HgVcs
import org.zmlx.hg4idea.command.HgAddCommand

class HgAdd : ScheduleForAdditionActionExtension {

  override fun getSupportedVcs(project: Project) = HgVcs.getInstance(project)!!

  override fun isStatusForAddition(status: FileStatus) =
    status === FileStatus.IGNORED

  override fun doAddFiles(project: Project, vcsRoot: VirtualFile, paths: List<FilePath>, containsIgnored: Boolean) {
    HgAddCommand(project).addWithProgress(paths.mapNotNull(FilePath::getVirtualFile))
  }
}