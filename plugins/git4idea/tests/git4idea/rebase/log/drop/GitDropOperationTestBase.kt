// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.drop

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoPossibility
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoResult
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertCommitted

internal abstract class GitDropOperationTestBase : GitSingleRepoTest() {
  protected abstract fun execute(commitsToDrop: List<VcsCommitMetadata>): GitCommitEditingOperationResult

  fun `test drop last commit`() {
    file("a").create().addCommit("Commit a").details()
    file("b").create().addCommit("Commit b").details()
    val commitToDrop = file("c").create().addCommit("Commit c").details()

    refresh()
    updateChangeListManager()

    execute(listOf(commitToDrop))

    repo.assertCommitted(1) {
      added("b")
    }
    repo.assertCommitted(2) {
      added("a")
    }
  }

  fun `test drop middle commit`() {
    file("a").create().addCommit("Commit a").details()
    val commitToDrop = file("b").create().addCommit("Commit b").details()
    file("c").create().addCommit("Commit c").details()

    refresh()
    updateChangeListManager()

    execute(listOf(commitToDrop))

    repo.assertCommitted(1) {
      added("c")
    }
    repo.assertCommitted(2) {
      added("a")
    }
  }

  fun `test drop non-linear history`() {
    file("a").create().addCommit("Commit a").details()
    val commitToDropB = file("b").create().addCommit("Commit b").details()
    file("c").create().addCommit("Commit c").details()
    val commitToDropD = file("d").create().addCommit("Commit d").details()
    file("e").create().addCommit("Commit e").details()

    refresh()
    updateChangeListManager()

    execute(listOf(commitToDropD, commitToDropB))

    repo.assertCommitted(1) {
      added("e")
    }
    repo.assertCommitted(2) {
      added("c")
    }
    repo.assertCommitted(3) {
      added("a")
    }
  }

  fun `test undo dropping of the last commit`() {
    file("a").create().addCommit("Commit a").details()
    file("b").create().addCommit("Commit b").details()
    val commitToDrop = file("c").create().addCommit("Commit c").details()

    refresh()
    updateChangeListManager()

    val operationResult = execute(listOf(commitToDrop)) as Complete

    assertTrue(operationResult.checkUndoPossibility() is UndoPossibility.Possible)
    val undoResult = operationResult.undo()
    assertTrue(undoResult is UndoResult.Success)

    repo.assertCommitted(1) {
      added("c")
    }
    repo.assertCommitted(2) {
      added("b")
    }
    repo.assertCommitted(3) {
      added("a")
    }
  }
}

