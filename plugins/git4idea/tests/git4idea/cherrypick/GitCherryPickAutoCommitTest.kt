// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.dvcs.repo.Repository
import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.registry.Registry
import git4idea.GitUtil
import git4idea.cherrypick.GitCherryPickContinueProcess.launchCherryPick
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class GitCherryPickAutoCommitTest(private val createChangelistAutomatically: Boolean, private val useGitSequencer: Boolean, private val emptyCherryPickResolutionStrategy: EmptyCherryPickResolutionStrategy) : GitCherryPickTest() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "createChangelist={0}, useGitSequencer={1}, emptyCommitStrategy={2}")
    fun getModulesCount(): List<Array<Any>> {
      val booleanValues = listOf(true, false)
      val strategyValues = EmptyCherryPickResolutionStrategy.entries

      return booleanValues.flatMap { createChangelist ->
        booleanValues.flatMap { useGitSequencer ->
          strategyValues.map { strategy ->
            arrayOf(createChangelist, useGitSequencer, strategy)
          }
        }
      }
    }
  }

  override fun setUp() {
    super.setUp()
    vcsAppSettings.CREATE_CHANGELISTS_AUTOMATICALLY = createChangelistAutomatically
    gitVcsSettings.emptyCherryPickResolutionStrategy = emptyCherryPickResolutionStrategy
    vcsNotifier.cleanup()
    Registry.get("git.cherry.pick.use.git.sequencer").setValue(useGitSequencer)
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
    // TODO: Investigate why it's super flaky for this case
    // In practice git will check all the sequence before starting, see that there are local changes
    // and stop directly at the first commit
    if (useGitSequencer) return
    val commonFile = file("common.txt")
    commonFile.create("initial content\n").addCommit("common")
    branch("feature")
    val fix1 = file("one.txt").create().addCommit("fix #1").hash()
    val common = commonFile.append("on master\n").addCommit("appended common").hash()
    val fix3 = file("two.txt").create().addCommit("fix #2").hash()
    checkout("feature")
    commonFile.append("on feature\n")

    cherryPick(fix1, common, fix3, expectSuccess = false)

    assertErrorNotification("Cherry-pick failed", """
      ${shortHash(common)} appended common
      """ + GitBundle.message("warning.your.local.changes.would.be.overwritten.by", "cherry-pick", "shelve") + """
      """ + GitBundle.message("apply.changes.operation.successful.for.commits", "cherry-pick", 1) + """
      ${shortHash(fix1)} fix #1""")

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

    if (useGitSequencer) {
      // When using the git sequencer it will stop at the empty commit, waiting for a continue
      assertLogMessages("fix #1")
      assertSuccessfulNotification("Applied 1 commit from 2", """
      ${shortHash(commit1)} fix #1
      ${shortHash(emptyCommit)} was skipped, because all changes have already been applied.""")
    }
    else {
      when (emptyCherryPickResolutionStrategy) {
        EmptyCherryPickResolutionStrategy.SKIP -> assertLogMessages("fix #2", "fix #1")
        EmptyCherryPickResolutionStrategy.CREATE_EMPTY -> assertLogMessages("fix #2", "to common", "fix #1")
      }
      assertSuccessfulNotification("Applied 2 commits from 3", """
      ${shortHash(commit1)} fix #1
      ${shortHash(commit3)} fix #2
      ${shortHash(emptyCommit)} was skipped, because all changes have already been applied.""")
    }
  }

  @Test
  fun `staged changes prevent cherry-pick`() {
    val commit = file("a.txt").create().addCommit("fix #1").hash()
    file("b.txt").create().add()

    cherryPick(commit, expectSuccess = false)

    assertErrorNotification("Cherry-pick failed",
                            GitBundle.message("warning.your.local.changes.would.be.overwritten.by", "cherry-pick", "shelve"),
                            listOf(IdeBundle.message("action.show.files"),
                                   GitBundle.message("apply.changes.save.and.retry.operation", "Shelve"))
    )
  }

  // IJPL-84826
  @Test
  fun `test cherry-pick continue`(): Unit = runBlocking {
    // Setup: create conflicting commits
    val file = file("file.txt")
    file.create("line 1\n").addCommit("initial")

    git("checkout -b feature")
    val featureCommit = file.append("line 2 from feature\n").addCommit("feature change").hash()

    git("checkout master")
    file.write("line 1\nline 2 from master\n")
    addCommit("master change")

    // Start cherry-pick that will conflict
    `do nothing on merge`()
    cherryPick(featureCommit, expectSuccess = false)

    `assert merge dialog was shown`()
    assertEquals(Repository.State.GRAFTING, repo.state)

    // Resolve conflicts
    val resolved = file.write("line 1\nline 2 resolved\n")
    git("add file.txt")

    // Test: continue cherry-pick
    launchCherryPick(repo).join()

    // Verify
    assertEquals(Repository.State.NORMAL, repo.state)
    assertTrue(file.read().contains("line 2 resolved"))
    assertSuccessfulNotification(title = "Cherry-pick continue successful", message = "${shortHash(resolved.hash())} feature change")
  }

  // Conflict on the last commit — after resolving, --continue finishes the sequence
  @Test
  fun `test cherry-pick continue on last commit finishes sequence`(): Unit = runBlocking {
    val common = file("common_last.txt")
    common.create("base\n").addCommit("base")

    // Create two commits; the second will conflict with master
    branch("feature")
    val c1 = file("a_last.txt").create().addCommit("last-c1").hash()
    val c2 = common.append("on master\n").addCommit("last-c2 common").hash()

    // On feature, diverge to make c2 conflict when cherry-picked onto master
    checkout("feature")
    common.append("on feature\n").addCommit("feature-touch").hash()

    // Start cherry-pick: c1 applies cleanly, c2 conflicts and stops
    `do nothing on merge`()
    cherryPick(c1, c2, expectSuccess = false)
    `assert merge dialog was shown`()
    assertEquals(Repository.State.GRAFTING, repo.state)

    // Resolve conflict and stage
    val resolved = common.write("base\non master\non feature\n")
    git("add common_last.txt")

    // Continue — regardless of sequencer setting, nothing else left to apply
    launchCherryPick(repo).join()

    // Assert finished and the last commit applied
    assertEquals(Repository.State.NORMAL, repo.state)
    repo.assertLatestSubjects("last-c2 common")
    assertSuccessfulNotification(title = "Cherry-pick continue successful", message = "${shortHash(resolved.hash())} last-c2 common")
  }

  // Conflict in the middle — behavior depends on registry
  // - Disabled (sequencer off): continue applies only the conflicted commit, does NOT proceed with the rest
  // - Enabled (sequencer on): continue proceeds with the remaining commits in the sequence
  @Test
  fun `test cherry-pick continue on middle conflict honors sequencer setting`(): Unit = runBlocking {
    val common = file("common_mid.txt")
    common.create("base\n").addCommit("base")

    branch("feature")
    val c1 = file("one_mid.txt").create().addCommit("mid-c1").hash()
    val c2 = common.append("on master\n").addCommit("mid-c2 common").hash()
    val c3 = file("two_mid.txt").create().addCommit("mid-c3").hash()

    // Make c2 conflict by changing the same file on feature
    checkout("feature")
    common.append("on feature\n").addCommit("feature-side").hash()

    // Start: pick c1,c2,c3 – we expect stop at c2
    `do nothing on merge`()
    cherryPick(c1, c2, c3, expectSuccess = false)
    `assert merge dialog was shown`()
    assertEquals(Repository.State.GRAFTING, repo.state)

    // Resolve conflict and stage
    common.write("base\non master\non feature\n")
    git("add common_mid.txt")

    // Act: continue
    launchCherryPick(repo).join()
    // Assert based on sequencer setting
    if (useGitSequencer) {
      // Sequencer enabled: continue applies remaining commits (c3)
      assertEquals(Repository.State.NORMAL, repo.state)
      repo.assertLatestSubjects("mid-c3", "mid-c2 common")
      val commits = GitLogUtil.collectMetadata(project, repo.root).commits
      val head = commits[0].id.toShortString()
      val head1 = commits[1].id.toShortString()
      assertSuccessfulNotification(title = "Cherry-pick continue successful", message = "$head mid-c3<br/>$head1 mid-c2 common")
    }
    else {
      // Sequencer disabled: continue commits only the conflicted commit (c2); c3 is NOT auto-applied
      assertEquals(Repository.State.NORMAL, repo.state)
      repo.assertLatestSubjects("mid-c2 common")
      val head = GitUtil.getHead(repo)!!.toShortString()
      assertSuccessfulNotification(title = "Cherry-pick continue successful", message = "$head mid-c2 common")
    }
  }

  @Test
  fun `test cherry-pick continue fails when conflicts unresolved`() = runBlocking {
    // Arrange: set up a conflict and leave it unresolved
    val f = file("conflict_unresolved.txt")
    f.create("base\n").addCommit("base")

    branch("feature")
    val featureCommit = f.append("feature change\n").addCommit("feature change").hash()

    checkout("master")
    f.append("master change\n").addCommit("master change").hash()

    // Start cherry-pick to cause a conflict and leave it unresolved
    `do nothing on merge`()
    cherryPick(featureCommit, expectSuccess = false)
    `assert merge dialog was shown`()
    assertEquals(Repository.State.GRAFTING, repo.state)

    // Act: try to continue with unresolved conflicts
    launchCherryPick(repo).join()

    // Assert: still grafting, operation didn't finish
    assertEquals(Repository.State.GRAFTING, repo.state)
  }
}
