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
import com.jetbrains.changeReminder.predict.PredictedChange
import com.jetbrains.changeReminder.predict.PredictedFilePath
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.checkin.GitCheckinEnvironment
import git4idea.history.GitCommitRequirements
import git4idea.history.GitCommitRequirements.DiffInMergeCommits
import git4idea.history.GitCommitRequirements.DiffRenameLimit
import git4idea.history.GitLogUtil

private fun CheckinProjectPanel.gitCheckinOptions(): GitCheckinEnvironment.GitCheckinOptions? {
  if (this !is CommitChangeListDialog) return null
  return additionalComponents
           .filterIsInstance(GitCheckinEnvironment.GitCheckinOptions::class.java)
           .firstOrNull()
         ?: return null
}

fun CheckinProjectPanel.isAmend(): Boolean = commitWorkflowHandler.isAmendCommitMode

fun CheckinProjectPanel.author(): String? = this.gitCheckinOptions()?.author

fun CheckinProjectPanel.getGitRootFiles(project: Project): Map<VirtualFile, Collection<FilePath>> {
  val rootFiles = HashMap<VirtualFile, HashSet<FilePath>>()

  this.selectedChanges.forEach { file ->
    val filePath = ChangesUtil.getFilePath(file)
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
  val requirements = GitCommitRequirements(diffRenameLimit = DiffRenameLimit.NO_RENAMES,
                                           diffInMergeCommits = DiffInMergeCommits.NO_DIFF)
  GitLogUtil.readFullDetailsForHashes(project, root, hashes.toList(), requirements, Consumer<GitCommit> {
    commitConsumer(it)
  })
}

fun GitCommit.changedFilePaths(): List<FilePath> = this.changes.mapNotNull { ChangesUtil.getFilePath(it) }

internal fun Collection<FilePath>.toPredictedFiles(changeListManager: ChangeListManager) = this.mapNotNull {
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