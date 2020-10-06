// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.AbstractCommitter
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitUtil.getRepositoryForFile
import git4idea.checkin.GitCommitOptions
import git4idea.checkin.GitRepositoryCommitter
import git4idea.index.vfs.GitIndexFileSystemRefresher

internal class GitStageCommitState(val roots: Collection<VirtualFile>, val commitMessage: String)

internal class GitStageCommitter(project: Project, private val commitState: GitStageCommitState, commitContext: CommitContext) :
  AbstractCommitter(project, emptyList(), commitState.commitMessage, commitContext) {

  val successfulRoots = mutableSetOf<VirtualFile>()
  val failedRoots = mutableMapOf<VirtualFile, VcsException>()

  override fun commit() {
    for (root in commitState.roots) {
      try {
        commitRoot(root)
        successfulRoots.add(root)
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
  override fun onSuccess() = Unit
  override fun onFailure() = Unit

  override fun onFinish() {
    VcsFileUtil.markFilesDirty(project, commitState.roots)
    GitIndexFileSystemRefresher.refreshRoots(project, commitState.roots)
  }

  @Throws(VcsException::class)
  private fun commitRoot(root: VirtualFile) {
    val repository = getRepositoryForFile(project, root)
    val committer = GitRepositoryCommitter(repository, GitCommitOptions(commitContext))

    committer.commitStaged(commitMessage)
  }
}