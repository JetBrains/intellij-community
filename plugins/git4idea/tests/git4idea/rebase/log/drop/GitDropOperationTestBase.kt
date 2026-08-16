// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.drop

import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoPossibility
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoResult
import git4idea.test.GitSingleRepoContext
import git4idea.test.assertCommitted
import git4idea.test.file
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The shared drop-commit test suite. Subclasses provide the way the drop is actually performed
 * (a native rebase or an in-memory one) by implementing [execute].
 */
internal abstract class GitDropOperationTestBase {
  private val contextFixture = gitSingleRepoContextFixture()
  protected val context: GitSingleRepoContext get() = contextFixture.get()

  protected abstract fun GitSingleRepoContext.execute(commitsToDrop: List<VcsCommitMetadata>): GitCommitEditingOperationResult

  @Test
  fun `test drop last commit`(): Unit = with(context) {
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

  @Test
  fun `test drop middle commit`(): Unit = with(context) {
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

  @Test
  fun `test drop non-linear history`(): Unit = with(context) {
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

  @Test
  fun `test undo dropping of the last commit`(): Unit = with(context) {
    file("a").create().addCommit("Commit a").details()
    file("b").create().addCommit("Commit b").details()
    val commitToDrop = file("c").create().addCommit("Commit c").details()

    refresh()
    updateChangeListManager()

    val operationResult = execute(listOf(commitToDrop)) as Complete

    assertThat(runBlocking { operationResult.checkUndoPossibility() }).isInstanceOf(UndoPossibility.Possible::class.java)
    assertThat(operationResult.undo()).isInstanceOf(UndoResult.Success::class.java)

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
