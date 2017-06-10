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

import git4idea.test.GitSingleRepoTest
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
}