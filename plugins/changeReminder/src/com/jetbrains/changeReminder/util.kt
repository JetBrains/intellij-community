// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.checkin.GitCheckinEnvironment
import git4idea.history.GitLogUtil

fun CheckinProjectPanel.isAmend(): Boolean {
  if (this !is CommitChangeListDialog) return false
  val gitCheckinOptions = this.additionalComponents
                            .filterIsInstance(GitCheckinEnvironment.GitCheckinOptions::class.java)
                            .firstOrNull()
                          ?: return false
  return gitCheckinOptions.isAmend
}

fun CheckinProjectPanel.getGitRootFiles(project: Project): Map<VirtualFile, Collection<FilePath>> {
  val rootFiles = HashMap<VirtualFile, HashSet<FilePath>>()

  this.files.forEach { file ->
    val filePath = VcsUtil.getFilePath(file.absolutePath)
    val fileVcs = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(filePath)
    if (fileVcs != null && fileVcs.vcs is GitVcs) {
      val fileRoot = fileVcs.path
      if (fileRoot != null) {
        rootFiles.getOrPut(fileRoot) { HashSet() }.add(filePath)
      }
    }
  }

  return rootFiles
}

fun processCommitsFromHashes(project: Project, root: VirtualFile, hashes: List<String>, commitConsumer: (GitCommit) -> Unit): Unit =
  GitLogUtil.readFullDetailsForHashes(
    project,
    root,
    GitVcs.getInstance(project),
    Consumer<GitCommit> {
      commitConsumer(it)
    },
    hashes.toList(),
    true,
    false,
    false,
    false,
    GitLogUtil.DiffRenameLimit.NO_RENAMES
  )

fun GitCommit.changedFilePaths(): List<FilePath> = this.changes.mapNotNull { it.afterRevision?.file ?: it.beforeRevision?.file }