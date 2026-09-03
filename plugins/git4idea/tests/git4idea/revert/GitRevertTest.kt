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

import com.intellij.ide.IdeBundle
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.assertErrorNotification
import com.intellij.vcs.test.assertSuccessfulNotification
import com.intellij.vcs.test.assertWarningNotification
import com.intellij.vcs.test.updateChangeListManager
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.i18n.GitBundle
import git4idea.test.GitSingleRepoContext
import git4idea.test.`assert commit dialog was shown`
import git4idea.test.`assert merge dialog was shown`
import git4idea.test.assertLatestSubjects
import git4idea.test.`do nothing on merge`
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.last
import git4idea.test.`mark as resolved on merge`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Revert works in two modes: revert & commit immediately, and show the commit dialog.
 *
 * For the sake of simplicity, this test covers only immediate revert, since the complicated logic which
 * updates the ChangeListManager and shows the commit dialog is tested in [git4idea.cherrypick.GitCherryPickAutoCommitTest],
 * except the case of reverting with conflicts when the commit dialog is shown anyway.
 */
@TestApplication
class GitRevertTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test simple revert`(): Unit = with(context) {
    val file = file("r.txt")
    val commit = file.create("initial\n").addCommit("Created r.txt").details()

    revertAutoCommit(commit)

    assertSuccessfulNotification("Revert successful", "${commit.id.toShortString()} ${commit.subject}")
    assertThat(file.exists()).describedAs("File should have been deleted").isFalse()
    repo.assertLatestSubjects("Revert \"${commit.subject}\"")
  }

  @Test
  fun `test local changes would be overwritten by revert`(): Unit = with(context) {
    val file = file("r.txt").create("initial\n")
    val commit = file.addCommit("Created r.txt").details()
    file.append("second\n")

    revertAutoCommit(commit)

    assertErrorNotification("Revert failed", """
      ${commit.id.toShortString()} ${commit.subject}
      """ + GitBundle.message("warning.your.local.changes.would.be.overwritten.by", "revert", "shelve"))
    assertThat(file.read()).describedAs("File content shouldn't change").isEqualTo("initial\nsecond\n")
    assertThat(last()).describedAs("No new commits should have been created").isEqualTo(commit.id.asString())
  }

  @Test
  fun `test empty revert`(): Unit = with(context) {
    val file = file("r.txt").create("initial\n").addCommit("Created r.txt")
    val commit = file.append("second\n").addCommit("Appended second").details()
    val lastCommit = file.write("initial\n").addCommit("Rolled back second").hash()

    revertAutoCommit(commit)

    assertWarningNotification("Nothing to revert", "All changes from ${commit.id.toShortString()} have already been reverted")
    assertThat(last()).describedAs("No new commits should have been created").isEqualTo(lastCommit)
  }

  @Test
  fun `test revert two commits`(): Unit = with(context) {
    val commit1 = file("a.txt").create().addCommit("Create a").details()
    val commit2 = file("b.txt").create().addCommit("Create b").details()

    revertAutoCommit(commit2, commit1)

    repo.assertLatestSubjects(
      "Revert \"${commit1.subject}\"",
      "Revert \"${commit2.subject}\""
    )
  }

  @Test
  fun `test one commit reverted, second fails with error`(): Unit = with(context) {
    val e = file("e.txt")
    val commit1 = e.create("e\n").addCommit("Created e").details()
    e.append("local changes")
    val rFile = file("r.txt")
    val commit2 = rFile.create("initial\n").addCommit("Created r.txt").details()

    revertAutoCommit(commit2, commit1)

    assertErrorNotification("Revert failed","""
      ${commit1.id.toShortString()} ${commit1.subject}
      """ + GitBundle.message("warning.your.local.changes.would.be.overwritten.by", "revert", "shelve") + """
      """ + GitBundle.message("apply.changes.operation.successful.for.commits", "revert", 1) + """
      ${commit2.id.toShortString()} ${commit2.subject}""")
    assertThat(rFile.exists()).describedAs("File should have been deleted").isFalse()
    repo.assertLatestSubjects("Revert \"${commit2.subject}\"")
  }

  @Test
  fun `test two commits reverted, one more was skipped because empty`(): Unit = with(context) {
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

  @Test
  fun `test revert with conflicts shows merge dialog`(): Unit = with(context) {
    val commitToRevert = prepareRevertConflict("c.txt")

    `do nothing on merge`()

    revertAutoCommit(commitToRevert)
    `assert merge dialog was shown`()
  }

  @Test
  fun `test revert with conflicts shows commit dialog after resolving conflicts`(): Unit = with(context) {
    val commitToRevert = prepareRevertConflict("c.txt")
    `mark as resolved on merge`()
    vcsHelper.onCommit { true }

    revertAutoCommit(commitToRevert)

    `assert commit dialog was shown`()
  }

  @Test
  fun `test revert with conflicts shows notification if conflicts not resolved`(): Unit = with(context) {
    val commitToRevert = prepareRevertConflict("c.txt")
    `do nothing on merge`()

    revertAutoCommit(commitToRevert)

    val notification = assertWarningNotification(GitBundle.message("apply.changes.operation.performed.with.conflicts", "Revert"), """
      ${commitToRevert.id.toShortString()} ${commitToRevert.subject}
      There are unresolved conflicts in the working tree.""")
    assertThat(notification.actions).hasSize(2)
    assertThat(notification.actions[0].templateText)
      .isEqualTo(GitBundle.message("apply.changes.unresolved.conflicts.notification.resolve.action.text"))
    assertThat(notification.actions[1].templateText)
      .isEqualTo(GitBundle.message("apply.changes.unresolved.conflicts.notification.abort.action.text", "Revert"))
  }

  @Test
  fun `test revert with conflicts resolve in chain`(): Unit = with(context) {
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

  @Test
  fun `test staged changes prevent revert with auto-commit`(): Unit = with(context) {
    val commit = file("a.txt").create().addCommit("fix #1").details()
    file("c.txt").create().add()

    revertAutoCommit(commit)

    assertErrorNotification("Revert failed",
                            GitBundle.message("warning.your.local.changes.would.be.overwritten.by", "revert", "shelve"),
                            listOf(IdeBundle.message("action.show.files"),
                                   GitBundle.message("apply.changes.save.and.retry.operation", "Shelve"))
    )
  }

  private fun GitSingleRepoContext.prepareRevertConflict(fileName: String): VcsFullCommitDetails {
    val file = file(fileName).create("initial\n").addCommit("initial")
    val commitToRevert = file.append("to revert\n").addCommit("temp content").details()
    file.append("conflict\n").addCommit("produce conflict")
    return commitToRevert
  }

  private fun GitSingleRepoContext.revertAutoCommit(vararg commit: VcsFullCommitDetails) {
    updateChangeListManager()
    GitRevertProcess(project, listOf(*commit)).execute()
  }
}