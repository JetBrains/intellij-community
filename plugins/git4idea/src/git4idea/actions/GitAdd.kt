// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionActionExtension
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitVcs
import git4idea.util.GitFileUtils

class GitAdd : ScheduleForAdditionActionExtension {
  override fun getSupportedVcs(project: Project) = GitVcs.getInstance(project)

  override fun isStatusForAddition(status: FileStatus) =
    status === FileStatus.MODIFIED ||
    status === FileStatus.MERGED_WITH_CONFLICTS ||
    status === FileStatus.ADDED ||
    status === FileStatus.DELETED ||
    status === FileStatus.IGNORED

  @Throws(VcsException::class)
  override fun doAddFiles(project: Project, vcsRoot: VirtualFile, paths: List<FilePath>, containsIgnored: Boolean) {
    GitFileUtils.addPaths(project, vcsRoot, paths, containsIgnored)
  }
}
