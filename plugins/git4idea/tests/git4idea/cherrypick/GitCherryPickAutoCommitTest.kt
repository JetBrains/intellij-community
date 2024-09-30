// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.cherrypick

import git4idea.i18n.GitBundle
import git4idea.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GitCherryPickAutoCommitTest(private val createChangelistAutomatically: Boolean) : GitCherryPickTest() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun getModulesCount() = listOf(true, false)
  }

  override fun setUp() {
    super.setUp()
    setValueForTest(vcsAppSettings::CREATE_CHANGELISTS_AUTOMATICALLY, createChangelistAutomatically)
  }

  @Test
  fun `test cherry-pick from protected branch should add suffix by default`() {
    branch("feature")
    val commit = file("c.txt").create().addCommit("fix #1").hash()
    git("update-ref refs/remotes/origin/master HEAD")
    checkout("feature")

    cherryPick(commit)

    assertSuccessfulNotification("Cherry-pick successful", "${shortHash(commit)} fix #1")
    assertLastMessage("fix #1\n\n(cherry picked from commit ${commit})")
    changeListManager.waitScheduledChangelistDeletions()
    changeListManager.assertOnlyDefaultChangelist()
  }

  @Test
  fun `test simple cherry-pick`() {
    branch("feature")
    val commit = file("c.txt").create().addCommit("fix #1").hash()
    checkout("feature")

    cherryPick(commit)

    assertSuccessfulNotification("Cherry-pick successful", "${shortHash(commit)} fix #1")
    assertLastMessage("fix #1")
    changeListManager.waitScheduledChangelistDeletions()
    changeListManager.assertOnlyDefaultChangelist()
  }

  @Test
  fun `test dirty tree conflicting with commit`() {
    `check dirty tree conflicting with commit`()
  }

  @Test
  fun `test untracked file conflicting with commit`() {
    `check untracked file conflicting with commit`()
  }

  @Test
  fun `test conflict with cherry-picked commit should show merge dialog`() {
    `check conflict with cherry-picked commit should show merge dialog`()
  }

  @Test
  fun `test unresolved conflict with cherry-picked commit should produce a changelist`() {
    val commit = repo.prepareConflict()
    `do nothing on merge`()

    cherryPick(commit, expectSuccess = false)

    `assert merge dialog was shown`()

    if (vcsAppSettings.CREATE_CHANGELISTS_AUTOMATICALLY) {
      changeListManager.assertChangeListExists("on_master")
    }
    val notification = assertWarningNotification(GitBundle.message("apply.changes.operation.performed.with.conflicts", "Cherry-pick"), """
      ${shortHash(commit)} on_master
      There are unresolved conflicts in the working tree.
      """)
    assertEquals(2, notification.actions.size)
    assertEquals(GitBundle.message("apply.changes.unresolved.conflicts.notification.resolve.action.text"),
                 notification.actions[0].templateText)
    assertEquals(GitBundle.message("apply.changes.unresolved.conflicts.notification.abort.action.text", "Cherry-pick"),
                 notification.actions[1].templateText)
  }

  @Test
  fun `test resolve conflicts and commit`() {
    `check resolve conflicts and commit`()
  }

  @Test
  fun `test resolve conflicts but cancel commit`() {
    val commit = repo.prepareConflict()
    `mark as resolved on merge`()
    vcsHelper.onCommit { false }

    cherryPick(commit, expectSuccess = false)

    `assert merge dialog was shown`()
    `assert commit dialog was shown`()
    if (vcsAppSettings.CREATE_CHANGELISTS_AUTOMATICALLY) {
      changeListManager.assertChangeListExists("on_master")
    }
    assertNoNotification()
  }

  @Test
  fun `test cherry-pick 2 commits`() {
    branch("feature")
    val commit1 = file("one.txt").create().addCommit("fix #1").hash()
    val commit2 = file("two.txt").create().addCommit("fix #2").hash()
    checkout("feature")

    cherryPick(commit1, commit2)

    assertLogMessages("fix #2", "fix #1")
    assertSuccessfulNotification("Cherry-pick successful", """
      ${shortHash(commit1)} fix #1
      ${shortHash(commit2)} fix #2
""")
  }

  @Test
  fun `test cherry-picked 3 commits where 2nd conflicts with local changes`() {
    val common = file("common.txt")
    common.create("initial content\n").addCommit("common")
    branch("feature")
    val commit1 = file("one.txt").create().addCommit("fix #1").hash()
    val commit2 = common.append("on master\n").addCommit("appended common").hash()
    val commit3 = file("two.txt").create().addCommit("fix #2").hash()
    checkout("feature")
    common.append("on feature\n")

    cherryPick(commit1, commit2, commit3, expectSuccess = false)

    assertErrorNotification("Cherry-pick failed", """
      ${shortHash(commit2)} appended common
      """ + GitBundle.message("warning.your.local.changes.would.be.overwritten.by", "cherry-pick", "shelve") + """
      """ + GitBundle.message("apply.changes.operation.successful.for.commits", "cherry-pick", 1) + """
      ${shortHash(commit1)} fix #1""")

  }

  @Test
  fun `test cherry-pick 3 commits, where second conflicts with master`() {
    val common = file("common.txt")
    common.create("initial content\n").addCommit("common")
    branch("feature")
    val commit1 = file("one.txt").create().addCommit("fix #1").hash()
    val commit2 = common.append("on master\n").addCommit("appended common").hash()
    val commit3 = file("two.txt").create().addCommit("fix #2").hash()
    checkout("feature")
    common.append("on feature\n").addCommit("appended on feature").hash()
    `do nothing on merge`()

    cherryPick(commit1, commit2, commit3, expectSuccess = false)

    `assert merge dialog was shown`()
    assertLastMessage("fix #1")
  }

  // IDEA-73548
  @Test
  fun `test nothing to commit`() {
    val commit = file("c.txt").create().addCommit("fix #1").hash()
    repo.checkoutNew("feature")

    cherryPick(commit)

    assertWarningNotification("Nothing to cherry-pick",
                              "All changes from ${shortHash(commit)} have already been applied")
  }

  // IDEA-73548
  @Test
  fun `test several commits one of which have already been applied`() {
    file("common.txt").create("common content\n").addCommit("common file")
    repo.checkoutNew("feature")
    val commit1 = file("a.txt").create("initial\n").addCommit("fix #1").hash()
    val emptyCommit = file("common.txt").append("more to common\n").addCommit("to common").hash()
    val commit3 = file("a.txt").append("more\n").addCommit("fix #2").hash()
    checkout("master")
    file("common.txt").append("more to common\n").addCommit("to common from master")

    cherryPick(commit1, emptyCommit, commit3)

    assertLogMessages("fix #2", "fix #1")
    assertSuccessfulNotification("Applied 2 commits from 3", """
      ${shortHash(commit1)} fix #1
      ${shortHash(commit3)} fix #2
      ${shortHash(emptyCommit)} was skipped, because all changes have already been applied.""")
  }
}