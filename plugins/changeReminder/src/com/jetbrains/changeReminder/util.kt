// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcsUtil.VcsUtil
import com.jetbrains.changeReminder.predict.PredictedChange
import com.jetbrains.changeReminder.predict.PredictedFilePath
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.checkin.GitCheckinEnvironment
import git4idea.history.GitCommitRequirements
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

fun processCommitsFromHashes(project: Project, root: VirtualFile, hashes: List<String>, commitConsumer: (GitCommit) -> Unit) {
  val requirements = GitCommitRequirements(diffRenameLimit = GitCommitRequirements.DiffRenameLimit.NO_RENAMES,
                                           preserveOrder = false)
  GitLogUtil.readFullDetailsForHashes(project, root, GitVcs.getInstance(project), hashes.toList(),
                                      requirements, false, Consumer<GitCommit> {
    commitConsumer(it)
  })
}

fun GitCommit.changedFilePaths(): List<FilePath> = this.changes.mapNotNull { ChangesUtil.getFilePath(it) }

fun Collection<FilePath>.toPredictedFiles(changeListManager: ChangeListManager) = this.mapNotNull {
  val currentChange = changeListManager.getChange(it)
  when {
    currentChange != null -> PredictedChange(currentChange)
    it.virtualFile != null -> PredictedFilePath(it)
    else -> null
  }
}

fun <T> measureSupplierTimeMillis(supplier: () -> T): Pair<Long, T> {
  val startTime = System.currentTimeMillis()
  val result = supplier()
  val executionTime = System.currentTimeMillis() - startTime

  return Pair(executionTime, result)
}