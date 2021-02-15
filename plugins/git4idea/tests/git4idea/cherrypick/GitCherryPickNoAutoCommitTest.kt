// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.cherrypick

import com.intellij.openapi.vcs.changes.LocalChangeList
import git4idea.i18n.GitBundle
import git4idea.test.*

class GitCherryPickNoAutoCommitTest : GitCherryPickTest() {

  override fun setUp() {
    super.setUp()
    appSettings.isAutoCommitOnCherryPick = false
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

    assertLastMessage("fix #1")
    assertSuccessfulNotification("Cherry-pick successful", "${shortHash(commit)} fix #1")
    changeListManager.assertNoChanges()
    changeListManager.waitScheduledChangelistDeletions()
    changeListManager.assertOnlyDefaultChangelist()
  }

  fun `test cherry-pick from protected branch should add suffix by default`() {
    branch("feature")
    val commit = file("f.txt").create().addCommit("fix #1").hash()
    git("update-ref refs/remotes/origin/master HEAD")
    checkout("feature")
    vcsHelper.onCommit { msg ->
      git("commit -am '$msg'")
      true
    }

    cherryPick(commit)

    assertLastMessage("fix #1\n\n(cherry picked from commit $commit)")
    assertSuccessfulNotification("Cherry-pick successful", "${shortHash(commit)} fix #1")
    changeListManager.assertNoChanges()
    changeListManager.waitScheduledChangelistDeletions()
    changeListManager.assertOnlyDefaultChangelist()
  }

  fun `test cherry pick and cancel commit`() {
    branch("feature")
    val commit = file("f.txt").create().addCommit("fix #1").hash()
    checkout("feature")
    vcsHelper.onCommit { false }

    cherryPick(commit)

    val list = changeListManager.assertChangeListExists("fix #1")
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

    assertLogMessages("fix #2", "fix #1")
    changeListManager.assertNoChanges()
    changeListManager.waitScheduledChangelistDeletions()
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

    assertLastMessage("fix #1")
    assertWarningNotification(GitBundle.message("apply.changes.operation.canceled", "Cherry-pick"), """
      ${shortHash(commit2)} fix #2
      """ + GitBundle.message("apply.changes.operation.successful.for.commits", "cherry-pick", 1) + """
      ${shortHash(commit1)} fix #1""")
    val list = changeListManager.assertChangeListExists("fix #2")
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
    assertLastMessage("Modify the file")
    repo.assertCommitted {
      modified(initialName)
    }
    changeListManager.waitScheduledChangelistDeletions()
    changeListManager.assertOnlyDefaultChangelist()
  }

  private fun assertChanges(list: LocalChangeList, file: String) {
    assertEquals("Changelist size is incorrect", 1, list.changes.size)
    assertEquals("Incorrect changed file", file, list.changes.first().afterRevision!!.file.name)
  }
}