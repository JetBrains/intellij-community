// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.drop

import git4idea.rebase.log.GitMultipleCommitEditingOperationResult.Complete
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult.Complete.UndoPossibility
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult.Complete.UndoResult
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertCommitted

class GitDropOperationTest : GitSingleRepoTest() {
  fun `test drop last commit`() {
    file("a").create().addCommit("Commit a").details()
    file("b").create().addCommit("Commit b").details()
    val commitToDrop = file("c").create().addCommit("Commit c").details()

    refresh()
    updateChangeListManager()

    GitDropOperation(repo).execute(listOf(commitToDrop))

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

    GitDropOperation(repo).execute(listOf(commitToDrop))

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

    GitDropOperation(repo).execute(listOf(commitToDropD, commitToDropB))

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

    val operationResult = GitDropOperation(repo).execute(listOf(commitToDrop)) as Complete

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