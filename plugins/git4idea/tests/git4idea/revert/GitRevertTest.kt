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
package git4idea.revert

import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.GitContentRevision.createRevision
import git4idea.GitRevisionNumber
import git4idea.history.GitLogUtil
import git4idea.test.*
import java.nio.charset.Charset

/**
 * Revert works in two modes: revert & commit immediately, and show the commit dialog.
 *
 * For the sake of simplicity, this test covers only immediate revert, since the complicated logic which
 * updates the ChangeListManager and shows the commit dialog is tested in [git4idea.cherrypick.GitCherryPickNoAutoCommitTest],
 * except the case of reverting with conflicts when the commit dialog is shown anyway.
 */
class GitRevertTest : GitSingleRepoTest() {

  fun `test simple revert`() {
    val file = file("r.txt")
    val commit = file.create("initial\n").addCommit("Created r.txt").details()

    revertAutoCommit(commit)

    assertSuccessfulNotification("Revert successful", "${commit.id.toShortString()} ${commit.subject}")
    assertFalse("File should have been deleted", file.exists())
    repo.assertLatestSubjects("Revert \"${commit.subject}\"")
  }

  fun `test local changes would be overwritten by revert`() {
    val file = file("r.txt").create("initial\n")
    val commit = file.addCommit("Created r.txt").details()
    file.append("second\n")

    revertAutoCommit(commit)

    assertErrorNotification("Revert Failed", """
      ${commit.id.toShortString()} ${commit.subject}
      Your local changes would be overwritten by revert. Commit your changes or stash them to proceed.""")
    assertEquals("File content shouldn't change", "initial\nsecond\n", file.read())
    assertEquals("No new commits should have been created", commit.id.asString(), last())
  }

  fun `test empty revert`() {
    val file = file("r.txt").create("initial\n").addCommit("Created r.txt")
    val commit = file.append("second\n").addCommit("Appended second").details()
    val lastCommit = file.write("initial\n").addCommit("Rolled back second").hash()

    revertAutoCommit(commit)

    assertWarningNotification("Nothing to revert", "All changes from ${commit.id.toShortString()} have already been reverted")
    assertEquals("No new commits should have been created", lastCommit, last())
  }

  fun `test revert two commits`() {
    val commit1 = file("a.txt").create().addCommit("Create a").details()
    val commit2 = file("b.txt").create().addCommit("Create b").details()

    revertAutoCommit(commit2, commit1)

    repo.assertLatestSubjects(
      "Revert \"${commit1.subject}\"",
      "Revert \"${commit2.subject}\""
    )
  }

  fun `test one commit reverted, second fails with error`() {
    val e = file("e.txt")
    val commit1 = e.create("e\n").addCommit("Created e").details()
    e.append("local changes")
    val rFile = file("r.txt")
    val commit2 = rFile.create("initial\n").addCommit("Created r.txt").details()

    revertAutoCommit(commit2, commit1)

    assertErrorNotification("Revert Failed","""
      ${commit1.id.toShortString()} ${commit1.subject} Your local changes would be overwritten by revert.
      Commit your changes or stash them to proceed.
      However revert succeeded for the following commit:
      ${commit2.id.toShortString()} ${commit2.subject}""")
    assertFalse("File should have been deleted", rFile.exists())
    repo.assertLatestSubjects("Revert \"${commit2.subject}\"")
  }

  fun `test two commits reverted, one more was skipped because empty`() {
    val goodFile = file("good.txt")
    val commit1 = goodFile.create("initial\n").addCommit("Created good").details()
    val file = file("r.txt").create("initial\n").addCommit("Created r.txt")
    val commit2 = file.append("second\n").addCommit("Appended second").details()
    file.write("initial\n").addCommit("Rolled back second")
    val commit3 = goodFile.append("more good\n").addCommit("More good").details()

    revertAutoCommit(commit3, commit2, commit1)

    assertSuccessfulNotification("Reverted 2 commits from 3", """
      ${commit3.id.toShortString()} ${commit3.subject}
      ${commit1.id.toShortString()} ${commit1.subject}
      ${commit2.id.toShortString()} was skipped, because all changes have already been reverted.
    """)

    repo.assertLatestSubjects(
      "Revert \"${commit1.subject}\"",
      "Revert \"${commit3.subject}\""
    )
  }

  fun `test revert with conflicts shows merge dialog`() {
    val commitToRevert = prepareRevertConflict("c.txt")

    `do nothing on merge`()

    revertAutoCommit(commitToRevert)
    `assert merge dialog was shown`()
  }

  fun `test revert with conflicts shows commit dialog after resolving conflicts`() {
    val commitToRevert = prepareRevertConflict("c.txt")
    `mark as resolved on merge`()
    vcsHelper.onCommit { true }

    revertAutoCommit(commitToRevert)

    `assert commit dialog was shown`()
  }

