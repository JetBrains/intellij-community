// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.AbstractCommitter
import com.intellij.vcs.commit.commitWithoutChangesRoots
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitUtil
import git4idea.GitUtil.getRepositoryForFile
import git4idea.checkin.GitCommitOptions
import git4idea.checkin.GitPushAfterCommitDialog
import git4idea.checkin.GitRepositoryCommitter
import git4idea.checkin.isPushAfterCommit
import git4idea.repo.GitRepository
import git4idea.repo.isSubmodule
import git4idea.util.GitFileUtils.addPaths

internal class GitStageCommitState(val roots: Set<VirtualFile>, val commitMessage: String)

internal class GitStageCommitter(
  project: Project,
  private val commitState: GitStageCommitState,
  private val toStage: Map<VirtualFile, Collection<FilePath>>,
  commitContext: CommitContext
) : AbstractCommitter(project, emptyList(), commitState.commitMessage, commitContext) {

  val successfulRepositories = mutableSetOf<GitRepository>()
  val failedRoots = mutableMapOf<VirtualFile, VcsException>()

  override fun commit() {
    val roots = commitState.roots + commitContext.commitWithoutChangesRoots.map { it.path }

    for (root in roots) {
      try {
        val toStageInRoot = toStage[root]
        if (toStageInRoot?.isNotEmpty() == true) {
          addPaths(project, root, toStageInRoot, true)
        }

        val repository = getRepositoryForFile(project, root)
        commitRepository(repository)
        successfulRepositories.add(repository)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        val rootError = e.asVcsException()

        addException(rootError)
        failedRoots[root] = rootError
      }
    }
  }

  override fun afterCommit() = Unit

  override fun onSuccess() {
    if (commitContext.isPushAfterCommit) {
      GitPushAfterCommitDialog.showOrPush(project, successfulRepositories)
    }
  }

  override fun onFailure() = Unit

  override fun onFinish() {
    for (repository in successfulRepositories) {
      GitUtil.getRepositoryManager(project).updateRepository(repository.root)
      if (repository.isSubmodule()) {
        VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(repository.root.parent)
      }
    }

    VcsFileUtil.markFilesDirty(project, commitState.roots)
  }

  @Throws(VcsException::class)
  private fun commitRepository(repository: GitRepository) {
    val committer = GitRepositoryCommitter(repository, GitCommitOptions(commitContext))
    committer.commitStaged(commitMessage)
  }
}