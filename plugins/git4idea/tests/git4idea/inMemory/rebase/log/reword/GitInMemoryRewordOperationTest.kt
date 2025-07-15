// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.reword

import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.inMemory.GitObjectRepository
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertLastMessage
import git4idea.test.message
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotEquals

class GitInMemoryRewordOperationTest : GitSingleRepoTest() {
  fun `test reword last commit`() {
    val parentCommit = file("a").create().addCommit("Add a").details()
    val commit = file("a").append("new content").addCommit("Modify a").details()

    val objectRepo = GitObjectRepository(repo)
    val newMessage = "Reworded commit message"

    refresh()
    updateChangeListManager()

    executeInMemoryReword(objectRepo, commit, newMessage) as GitCommitEditingOperationResult.Complete
    assertLastMessage(newMessage)

    val newParentCommit = git("rev-parse HEAD~1")
    assertEquals(parentCommit.id.asString(), newParentCommit)

    val diffOutput = git("diff ${commit.id.asString()} HEAD")
    assertTrue("Git diff should show no changes between original and reworded commit", diffOutput.isEmpty())
  }

  fun `test reword previous commit`() {
    file("a").create().addCommit("Add a").details()
    val commit = file("a").append("content").addCommit("Old message").details()
    file("b").create().addCommit("Latest commit")

    val objectRepo = GitObjectRepository(repo)
    val newMessage = "New message"

    refresh()
    updateChangeListManager()

    executeInMemoryReword(objectRepo, commit, newMessage) as GitCommitEditingOperationResult.Complete

    val commitMessage = repo.message("HEAD^1")
    assertEquals(newMessage, commitMessage)
  }

  fun `test reword preserves parents and children`() {
    val parentCommit = file("a").create().addCommit("Parent").details()
    val targetCommit = file("b").create().addCommit("Target").details()
    file("c").create().addCommit("Child").details() // childCommit

    val objectRepo = GitObjectRepository(repo)
    val newMessage = "Updated target"

    refresh()
    updateChangeListManager()

    executeInMemoryReword(objectRepo, targetCommit, newMessage) as GitCommitEditingOperationResult.Complete

    val childParent = git("rev-parse HEAD^")
    val targetParent = git("rev-parse HEAD^^")

    assertEquals(parentCommit.id.asString(), targetParent)
    assertNotEquals(targetCommit.id.asString(), childParent)
  }

  fun `test reword initial commit`() {
    val commit = file("a").create().addCommit("Initial").details()

    val objectRepo = GitObjectRepository(repo)
    val newMessage = "Updated initial"

    refresh()
    updateChangeListManager()

    executeInMemoryReword(objectRepo, commit, newMessage) as GitCommitEditingOperationResult.Complete

    assertLastMessage(newMessage)
  }

  fun `test reword with special characters`() {
    val commit = file("a").create().addCommit("Simple message").details()

    val objectRepo = GitObjectRepository(repo)
    val newMessage = "Message with #hash and\n\nmultiple\nlines"

    refresh()
    updateChangeListManager()

    executeInMemoryReword(objectRepo, commit, newMessage) as GitCommitEditingOperationResult.Complete

    assertLastMessage(newMessage)
  }

  private fun executeInMemoryReword(objectRepo: GitObjectRepository, commit: VcsCommitMetadata, newMessage: String): GitCommitEditingOperationResult =
    runBlocking {
      GitInMemoryRewordOperation(repo, objectRepo, commit, newMessage).execute()
    }
}