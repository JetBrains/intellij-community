// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.reword

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.config.GitConfigUtil
import git4idea.inMemory.rebase.log.GitInMemoryOperationContext
import git4idea.inMemory.rebase.log.RewrittenCommit
import git4idea.inMemory.rebase.log.capturePostRewrites
import git4idea.inMemory.rebase.log.gitInMemoryOperationFixture
import git4idea.inMemory.rebase.log.run
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.assertLastMessage
import git4idea.test.commit
import git4idea.test.file
import git4idea.test.getHash
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.lastMessage
import git4idea.test.message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitInMemoryRewordOperationTest {
  private val fixture = gitSingleRepoContextFixture().gitInMemoryOperationFixture()
  private val context: GitInMemoryOperationContext get() = fixture.get()

  @Test
  fun `test reword last commit`(): Unit = with(context) {
    val parentCommit = file("a").create().addCommit("Add a").details()
    val commit = file("a").append("new content").addCommit("Modify a").details()

    val newMessage = "Reworded commit message"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, commit.id, newMessage).run() as GitCommitEditingOperationResult.Complete
    assertLastMessage(newMessage)

    val newParentCommit = git("rev-parse HEAD~1")
    assertThat(newParentCommit).isEqualTo(parentCommit.id.asString())

    val diffOutput = git("diff ${commit.id.asString()} HEAD")
    assertThat(diffOutput).describedAs("Git diff should show no changes between original and reworded commit").isEmpty()
  }

  @Test
  fun `test reword previous commit`(): Unit = with(context) {
    file("a").create().addCommit("Add a").details()
    val commit = file("a").append("content").addCommit("Old message").details()
    file("b").create().addCommit("Latest commit")

    val newMessage = "New message\n"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, commit.id, newMessage).run() as GitCommitEditingOperationResult.Complete

    assertThat(repo.message("HEAD^1")).isEqualTo(newMessage)
  }

  @Test
  fun `test reword preserves parents and children`(): Unit = with(context) {
    val parentCommit = file("a").create().addCommit("Parent").details()
    val targetCommit = file("b").create().addCommit("Target").details()
    file("c").create().addCommit("Child").details() // childCommit

    val newMessage = "Updated target"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, targetCommit.id, newMessage).run() as GitCommitEditingOperationResult.Complete

    val childParent = git("rev-parse HEAD^")
    val targetParent = git("rev-parse HEAD^^")

    assertThat(targetParent).isEqualTo(parentCommit.id.asString())
    assertThat(childParent).isNotEqualTo(targetCommit.id.asString())
  }

  @Test
  fun `test reword initial commit`(): Unit = with(context) {
    val commit = file("a").create().addCommit("Initial").details()

    val newMessage = "Updated initial"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, commit.id, newMessage).run() as GitCommitEditingOperationResult.Complete

    assertLastMessage(newMessage)
  }

  @Test
  fun `test reword with special characters`(): Unit = with(context) {
    val commit = file("a").create().addCommit("Simple message").details()

    val newMessage = "Message with #hash and\n\nmultiple\nlines"

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, commit.id, newMessage).run() as GitCommitEditingOperationResult.Complete

    assertLastMessage(newMessage)
  }

  @Test
  fun `test reword fires post-rewrite mapping for target and descendants`(): Unit = with(context) {
    file("a").create("content a").addCommit("Add a")
    val targetCommit = file("b").create("content b").addCommit("Add b").details()
    val bOldHash = targetCommit.id.asString()
    file("c").create("content c").add()
    val cOldHash = commit("Add c")

    refresh()
    updateChangeListManager()

    val postRewrites = capturePostRewrites()

    GitInMemoryRewordOperation(objectRepo, targetCommit.id, "Reworded Add b").run() as GitCommitEditingOperationResult.Complete

    val bNewHash = getHash(1)
    val cNewHash = getHash(0)

    assertThat(postRewrites.single().mappings).containsExactly(
      RewrittenCommit(bOldHash, bNewHash),
      RewrittenCommit(cOldHash, cNewHash)
    )
  }

  @Test
  fun `test reword copies notes for rewritten commit and descendants`(): Unit = with(context) {
    file("a").create("content a").addCommit("Add a")
    val targetCommit = file("b").create("content b").addCommit("Add b").details()
    val bOldHash = targetCommit.id.asString()
    file("c").create("content c").add()
    val cOldHash = commit("Add c")

    GitConfigUtil.setValue(project, repo.root, "notes.rewriteRef", "refs/notes/commits")
    git("notes add -m note-b $bOldHash")
    git("notes add -m note-c $cOldHash")

    refresh()
    updateChangeListManager()

    GitInMemoryRewordOperation(objectRepo, targetCommit.id, "Reworded Add b").run() as GitCommitEditingOperationResult.Complete

    val bNewHash = getHash(1)
    val cNewHash = getHash(0)

    assertThat(git("notes show $bNewHash")).isEqualTo("note-b")
    assertThat(git("notes show $cNewHash")).isEqualTo("note-c")
  }

  @Test
  fun `test compare messages should add newline at the end`(): Unit = with(context) {
    val (inMemMessage, nativeMessage) = rewordInMemoryAndNativeAndGetMessages("Implement feature")

    assertThat(inMemMessage).isEqualTo(nativeMessage)
  }

  @Test
  fun `test compare messages with default cleanup and default comment char`(): Unit = with(context) {
    val (inMemMessage, nativeMessage) = rewordInMemoryAndNativeAndGetMessages(COMPLEX_MESSAGE)

    assertThat(inMemMessage).isEqualTo(nativeMessage)
  }

  @Test
  fun `test compare messages with verbatim cleanup`(): Unit = with(context) {
    GitConfigUtil.setValue(project, repo.root, GitConfigUtil.COMMIT_CLEANUP, "verbatim")

    val (inMemMessage, nativeMessage) = rewordInMemoryAndNativeAndGetMessages(COMPLEX_MESSAGE)

    assertThat(inMemMessage).isEqualTo(nativeMessage)
  }

  @Test
  fun `test compare messages with strip cleanup and custom comment char`(): Unit = with(context) {
    GitConfigUtil.setValue(project, repo.root, GitConfigUtil.COMMIT_CLEANUP, "strip")
    GitConfigUtil.setValue(project, repo.root, GitConfigUtil.CORE_COMMENT_CHAR, ";")

    val (inMemMessage, nativeMessage) = rewordInMemoryAndNativeAndGetMessages(COMPLEX_MESSAGE)

    assertThat(inMemMessage).isEqualTo(nativeMessage)
  }

  @Test
  fun `test compare messages with whitespace cleanup`(): Unit = with(context) {
    GitConfigUtil.setValue(project, repo.root, GitConfigUtil.COMMIT_CLEANUP, "whitespace")

    val (inMemMessage, nativeMessage) = rewordInMemoryAndNativeAndGetMessages(COMPLEX_MESSAGE)

    assertThat(inMemMessage).isEqualTo(nativeMessage)
  }

  // IJPL-212686
  private fun GitInMemoryOperationContext.rewordInMemoryAndNativeAndGetMessages(message: String): Pair<String, String> {
    file("a").create().addCommit("Add a")
    val commit = file("a").append("new content").addCommit("Modify a").details()
    refresh()
    updateChangeListManager()

    val inBranch = "in-memory"
    val nativeBranch = "native"

    git("checkout -B $inBranch")
    GitInMemoryRewordOperation(objectRepo, commit.id, message).run() as GitCommitEditingOperationResult.Complete
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
