// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.squash

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoPossibility
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoResult
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertCommitted
import git4idea.test.assertLastMessage
import git4idea.test.assertMessage
import git4idea.test.message

internal abstract class GitSquashOperationTestBase : GitSingleRepoTest() {
  protected abstract fun execute(commitsToSquash: List<VcsCommitMetadata>, newMessage: String): GitCommitEditingOperationResult

  fun `test squash last few commits`() {
    val commitA = file("a").create().addCommit("Commit a").details()
    val commitB = file("b").create().addCommit("Commit b").details()
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"

    execute(commitsToSquash, newMessage)

    assertLastMessage(newMessage)
    repo.assertCommitted {
      added("a")
      added("b")
      added("c")
    }
  }

  fun `test squash few non-last commits`() {
    file("before").create().addCommit("Commit before")
    val commitA = file("a").create().addCommit("Commit a").details()
    val commitB = file("b").create().addCommit("Commit b").details()
    val commitC = file("c").create().addCommit("Commit c").details()
    file("after").create().addCommit("Commit after")
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    execute(commitsToSquash, newMessage)

    assertMessage(newMessage, repo.message("HEAD^"))
    repo.assertCommitted(1) {
      added("after")
    }
    repo.assertCommitted(2) {
      added("a")
      added("b")
      added("c")
    }
    repo.assertCommitted(3) {
      added("before")
    }
  }

  fun `test squash non-linear history`() {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between1").create().addCommit("Commit between1")
    val commitB = file("b").create().addCommit("Commit b").details()
    file("between2").create().addCommit("Commit between2")
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    execute(commitsToSquash, newMessage)

    assertMessage(newMessage, repo.message("HEAD~2"))
    repo.assertCommitted(1) {
      added("between2")
    }
    repo.assertCommitted(2) {
      added("between1")
    }
    repo.assertCommitted(3) {
      added("a")
      added("b")
      added("c")
    }
  }

  fun `test undo squash non-linear history`() {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between1").create().addCommit("Commit between1")
    val commitB = file("b").create().addCommit("Commit b").details()
    file("between2").create().addCommit("Commit between2")
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    val operationResult = execute(commitsToSquash, newMessage) as Complete

    assertTrue(operationResult.checkUndoPossibility() is UndoPossibility.Possible)
    val undoResult = operationResult.undo()
    assertTrue(undoResult is UndoResult.Success)

    repo.assertCommitted(1) {
      added("c")
    }
    repo.assertCommitted(2) {
      added("between2")
    }
    repo.assertCommitted(3) {
      added("b")
    }
    repo.assertCommitted(5) {
      added("a")
    }
  }

  fun `test undo squash non-linear history is not allowed if repository changed`() {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between1").create().addCommit("Commit between1")
    val commitB = file("b").create().addCommit("Commit b").details()
    file("between2").create().addCommit("Commit between2")
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    val operationResult = execute(commitsToSquash, newMessage) as Complete

    file("new").create().addCommit("new")

    assertTrue(operationResult.checkUndoPossibility() is UndoPossibility.Impossible.HeadMoved)
  }

  fun `test undo squash linear history is not allowed if first changed commit is pushed to protected branch`() {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between1").create().addCommit("Commit between1")
    val commitB = file("b").create().addCommit("Commit b").details()
    file("between2").create().addCommit("Commit between2")
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    val operationResult = execute(commitsToSquash, newMessage) as Complete

    git("update-ref refs/remotes/origin/master HEAD~2")

    assertTrue(operationResult.checkUndoPossibility() is UndoPossibility.Impossible.PushedToProtectedBranch)
  }
}