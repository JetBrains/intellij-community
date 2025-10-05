// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.reword

import git4idea.inMemory.rebase.log.GitInMemoryOperationTest
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.assertLastMessage
import git4idea.test.message
import org.junit.jupiter.api.Assertions.assertNotEquals

internal class GitInMemoryRewordOperationTest : GitInMemoryOperationTest() {
  fun `test reword last commit`() {
    val parentCommit = file("a").create().addCommit("Add a").details()
    val commit = file("a").append("new content").addCommit("Modify a").details()

    val newMessage = "Reworded commit message"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, commit, newMessage).run() as GitCommitEditingOperationResult.Complete
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

    val newMessage = "New message"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, commit, newMessage).run() as GitCommitEditingOperationResult.Complete

    val commitMessage = repo.message("HEAD^1")
    assertEquals(newMessage, commitMessage)
  }

  fun `test reword preserves parents and children`() {
    val parentCommit = file("a").create().addCommit("Parent").details()
    val targetCommit = file("b").create().addCommit("Target").details()
    file("c").create().addCommit("Child").details() // childCommit

    val newMessage = "Updated target"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, targetCommit, newMessage).run() as GitCommitEditingOperationResult.Complete

    val childParent = git("rev-parse HEAD^")
    val targetParent = git("rev-parse HEAD^^")

    assertEquals(parentCommit.id.asString(), targetParent)
    assertNotEquals(targetCommit.id.asString(), childParent)
  }

  fun `test reword initial commit`() {
    val commit = file("a").create().addCommit("Initial").details()

    val newMessage = "Updated initial"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, commit, newMessage).run() as GitCommitEditingOperationResult.Complete

    assertLastMessage(newMessage)
  }

  fun `test reword with special characters`() {
    val commit = file("a").create().addCommit("Simple message").details()

    val newMessage = "Message with #hash and\n\nmultiple\nlines"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, commit, newMessage).run() as GitCommitEditingOperationResult.Complete

    assertLastMessage(newMessage)
  }
}