  fun `test revert with conflicts shows notification if conflicts not resolved`() {
    val commitToRevert = prepareRevertConflict("c.txt")
    `do nothing on merge`()

    revertAutoCommit(commitToRevert)

    assertWarningNotification("Reverted with conflicts", """
      ${commitToRevert.id.toShortString()} ${commitToRevert.subject}
      Unresolved conflicts remain in the working tree. <a href='resolve'>Resolve them.<a/>""")
  }

  fun `test revert with conflicts resolve in chain`() {
    val goodFile = file("good.txt")
    val commit1 = goodFile.create("initial\n").addCommit("Created good").details()
    val conflictingCommit = prepareRevertConflict("c.txt")
    val commit3 = goodFile.append("more good\n").addCommit("More good").details()

    `mark as resolved on merge`()
    vcsHelper.onCommit { msg ->
      git("commit -am '$msg'")
      true
    }

    revertAutoCommit(commit3, conflictingCommit, commit1)

    assertSuccessfulNotification("Revert successful", listOf(commit3, conflictingCommit, commit1).joinToString("<br/>")
      { "${it.id.toShortString()} ${it.subject}"})
    repo.assertLatestSubjects(
      "Revert \"${commit1.subject}\"",
      "Revert \"${conflictingCommit.subject}\"",
      "Revert \"${commit3.subject}\""
    )
  }

  fun `test reverting added file`() {
    val file = file("r.txt")
    val commit = file.create("initial\n").addCommit("Created r.txt").details()

    vcsHelper.onCommit { msg ->
      git("commit -am '$msg'")
      true
    }

    `revert without auto-commit`(commit)

    assertSuccessfulNotification("Revert successful", "${commit.id.toShortString()} ${commit.subject}")
    assertFalse("File should have been deleted", file.exists())

    val changes = VcsLogUtil.getDetails(logProvider, projectRoot, listOf("HEAD")).first().changes
    val beforeRevision = createRevision(getFilePath(file.file), GitRevisionNumber.HEAD, project, Charset.defaultCharset())
    assertOrderedEquals("Incorrect reverting commit", changes, Change(beforeRevision, null))
  }

  fun `test default commit message proposed on revert`() {
    val file = file("r.txt")
    file.create("initial\n").addCommit("Created r.txt")
    val commit = file.append("second\n").addCommit("Append something").details()

    var actualMessage = ""
    vcsHelper.onCommit { msg ->
      actualMessage = msg
      true
    }

    `revert without auto-commit`(commit)

    `assert commit dialog was shown`()
    assertEquals("Commit message is incorrect", commitMessageForRevert(commit), actualMessage)
  }

  fun `test reverting commit doesn't preserve authorship of the original commit`() {
    file("a.txt").create("initial\n").add()
    git("commit --author='Original Author <original@example.com>' -m original_commit")
    val commit = GitLogUtil.collectFullDetails(project, projectRoot, "-1").first()

    vcsHelper.onCommit { false }

    `revert without auto-commit`(commit)

    val comment = commitMessageForRevert(commit)
    val list = changeListManager.assertChangeListExists(comment)
    val data = list.data
    assertNull("There should be no author information in the changelist: $data", data)
  }

  fun `test revert commit which was renamed later`() {
    val initialName = "a.txt"
    file(initialName).create("initial\n").addCommit("create $initialName")
    val commit = file(initialName).append("more\n").addCommit("add content").details()
    val renamed = "renamed.txt"
    git("mv $initialName $renamed")
    commit("Rename $initialName to $renamed")

    vcsHelper.onCommit { msg ->
      git("commit -am '$msg'")
      true
    }

    `revert without auto-commit`(commit)

    assertSuccessfulNotification("Revert successful", "${commit.id.toShortString()} ${commit.subject}")
    repo.assertCommitted {
      modified(renamed)
    }
  }

  private fun commitMessageForRevert(commit: VcsFullCommitDetails): String {
    return """
        Revert "${commit.subject}"

        This reverts commit ${commit.id.toShortString()}""".trimIndent()
  }

  private fun prepareRevertConflict(fileName: String) : VcsFullCommitDetails {
    val file = file(fileName).create("initial\n").addCommit("initial")
    val commitToRevert = file.append("to revert\n").addCommit("temp content").details()
    file.append("conflict\n").addCommit("produce conflict")
    return commitToRevert
  }

  private fun revertAutoCommit(vararg commit: VcsFullCommitDetails) {
    updateChangeListManager()
    GitRevertOperation(project, listOf(*commit), true).execute()
  }

  private fun `revert without auto-commit`(commit: VcsFullCommitDetails) {
    updateChangeListManager()
    GitRevertOperation(project, listOf(commit), false).execute()
  }
}