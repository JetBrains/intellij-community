package com.jetbrains.changeReminder.commit.handle

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitVcs
import git4idea.checkin.GitCheckinEnvironment

fun CheckinProjectPanel.filePaths(): List<FilePath> =
  this.files
    .map { file -> VcsUtil.getFilePath(file.absolutePath) }

fun CheckinProjectPanel.gitFiles(project: Project): Collection<FilePath> =
  this.filePaths()
    .filter { VcsUtil.getVcsFor(project, it) is GitVcs }


fun CheckinProjectPanel.isAmend(): Boolean {
  if (this !is CommitChangeListDialog) return false
  val gitCheckinOptions = this.additionalComponents
                            .filterIsInstance(GitCheckinEnvironment.GitCheckinOptions::class.java)
                            .firstOrNull()
                          ?: return false
  return gitCheckinOptions.isAmend
}

fun CheckinProjectPanel.getRootFiles(project: Project): Map<VirtualFile, Collection<FilePath>> {
  val rootFiles = HashMap<VirtualFile, HashSet<FilePath>>()

  this.gitFiles(project).forEach { file ->
    val fileRoot = VcsUtil.getVcsRootFor(project, file)
    if (fileRoot != null) {
      rootFiles.getOrPut(fileRoot) { HashSet() }.add(file)
    }
  }

  return rootFiles
}