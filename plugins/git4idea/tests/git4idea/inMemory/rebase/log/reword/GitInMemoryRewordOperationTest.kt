// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.reword

import git4idea.config.GitConfigUtil
import git4idea.inMemory.rebase.log.GitInMemoryOperationTest
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.assertLastMessage
import git4idea.test.lastMessage
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

    val newMessage = "New message\n"

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

  fun `test compare messages should add newline at the end`() {
    val (inMemMessage, nativeMessage) = rewordInMemoryAndNativeAndGetMessages("Implement feature")

    assertEquals(nativeMessage, inMemMessage)
  }

  fun `test compare messages with default cleanup and default comment char`() {
    val (inMemMessage, nativeMessage) = rewordInMemoryAndNativeAndGetMessages(COMPLEX_MESSAGE)

    assertEquals(nativeMessage, inMemMessage)
  }

  fun `test compare messages with verbatim cleanup`() {
    GitConfigUtil.setValue(project, repo.root, GitConfigUtil.COMMIT_CLEANUP, "verbatim")

    val (inMemMessage, nativeMessage) = rewordInMemoryAndNativeAndGetMessages(COMPLEX_MESSAGE)

    assertEquals(nativeMessage, inMemMessage)
  }

  fun `test compare messages with strip cleanup and custom comment char`() {
    GitConfigUtil.setValue(project, repo.root, GitConfigUtil.COMMIT_CLEANUP, "strip")
    GitConfigUtil.setValue(project, repo.root, GitConfigUtil.CORE_COMMENT_CHAR, ";")

    val (inMemMessage, nativeMessage) = rewordInMemoryAndNativeAndGetMessages(COMPLEX_MESSAGE)

    assertEquals(nativeMessage, inMemMessage)
  }

  // IJPL-212686
  private fun rewordInMemoryAndNativeAndGetMessages(message: String): Pair<String, String> {
    file("a").create().addCommit("Add a")
    val commit = file("a").append("new content").addCommit("Modify a").details()
    refresh()
    updateChangeListManager()

    val inBranch = "in-memory"
    val nativeBranch = "native"

    git("checkout -B $inBranch")
    GitInMemoryRewordOperation(objectRepo, commit, message).run() as GitCommitEditingOperationResult.Complete
    val inMemMessage = lastMessage()

    git("checkout master")

    git("checkout -B $nativeBranch")
    git("commit --amend -m '$message'")
    val nativeMessage = lastMessage()

    return inMemMessage to nativeMessage
  }

  private val COMPLEX_MESSAGE = """
      
      # This is a comment with default char
      
      Subject with trailing spaces    
         
      # Another default comment
      ; Comment with different char
          
      Body line with trailing spaces       
      
      
      
      Another body line
      
      ; comment at the end
      
    """.trimIndent()
}