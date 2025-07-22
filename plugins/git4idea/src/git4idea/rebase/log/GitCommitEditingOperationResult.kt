// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.openapi.util.NlsContexts
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitUtil
import git4idea.branch.GitRebaseParams
import git4idea.commands.Git
import git4idea.findProtectedRemoteBranchContainingCommit
import git4idea.history.GitLogUtil
import git4idea.rebase.GitRebaseUtils.getCommitsRangeToRebase
import git4idea.repo.GitRepository
import git4idea.reset.GitResetMode

internal sealed class GitCommitEditingOperationResult {
  class Complete(
    val repository: GitRepository,
    private val base: GitRebaseParams.RebaseUpstream,
    private val oldHead: String,
    private val newHead: String,
    val commitToFocus: Hash? = null,
  ) : GitCommitEditingOperationResult() {
    private val firstChangedHash = findFirstChangedHash()

    private fun findFirstChangedHash(): Hash? {
      val changedCommitsRange = getCommitsRangeToRebase(base, newHead)
      val changedCommits = GitLogUtil.collectMetadata(repository.project, repository.root, changedCommitsRange).commits
      return changedCommits.lastOrNull()?.id
    }

    fun checkUndoPossibility(): UndoPossibility {
      repository.update()
      if (repository.currentRevision != newHead) {
        return UndoPossibility.Impossible.HeadMoved
      }
      if (firstChangedHash == null) {
        // list of changed commits is empty and head wasn't moved, so we can easily do undo action
        return UndoPossibility.Possible
      }
      val protectedBranch = findProtectedRemoteBranchContainingCommit(repository, firstChangedHash)
      if (protectedBranch != null) {
        return UndoPossibility.Impossible.PushedToProtectedBranch(protectedBranch)
      }
      return UndoPossibility.Possible
    }

    fun undo(): UndoResult {
      val res = Git.getInstance().reset(repository, GitResetMode.KEEP, oldHead)
      repository.update()
      return if (res.success()) {
        GitUtil.updateAndRefreshChangedVfs(repository, HashImpl.build(newHead))
        UndoResult.Success
      }
      else {
        UndoResult.Error(res.errorOutputAsHtmlString)
      }
    }

    sealed class UndoResult {
      object Success : UndoResult()
      class Error(@NlsContexts.NotificationContent val errorHtml: String) : UndoResult()
    }

    sealed class UndoPossibility {
      sealed class Impossible : UndoPossibility() {
        object HeadMoved : Impossible()
        class PushedToProtectedBranch(val branch: String) : Impossible()
      }

      object Possible : UndoPossibility()
    }
  }

  object Incomplete : GitCommitEditingOperationResult()
}