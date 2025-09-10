// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.squash

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.inMemory.rebase.log.GitInMemoryOperationTest
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.assertCommitted
import git4idea.test.assertLastMessage
import git4idea.test.assertLatestHistory
import org.junit.jupiter.api.assertThrows

internal class GitInMemorySquashConsecutiveOperationTest : GitInMemoryOperationTest() {
  private val SQUASH_MESSAGE = "Squashed commit message"

  private fun runTest(
    setup: () -> List<VcsCommitMetadata>,
    assertion: () -> Unit
  ) {
    val commitsToSquash = setup()
    refresh()
    updateChangeListManager()
    GitInMemorySquashConsecutiveOperation(objectRepo, commitsToSquash, SQUASH_MESSAGE).run() as GitCommitEditingOperationResult.Complete
    assertion()
  }

  fun `test squash last consecutive commits`() {
    runTest(
      setup = {
        val commitA = file("a").create().addCommit("Commit a").details()
        val commitB = file("b").create().addCommit("Commit b").details()
        val commitC = file("c").create().addCommit("Commit c").details()
        listOf(commitC, commitB, commitA)
      },
      assertion = {
        assertLastMessage(SQUASH_MESSAGE)
        repo.assertCommitted {
          added("a")
          added("b")
          added("c")
        }
      }
    )
  }

  fun `test squash non-last consecutive commits`() {
    runTest(
      setup = {
        file("before").create().addCommit("Commit before")
        val commitA = file("a").create().addCommit("Commit a").details()
        val commitB = file("b").create().addCommit("Commit b").details()
        val commitC = file("c").create().addCommit("Commit c").details()
        file("after").create().addCommit("Commit after")
        listOf(commitC, commitB, commitA)
      },
      assertion = {
        repo.assertLatestHistory("Commit after", SQUASH_MESSAGE)
        with(repo) {
          assertCommitted(1) { added("after") }
          assertCommitted(2) {
            added("a")
            added("b")
            added("c")
          }
          assertCommitted(3) {
            added("before")
          }
        }
      }
    )
  }

  fun `test squash consecutive commits with complex changes`() {
    runTest(
      setup = {
        file("file1").create("initial content").addCommit("Initial commit")
        val commitA = file("file1").write("modified content").addCommit("Modify file1").details()
        val commitB = file("file2").create("new file content").addCommit("Add file2").details()
        val commitC = file("file1").write("final content").addCommit("Modify file1 again").details()
        listOf(commitC, commitB, commitA)
      },
      assertion = {
        assertLastMessage(SQUASH_MESSAGE)
        repo.assertCommitted {
          modified("file1")
          added("file2")
        }
      }
    )
  }

  fun `test fail when commits are not consecutive`() {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between").create().addCommit("Commit between")
    val commitB = file("b").create().addCommit("Commit b").details()
    val nonConsecutiveCommits = listOf(commitB, commitA)

    refresh()
    updateChangeListManager()

    assertThrows<IllegalArgumentException> {
      GitInMemorySquashConsecutiveOperation(objectRepo, nonConsecutiveCommits, SQUASH_MESSAGE).run() as GitCommitEditingOperationResult.Complete
    }
  }
}