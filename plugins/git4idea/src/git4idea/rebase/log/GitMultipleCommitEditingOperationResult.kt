// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.vcs.log.Hash
import git4idea.commands.Git
import git4idea.findProtectedRemoteBranchContainingCommit
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import git4idea.reset.GitResetMode

internal sealed class GitMultipleCommitEditingOperationResult {
  class Complete(
    private val repository: GitRepository,
    private val base: String,
    private val oldHead: String,
    private val newHead: String
  ) : GitMultipleCommitEditingOperationResult() {
    private val firstChangedHash = findFirstChangedHash()

    private fun findFirstChangedHash(): Hash? {
      val changedCommitsRange = "$base..$newHead"
      val changedCommits = GitLogUtil.collectMetadata(repository.project, repository.root, changedCommitsRange).commits
      return changedCommits.lastOrNull()?.id
    }

    fun checkUndoPossibility(): UndoPossibility {
      repository.update()
      if (repository.currentRevision != newHead) {
        return UndoPossibility.HeadMoved
      }
      if (firstChangedHash == null) {
        // list of changed commits is empty and head wasn't moved, so we can easily do undo action
        return UndoPossibility.Possible
      }
      val protectedBranch = findProtectedRemoteBranchContainingCommit(repository, firstChangedHash)
      if (protectedBranch != null) {
        return UndoPossibility.PushedToProtectedBranch(protectedBranch)
      }
      return UndoPossibility.Possible
    }

    fun undo(): UndoResult {
      val res = Git.getInstance().reset(repository, GitResetMode.KEEP, oldHead)
      repository.update()
      return if (res.success()) {
        UndoResult.Success
      }
      else {
        UndoResult.Error(res.errorOutputAsHtmlString)
      }
    }

    sealed class UndoResult {
      object Success : UndoResult()
      class Error(val errorHtml: String) : UndoResult()
    }

    sealed class UndoPossibility {
      object Possible : UndoPossibility()
      object HeadMoved : UndoPossibility()
      class PushedToProtectedBranch(val branch: String) : UndoPossibility()
    }
  }

  object Incomplete : GitMultipleCommitEditingOperationResult()
}