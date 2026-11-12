// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.assertErrorNotification
import com.intellij.vcs.test.cleanupForAssertion
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.branch.GitBranchUiHandler
import git4idea.branch.GitBranchWorker
import git4idea.branch.GitRebaseParams
import git4idea.config.GitSaveChangesPolicy
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.UNKNOWN_ERROR_TEXT
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.resolveConflicts
import git4idea.test.runUnderProgress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

@TestApplication
@EnableTracingFor(categories = ["#git4idea.rebase"])
internal class GitMultiRepoRebaseTest {
  private val saveChangesPolicy = GitSaveChangesPolicy.SHELVE
  private val contextFixture = gitPlatformContextFixture(saveChangesPolicy = saveChangesPolicy)
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var ultimate: GitRepository
  private lateinit var community: GitRepository
  private lateinit var contrib: GitRepository
  private lateinit var allRepositories: List<GitRepository>

  @BeforeEach
  fun setUp() {
    with(context) {
      cd(projectNioRoot)
      val communityDir = mkdir("community")
      val contribDir = mkdir("contrib")

      ultimate = createRepository(project, projectNioRoot, false)
      community = createRepository(project, communityDir, false)
      contrib = createRepository(project, contribDir, false)
      listOf(ultimate, community, contrib).forEach { it.hideIdeaProjectFilesFromGit() }

      cd(projectNioRoot)
      touch(".gitignore", "community\ncontrib")
      git("add .gitignore")
      git("commit -m gitignore")

      allRepositories = listOf(ultimate, community, contrib)
    }
  }

  @Test
  fun `test all successful`(): Unit = with(context) {
    ultimate.`place feature above master`()
    community.`diverge feature and master`()
    contrib.`place feature on master`()

    refresh()
    updateChangeListManager()

    rebase("master")

    assertSuccessfulRebaseNotification("Rebased feature on master")
    assertAllRebased()
    assertNoRebaseInProgress(allRepositories)
  }

  @Test
  fun `test abort from critical error during rebasing 2nd root, before any commits were applied`(): Unit = with(context) {
    val localChange = LocalChange(community, "new.txt", "Some content")
    `fail with critical error while rebasing 2nd root`(localChange)

    assertErrorNotification("Rebase failed",
                            """
                            contrib: $UNKNOWN_ERROR_TEXT <br/>
                            ${localChangesWarning(saveChangesPolicy)}
                            """)

    community.`assert feature rebased on master`()
    contrib.`assert feature not rebased on master`()
    ultimate.`assert feature not rebased on master`()
    assertNoRebaseInProgress(allRepositories)
    ultimate.assertNoLocalChanges()

    var confirmation: String? = null
    dialogManager.onMessage {
      confirmation = it
      Messages.YES
    }

    abortOngoingRebase()

    assertThat(confirmation).describedAs("Abort confirmation message was not shown").isNotNull()
    assertThat(cleanupForAssertion(confirmation!!))
      .describedAs("Incorrect confirmation message text")
      .isEqualTo(cleanupForAssertion("Do you want to rollback the successful rebase in community?"))
    assertNoRebaseInProgress(allRepositories)
    allRepositories.forEach { it.`assert feature not rebased on master`() }

    localChange.verify()
  }

  @Test
  fun `test abort from critical error while rebasing 2nd root, after some commits were applied`(): Unit = with(context) {
    val localChange = LocalChange(community, "new.txt", "Some content")
    `fail with critical error while rebasing 2nd root after some commits are applied`(localChange)

    vcsNotifier.lastNotification

    var confirmation: String? = null
    dialogManager.onMessage {
      confirmation = it
      Messages.YES
    }

    abortOngoingRebase()

    assertThat(confirmation).describedAs("Abort confirmation message was not shown").isNotNull()
    assertThat(cleanupForAssertion(confirmation!!))
      .describedAs("Incorrect confirmation message text")
      .isEqualTo(cleanupForAssertion("Abort rebase in contrib only or also rollback rebase in community?"))
    assertNoRebaseInProgress(allRepositories)
    allRepositories.forEach { it.`assert feature not rebased on master`() }

    localChange.verify()
  }

  @Test
  fun `test conflicts in multiple repositories are resolved separately`(): Unit = with(context) {
    ultimate.`prepare simple conflict`()
    community.`prepare simple conflict`()
    contrib.`diverge feature and master`()

    refresh()
    updateChangeListManager()
    keepCommitMessageAfterConflict()

    var facedConflictInUltimate = false
    var facedConflictInCommunity = false
    vcsHelper.onMerge {
      assertThat(facedConflictInCommunity && facedConflictInUltimate).isFalse()
      if (ultimate.hasConflict("c.txt")) {
        assertThat(facedConflictInUltimate).isFalse()
        facedConflictInUltimate = true
        assertNoRebaseInProgress(community)
        ultimate.resolveConflicts()
      }
      else if (community.hasConflict("c.txt")) {
        assertThat(facedConflictInCommunity).isFalse()
        facedConflictInCommunity = true
        assertNoRebaseInProgress(ultimate)
        community.resolveConflicts()
      }
    }

    rebase("master")

    assertThat(facedConflictInUltimate).isTrue()
    assertThat(facedConflictInCommunity).isTrue()
    allRepositories.forEach {
      it.`assert feature rebased on master`()
      assertNoRebaseInProgress(it)
      it.assertNoLocalChanges()
    }
  }

