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
import git4idea.GitUtil
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertLatestHistory
import git4idea.test.file
import git4idea.test.git

class GitRewordTest : GitSingleRepoTest() {

  fun `test reword latest commit`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    val newMessage = "Correct message"
    GitRewordOperation(myRepo, commit, newMessage).execute()

    assertEquals("Message reworded incorrectly", newMessage, git("log HEAD --no-walk --pretty=%B"))
  }

  fun `test reword previous commit`() {
    val file = file("a").create("initial")
    val commit = file.addCommit("Wrong message").details()
    file.append("b").addCommit("Second message")

    val newMessage = "Correct message"
    GitRewordOperation(myRepo, commit, newMessage).execute()

    assertEquals("Message reworded incorrectly", newMessage, git("log HEAD^ --no-walk --pretty=%B"))
  }

  fun `test undo reword`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    val operation = GitRewordOperation(myRepo, commit, "Correct message")
    operation.execute()
    operation.undo()

    assertEquals("Message reworded incorrectly", "Wrong message", git("log HEAD --no-walk --pretty=%B"))
  }

  fun `test undo is not possible if HEAD moved`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    val operation = GitRewordOperation(myRepo, commit, "Correct message")
    operation.execute()

    file("b").create().addCommit("New commit")

    operation.undo()

    myRepo.assertLatestHistory(
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

    val operation = GitRewordOperation(myRepo, commit, "Correct message")
    operation.execute()

    git("update-ref refs/remotes/origin/master HEAD")

    operation.undo()

    myRepo.assertLatestHistory(
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
    GitRewordOperation(myRepo, commit, newMessage).execute()

    val actualMessage = git("log HEAD --no-walk --pretty=%B")
    assertTrue("Message reworded incorrectly. Expected:\n[$newMessage] Actual:\n[$actualMessage]",
               StringUtil.equalsIgnoreWhitespaces(newMessage, actualMessage))
  }

  // IDEA-175443
  fun `test reword with hash symbol`() {
    val commit = file("a").create("initial").addCommit("Wrong message").details()

    val newMessage = """
      Subject

      #body starting with a hash
      """.trimIndent()
    GitRewordOperation(myRepo, commit, newMessage).execute()

    val actualMessage = git("log HEAD --no-walk --pretty=%B")
    assertTrue("Message reworded incorrectly. Expected:\n[$newMessage] Actual:\n[$actualMessage]",
               StringUtil.equalsIgnoreWhitespaces(newMessage, actualMessage))
  }
}