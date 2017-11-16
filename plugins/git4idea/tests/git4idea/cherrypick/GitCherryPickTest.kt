/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.cherrypick

import com.intellij.vcs.log.impl.HashImpl
import git4idea.test.*

abstract class GitCherryPickTest : GitSingleRepoTest() {

  protected fun `check dirty tree conflicting with commit`() {
    val file = file("c.txt")
    file.create("initial\n").addCommit("initial")
    branch("feature")
    val commit = file.append("master\n").addCommit("fix #1").hash()
    checkout("feature")
    file.append("local\n")

    cherryPick(commit)

    assertErrorNotification("Cherry-pick Failed", """
      ${shortHash(commit)} fix #1
      Your local changes would be overwritten by cherry-pick.
      Commit your changes or stash them to proceed.""")
  }

  protected fun `check untracked file conflicting with commit`() {
    branch("feature")
    val file = file("untracked.txt")
    val commit = file.create("master\n").addCommit("fix #1").hash()
    checkout("feature")
    file.create("untracked\n")

    cherryPick(commit)

    assertErrorNotification("Untracked Files Prevent Cherry-pick", """
      Move or commit them before cherry-pick""")
  }

  protected fun `check conflict with cherry-picked commit should show merge dialog`() {
    val initial = tac("c.txt", "base\n")
    val commit = repo.appendAndCommit("c.txt", "master")
    repo.checkoutNew("feature", initial)
    repo.appendAndCommit("c.txt", "feature")

    `do nothing on merge`()

    cherryPick(commit)

    `assert merge dialog was shown`()
  }

  protected fun `check resolve conflicts and commit`() {
    val commit = repo.prepareConflict()
    `mark as resolved on merge`()
    vcsHelper.onCommit { msg ->
      git("commit -am '$msg'")
      true
    }

    cherryPick(commit)

    `assert commit dialog was shown`()
    assertLastMessage("""
      on_master

      (cherry picked from commit ${shortHash(commit)})""".trimIndent())
    repo.assertCommitted {
      modified("c.txt")
    }
    assertSuccessfulNotification("Cherry-pick successful",
                                 "${shortHash(commit)} on_master")
    changeListManager.assertOnlyDefaultChangelist()
  }

  protected fun cherryPick(hashes: List<String>) {
    updateChangeListManager()
    val details = readDetails(hashes)
    GitCherryPicker(project, git).cherryPick(details)
  }

  protected fun cherryPick(vararg hashes: String) {
    cherryPick(hashes.asList())
  }

  protected fun shortHash(hash: String) = HashImpl.build(hash).toShortString()
}