  @Test
  fun `test retry doesn't touch successful repositories`(): Unit = with(context) {
    `fail with critical error while rebasing 2nd root`()

    GitRebaseUtils.continueRebase(project)

    assertSuccessfulRebaseNotification("Rebased feature on master")
    assertAllRebased()
    assertNoRebaseInProgress(allRepositories)
  }

  @Test
  fun `test continue rebase shouldn't attempt to stash`(): Unit = with(context) {
    ultimate.`diverge feature and master`()
    community.`prepare simple conflict`()
    contrib.`diverge feature and master`()

    refresh()
    updateChangeListManager()

    vcsHelper.onMerge {}
    rebase("master")
    GitRebaseUtils.continueRebase(project)

    `assert conflict not resolved notification`()
    assertNotRebased("feature", "master", community)
  }

  @Test
  fun `test continue rebase with unresolved conflicts should show merge dialog`(): Unit = with(context) {
    ultimate.`diverge feature and master`()
    community.`prepare simple conflict`()
    contrib.`diverge feature and master`()

    refresh()
    updateChangeListManager()
    keepCommitMessageAfterConflict()

    vcsHelper.onMerge {}
    rebase("master")

    var mergeDialogShown = false
    vcsHelper.onMerge {
      mergeDialogShown = true
      community.resolveConflicts()
    }
    GitRebaseUtils.continueRebase(project)

    assertThat(mergeDialogShown).describedAs("Merge dialog was not shown").isTrue()
    assertAllRebased()
  }

  @Test
  fun `test rollback if checkout with rebase fails on 2nd root`(): Unit = with(context) {
    allRepositories.forEach {
      it.`diverge feature and master`()
      it.git("checkout master")
    }
    git.setShouldRebaseFail { it == contrib }

    refresh()
    updateChangeListManager()

    val uiHandler = Mockito.mock(GitBranchUiHandler::class.java)
    Mockito.`when`(uiHandler.progressIndicator).thenReturn(EmptyProgressIndicator())
    try {
      runUnderProgress {
        GitBranchWorker(project, git, uiHandler).rebaseOnCurrent(allRepositories, "feature")
      }
    }
    finally {
      git.setShouldRebaseFail { false }
    }

    var confirmation: String? = null
    dialogManager.onMessage {
      confirmation = it
      Messages.YES
    }

    abortOngoingRebase()

    assertThat(confirmation).describedAs("Abort confirmation message was not shown").isNotNull()
    assertThat(cleanupForAssertion(confirmation!!))
      .describedAs("Incorrect confirmation message text")
      .isEqualTo(cleanupForAssertion("Do you want to rollback the successful rebase in community?"))
    assertNoRebaseInProgress(allRepositories)
    allRepositories.forEach {
      it.`assert feature not rebased on master`()
      assertThat(it.currentBranchName).describedAs("Incorrect current branch").isEqualTo("master")
    }
  }

  private fun GitPlatformTestContext.`fail with critical error while rebasing 2nd root`(localChange: LocalChange? = null) {
    allRepositories.forEach { it.`diverge feature and master`() }
    localChange?.generate()

    refresh()
    updateChangeListManager()

    git.setShouldRebaseFail { it == contrib }
    try {
      rebase("master")
    }
    finally {
      git.setShouldRebaseFail { false }
    }
  }

  private fun GitPlatformTestContext.`fail with critical error while rebasing 2nd root after some commits are applied`(
    localChange: LocalChange? = null,
  ) {
    community.`diverge feature and master`()
    `make rebase fail on 2nd commit`(contrib)
    ultimate.`diverge feature and master`()
    localChange?.generate()

    refresh()
    updateChangeListManager()

    try {
      rebase("master")
    }
    finally {
      git.setShouldRebaseFail { false }
    }
  }

  private fun GitPlatformTestContext.rebase(onto: String) {
    GitTestingRebaseProcess(project, GitRebaseParams(vcs.version, onto), allRepositories).rebase()
  }

  private fun GitPlatformTestContext.abortOngoingRebase() {
    GitRebaseUtils.abort(project, EmptyProgressIndicator())
  }

  private fun assertAllRebased() {
    assertRebased(ultimate, "feature", "master")
    assertRebased(community, "feature", "master")
    assertRebased(contrib, "feature", "master")
  }
}
