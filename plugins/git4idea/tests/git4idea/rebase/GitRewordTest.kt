/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.rebase

import com.intellij.openapi.util.text.StringUtil
import git4idea.config.GitVersionSpecialty
import git4idea.test.*
import org.junit.Assume.assumeTrue

class GitRewordTest : GitSingleRepoTest() {

  fun `test reword latest commit`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    val newMessage = "Correct message"
    GitRewordOperation(repo, commit, newMessage).execute()

    assertLastMessage(newMessage, "Message reworded incorrectly")
  }

  fun `test reword via amend doesn't touch the local changes`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()
    file("b").create("b").add()

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

    val newMessage = "Correct message"
    GitRewordOperation(repo, commit, newMessage).execute()

    assertMessage(newMessage, repo.message("HEAD^"), "Message reworded incorrectly")
  }

  fun `test undo reword`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    val operation = GitRewordOperation(repo, commit, "Correct message")
    operation.execute()
    operation.undo()

    assertLastMessage("Wrong message", "Message reworded incorrectly")
  }

  fun `test undo is not possible if HEAD moved`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    val operation = GitRewordOperation(repo, commit, "Correct message")
    operation.execute()

    file("b").create().addCommit("New commit")

    operation.undo()

    repo.assertLatestHistory(
      "New commit",
      "Correct message"
    )
    assertErrorNotification("Can't Undo Reword", "Repository has already been changed")
  }

  fun `test undo is not possible if commit was pushed`() {
    git("remote add origin http://example.git")
    val file = file("a").create("initial")
    file.append("First commit\n").addCommit("First commit")
    val commit = file.append("To reword\n").addCommit("Wrong message").details()
    file.append("Third commit").addCommit("Third commit")

    val operation = GitRewordOperation(repo, commit, "Correct message")
    operation.execute()

    git("update-ref refs/remotes/origin/master HEAD")

    operation.undo()

    repo.assertLatestHistory(
      "Third commit",
      "Correct message",
      "First commit"
    )
    assertErrorNotification("Can't Undo Reword", "Commit has already been pushed to origin/master")
  }

  // IDEA-175002
  fun `test reword with trailing spaces`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    val newMessage = "Subject with trailing spaces  \n\nBody \nwith \nspaces."
    GitRewordOperation(repo, commit, newMessage).execute()

    assertLastMessage(newMessage)
  }

  // IDEA-175443
  fun `test reword with hash symbol`() {
    assumeTrue("Not testing: not possible to fix in Git prior to 1.8.2: ${vcs.version}",
               GitVersionSpecialty.KNOWS_CORE_COMMENT_CHAR.existsIn(vcs.version)) // IDEA-182044

    val commit = file("a").create("initial").addCommit("Wrong message").details()

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