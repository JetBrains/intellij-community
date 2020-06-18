// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.util.text.StringUtil
import git4idea.config.GitVersionSpecialty
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult.Complete
import git4idea.rebase.log.GitMultipleCommitEditingOperationResult.Complete.UndoPossibility.Possible
import git4idea.test.*
import org.junit.Assume.assumeTrue

class GitRewordTest : GitSingleRepoTest() {

  fun `test reword latest commit`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val newMessage = "Correct message"
    GitRewordOperation(repo, commit, newMessage).execute()

    assertLastMessage(newMessage, "Message reworded incorrectly")
  }

  fun `test reword via amend doesn't touch the local changes`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()
    file("b").create("b").add()

    refresh()
    updateChangeListManager()

    val newMessage = "Correct message"
    GitRewordOperation(repo, commit, newMessage).execute()

    assertLastMessage(newMessage, "Message reworded incorrectly")
    repo.assertStagedChanges {
      added("b")
    }
    repo.assertCommitted {
      added("a")
    }
  }

  fun `test reword previous commit`() {
    val file = file("a").create("initial")
    val commit = file.addCommit("Wrong message").details()
    file.append("b").addCommit("Second message")

    refresh()
    updateChangeListManager()

    val newMessage = "Correct message"
    GitRewordOperation(repo, commit, newMessage).execute()

    assertMessage(newMessage, repo.message("HEAD^"), "Message reworded incorrectly")
  }

  fun `test undo reword`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val operation = GitRewordOperation(repo, commit, "Correct message")
    val result = operation.execute() as Complete

    assertTrue(result.checkUndoPossibility() is Possible)
    operation.undo(result)

    assertLastMessage("Wrong message", "Message reworded incorrectly")
  }

  fun `test undo is not possible if HEAD moved`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val operation = GitRewordOperation(repo, commit, "Correct message")
    val result = operation.execute() as Complete

    file("b").create().addCommit("New commit")

    assertTrue(result.checkUndoPossibility() is Complete.UndoPossibility.HeadMoved)
    operation.undo(result)

    repo.assertLatestHistory(
      "New commit",
      "Correct message"
    )
    assertErrorNotification("Can't Undo Commit Message Edit", "Repository has already been changed")
  }

  fun `test undo is not possible if commit was pushed`() {
    git("remote add origin http://example.git")
    val file = file("a").create("initial")
    file.append("First commit\n").addCommit("First commit")
    val commit = file.append("To reword\n").addCommit("Wrong message").details()
    file.append("Third commit").addCommit("Third commit")

    refresh()
    updateChangeListManager()

    val operation = GitRewordOperation(repo, commit, "Correct message")
    val result = operation.execute() as Complete

    git("update-ref refs/remotes/origin/master HEAD")

    val undoPossibility = result.checkUndoPossibility()
    assertTrue(undoPossibility is Complete.UndoPossibility.PushedToProtectedBranch && undoPossibility.branch == "origin/master")
    operation.undo(result)

    repo.assertLatestHistory(
      "Third commit",
      "Correct message",
      "First commit"
    )
    assertErrorNotification("Can't Undo Commit Message Edit", "Commit has already been pushed to origin/master")
  }

  // IDEA-175002
  fun `test reword with trailing spaces`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val newMessage = "Subject with trailing spaces  \n\nBody \nwith \nspaces."
    GitRewordOperation(repo, commit, newMessage).execute()

    assertLastMessage(newMessage)
  }

  // IDEA-175443
  fun `test reword with hash symbol`() {
    assumeTrue("Not testing: not possible to fix in Git prior to 1.8.2: ${vcs.version}",
               GitVersionSpecialty.KNOWS_CORE_COMMENT_CHAR.existsIn(vcs.version)) // IDEA-182044

    val commit = file("a").create("initial").addCommit("Wrong message").details()

    refresh()
    updateChangeListManager()

    val newMessage = """
      Subject

      #body starting with a hash
      """.trimIndent()
    GitRewordOperation(repo, commit, newMessage).execute()

    val actualMessage = git("log HEAD --no-walk --pretty=%B")
    assertTrue("Message reworded incorrectly. Expected:\n[$newMessage] Actual:\n[$actualMessage]",
               StringUtil.equalsIgnoreWhitespaces(newMessage, actualMessage))
  }
}