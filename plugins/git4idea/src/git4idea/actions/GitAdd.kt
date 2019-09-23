// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionActionExtension
import com.intellij.openapi.vcs.changes.actions.confirmAddFilePaths
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitVcs
import git4idea.i18n.GitBundle.getString
import git4idea.i18n.GitBundle.message
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
    // In some cases directories with FileStatus.NOT_CHANGED may require force add flag.
    // E.g. if some ignored directory contains staged files, Git will not return such directory as ignored.
    // But in fact such directories may contain ignored files and hence required force flag to add.
    val changeListManager = ChangeListManager.getInstance(project)
    val (dirs, otherPathToAdd) = paths.partition { path -> isDirWithNotChangedStatus(changeListManager, path) }
    val confirmedDirs =
      invokeAndWaitIfNeeded {
        confirmAddFilePaths(project, dirs,
                            { getString("confirmation.title.force.add.dir") },
                            { dirPath ->
                              message("confirmation.message.force.add.dir",
                                      FileUtil.getLocationRelativeToUserHome(dirPath.presentableUrl))
                            },
                            getString("confirmation.title.force.add.dirs"))
      }

    val needForceAdd = containsIgnored || confirmedDirs.isNotEmpty()
    GitFileUtils.addPaths(project, vcsRoot, otherPathToAdd + confirmedDirs, needForceAdd)
  }

  private fun isDirWithNotChangedStatus(changeListManager: ChangeListManager, path: FilePath) =
    path.virtualFile?.let { file -> file.isDirectory && changeListManager.getStatus(file) == FileStatus.NOT_CHANGED } ?: false
}
