// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.config.GitVersionSpecialty
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoPossibility.Possible
import git4idea.test.GitSingleRepoContext
import git4idea.test.assertCommitted
import git4idea.test.assertLastMessage
import git4idea.test.assertLatestHistory
import git4idea.test.assertMessage
import git4idea.test.assertStagedChanges
import git4idea.test.file
import git4idea.test.findGitLogProvider
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.message
import git4idea.test.runUnderProgress
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class GitRewordTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test reword latest commit`(): Unit = with(context) {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val newMessage = "Correct message"
    runUnderProgress { GitRewordOperation(repo, commit, newMessage).execute() }

    assertLastMessage(newMessage, "Message reworded incorrectly")
  }

  @Test
  fun `test reword initial commit via rebase`(): Unit = with(context) {
    val initialHash = git("log --pretty=%H").trim()
    file("a").create("initial").addCommit("Wrong message")

    val initialCommit = VcsLogUtil.getDetails(findGitLogProvider(repo.project), repo.root, listOf(initialHash)).first()

    refresh()
    updateChangeListManager()

    val newMessage = "Correct message"
    runUnderProgress { GitRewordOperation(repo, initialCommit, newMessage).execute() }

    assertMessage(newMessage, repo.message("HEAD^"), "Message reworded incorrectly")
  }

  @Test
  fun `test reword initial commit via amend`(): Unit = with(context) {
    val initialHash = git("log --pretty=%H").trim()
    val initialCommit = VcsLogUtil.getDetails(findGitLogProvider(repo.project), repo.root, listOf(initialHash)).first()

    refresh()
    updateChangeListManager()

    val newMessage = "Correct message"
    runUnderProgress { GitRewordOperation(repo, initialCommit, newMessage).execute() }

    assertLastMessage(newMessage, "Message reworded incorrectly")
  }

  @Test
  fun `test reword via amend doesn't touch the local changes`(): Unit = with(context) {
    val commit = file("a").create("initial").addCommit("Wrong message").details()
    file("b").create("b").add()

    refresh()
    updateChangeListManager()

    val newMessage = "Correct message"
    runUnderProgress { GitRewordOperation(repo, commit, newMessage).execute() }

    assertLastMessage(newMessage, "Message reworded incorrectly")
    repo.assertStagedChanges {
      added("b")
    }
    repo.assertCommitted {
      added("a")
    }
  }

  @Test
  fun `test reword previous commit`(): Unit = with(context) {
    val file = file("a").create("initial")
    val commit = file.addCommit("Wrong message").details()
    file.append("b").addCommit("Second message")

    refresh()
    updateChangeListManager()

    val newMessage = "Correct message"
    runUnderProgress { GitRewordOperation(repo, commit, newMessage).execute() }

    assertMessage(newMessage, repo.message("HEAD^"), "Message reworded incorrectly")
  }

  @Test
  fun `test undo reword`(): Unit = with(context) {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val operation = GitRewordOperation(repo, commit, "Correct message")
    val result = runUnderProgress { operation.execute() } as Complete

    assertThat(runBlocking { result.checkUndoPossibility() }).isInstanceOf(Possible::class.java)
    result.undo()

    assertLastMessage("Wrong message", "Message reworded incorrectly")
  }

  @Test
  fun `test undo is not possible if HEAD moved`(): Unit = with(context) {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val operation = GitRewordOperation(repo, commit, "Correct message")
    val result = runUnderProgress { operation.execute() } as Complete

    file("b").create().addCommit("New commit")

    assertThat(runBlocking { result.checkUndoPossibility() })
      .isInstanceOf(Complete.UndoPossibility.Impossible.HeadMoved::class.java)

    repo.assertLatestHistory(
      "New commit",
      "Correct message"
    )
  }

  @Test
  fun `test undo is not possible if commit was pushed`(): Unit = with(context) {
    git("remote add origin http://example.git")
    val file = file("a").create("initial")
    file.append("First commit\n").addCommit("First commit")
    val commit = file.append("To reword\n").addCommit("Wrong message").details()
    file.append("Third commit").addCommit("Third commit")

    refresh()
    updateChangeListManager()

    val operation = GitRewordOperation(repo, commit, "Correct message")
    val result = runUnderProgress { operation.execute() } as Complete

    git("update-ref refs/remotes/origin/master HEAD")

    val undoPossibility = runBlocking { result.checkUndoPossibility() }
    assertThat(undoPossibility).isInstanceOf(Complete.UndoPossibility.Impossible.PushedToProtectedBranch::class.java)
    assertThat((undoPossibility as Complete.UndoPossibility.Impossible.PushedToProtectedBranch).branch).isEqualTo("origin/master")

    repo.assertLatestHistory(
      "Third commit",
      "Correct message",
      "First commit"
    )
  }

  // IDEA-175002
  @Test
  fun `test reword with trailing spaces`(): Unit = with(context) {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val newMessage = "Subject with trailing spaces  \n\nBody \nwith \nspaces."
    runUnderProgress { GitRewordOperation(repo, commit, newMessage).execute() }

    assertLastMessage(newMessage)
  }

  // IDEA-175443
  @Test
  fun `test reword with hash symbol`(): Unit = with(context) {
    // IDEA-182044
    assumeTrue(GitVersionSpecialty.KNOWS_CORE_COMMENT_CHAR.existsIn(vcs.version)) {
      "Not testing: not possible to fix in Git prior to 1.8.2: ${vcs.version}"
    }

    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val newMessage = """
      Subject

      #body starting with a hash
      """.trimIndent()
    runUnderProgress { GitRewordOperation(repo, commit, newMessage).execute() }

    val actualMessage = git("log HEAD --no-walk --pretty=%B")
    assertThat(StringUtil.equalsIgnoreWhitespaces(newMessage, actualMessage))
      .describedAs("Message reworded incorrectly. Expected:\n[$newMessage] Actual:\n[$actualMessage]")
      .isTrue()
  }

  // IDEA-254399
  @Test
  fun `test reword via rebase with spaces at the beginning`(): Unit = with(context) {
    val commit = file("a").create("initial").addCommit("  \t    Wrong message").details()
    file("b").create().addCommit("One more commit")

    refresh()
    updateChangeListManager()

    val newMessage = "Correct message"
    runUnderProgress { GitRewordOperation(repo, commit, newMessage).execute() }

    repo.assertLatestHistory(
      "One more commit",
      newMessage
    )
  }
}
