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
package git4idea.cherrypick

import com.intellij.openapi.vcs.changes.LocalChangeList
import git4idea.test.*

class GitCherryPickNoAutoCommitTest : GitCherryPickTest() {

  override fun setUp() {
    super.setUp()
    settings.isAutoCommitOnCherryPick = false
  }

  fun `test commit dialog shown on cherry pick`() {
    branch("feature")
    val commit = file("f.txt").create().addCommit("fix #1").hash()
    checkout("feature")
    vcsHelper.onCommit { true }

    cherryPick(commit)

    assertTrue("Commit dialog was not shown", vcsHelper.commitDialogWasShown())
  }

  fun `test cherry pick and commit`() {
    branch("feature")
    val commit = file("f.txt").create().addCommit("fix #1").hash()
    checkout("feature")
    vcsHelper.onCommit { msg ->
      git("commit -am '$msg'")
      true
    }

    cherryPick(commit)

    assertLastMessage("fix #1\n\n(cherry picked from commit ${shortHash(commit)})")
    assertSuccessfulNotification("Cherry-pick successful", "${shortHash(commit)} fix #1")
    changeListManager.assertOnlyDefaultChangelist()
  }

  fun `test cherry pick and cancel commit`() {
    branch("feature")
    val commit = file("f.txt").create().addCommit("fix #1").hash()
    checkout("feature")
    vcsHelper.onCommit { false }

    cherryPick(commit)

    val list = changeListManager.assertChangeListExists("fix #1\n\n(cherry picked from commit ${shortHash(commit)})")
    assertNoNotification()
    updateChangeListManager()
    assertChanges(list, "f.txt")
  }

  fun `test cherry pick 2 commits`() {
    branch("feature")
    val commits = (1..2).map {
      file("$it.txt").create().addCommit("fix #$it").hash()
    }
    checkout("feature")
    vcsHelper.onCommit { msg ->
      git("commit -am '$msg'")
      true
    }

    cherryPick(commits)

    assertLogMessages("""
      fix #2

      (cherry picked from commit ${shortHash(commits[1])})""","""
      fix #1

      (cherry picked from commit ${shortHash(commits[0])})""")
    changeListManager.assertOnlyDefaultChangelist()
  }

  fun `test cherry pick 2 commits, but cancel second`() {
    branch("feature")
    val (commit1, commit2) = (1..2).map {
      file("$it.txt").create().addCommit("fix #$it").hash()
    }
    checkout("feature")

    vcsHelper.onCommit { msg ->
      if (msg.startsWith("fix #1")) {
        git("commit -am '$msg'")
        true
      } else false
    }

    cherryPick(commit1, commit2)

    assertLastMessage("fix #1\n\n(cherry picked from commit ${shortHash(commit1)})")
    assertWarningNotification("Cherry-pick cancelled", """
      ${shortHash(commit2)} fix #2
      However cherry-pick succeeded for the following commit:
      ${shortHash(commit1)} fix #1""".trimIndent())
    val list = changeListManager.assertChangeListExists("fix #2\n\n(cherry picked from commit ${shortHash(commit2)})")
    assertChanges(list, "2.txt")
  }

  fun `test dirty tree conflicting with commit`() {
    `check dirty tree conflicting with commit`()
  }

  fun `test untracked file conflicting with commit`() {
    `check untracked file conflicting with commit`()
  }

  fun `test conflict with cherry-picked commit should show merge dialog`() {
    `check conflict with cherry-picked commit should show merge dialog`()
  }

  fun `test resolve conflicts and commit`() {
    `check resolve conflicts and commit`()
  }

  fun `test cherry-pick changes in renamed file`() {
    val initialName = "a.txt"
    file(initialName).create("This file has name $initialName").addCommit("Create $initialName")

    val renamed = "renamed.txt"
    repo.checkoutNew("feature")
    git("mv $initialName $renamed")
    commit("Rename $initialName to $renamed")
    val commit = file(renamed).write("This file has name $renamed").addCommit("Modify the file").hash()
    checkout("master")

    vcsHelper.onCommit { msg ->
      git("commit -am '$msg'")
      true
    }

    cherryPick(commit)

    assertSuccessfulNotification("Cherry-pick successful", "${shortHash(commit)} Modify the file")
    assertLastMessage("Modify the file\n\n(cherry picked from commit ${shortHash(commit)})")
    repo.assertCommitted {
      modified(initialName)
    }
    changeListManager.assertOnlyDefaultChangelist()
  }

  private fun assertChanges(list: LocalChangeList, file: String) {
    assertEquals("Changelist size is incorrect", 1, list.changes.size)
    assertEquals("Incorrect changed file", file, list.changes.first().afterRevision!!.file.name)
  }
}