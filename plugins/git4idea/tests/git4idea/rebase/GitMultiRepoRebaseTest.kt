// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.mkdir
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.vcs.test.cleanupForAssertion
import git4idea.branch.GitBranchUiHandler
import git4idea.branch.GitBranchWorker
import git4idea.branch.GitRebaseParams
import git4idea.repo.GitRepository
import git4idea.test.UNKNOWN_ERROR_TEXT
import git4idea.test.git
import git4idea.test.resolveConflicts
import org.mockito.Mockito

class GitMultiRepoRebaseTest : GitRebaseBaseTest() {
  private lateinit var ultimate: GitRepository
  private lateinit var community: GitRepository
  private lateinit var contrib: GitRepository
  private lateinit var allRepositories: List<GitRepository>

  override fun setUp() {
    super.setUp()

    Executor.cd(projectRoot)
    val community = mkdir("community")
    val contrib = mkdir("contrib")

    ultimate = createRepository(projectPath)
    this.community = createRepository(community.path)
    this.contrib = createRepository(contrib.path)
    allRepositories = listOf(ultimate, this.community, this.contrib)

    Executor.cd(projectRoot)
    touch(".gitignore", "community\ncontrib")
    git(project, "add .gitignore")
    git(project, "commit -m gitignore")
  }

  fun `test all successful`() {
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

  fun `test abort from critical error during rebasing 2nd root, before any commits were applied`() {
    val localChange = LocalChange(community, "new.txt", "Some content")
    `fail with critical error while rebasing 2nd root`(localChange)

    assertErrorNotification("Rebase failed",
        """
        contrib: $UNKNOWN_ERROR_TEXT <br/>
        $LOCAL_CHANGES_WARNING
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

    assertNotNull(confirmation, "Abort confirmation message was not shown")
    assertEquals("Incorrect confirmation message text",
                 cleanupForAssertion("Do you want to rollback the successful rebase in community?"),
                 cleanupForAssertion(confirmation!!))
    assertNoRebaseInProgress(allRepositories)
    allRepositories.forEach { it.`assert feature not rebased on master`() }

    localChange.verify()
  }

  fun `test abort from critical error while rebasing 2nd root, after some commits were applied`() {
    val localChange = LocalChange(community, "new.txt", "Some content")
    `fail with critical error while rebasing 2nd root after some commits are applied`(localChange)

    vcsNotifier.lastNotification

    var confirmation: String? = null
    dialogManager.onMessage {
      confirmation = it
      Messages.YES
    }

    abortOngoingRebase()

    assertNotNull(confirmation, "Abort confirmation message was not shown")
    assertEquals("Incorrect confirmation message text",
                 cleanupForAssertion("Abort rebase in contrib only or also rollback rebase in community?"),
                 cleanupForAssertion(confirmation!!))
    assertNoRebaseInProgress(allRepositories)
    allRepositories.forEach { it.`assert feature not rebased on master`() }

    localChange.verify()
  }

  fun `test conflicts in multiple repositories are resolved separately`() {
    ultimate.`prepare simple conflict`()
    community.`prepare simple conflict`()
    contrib.`diverge feature and master`()

    refresh()
    updateChangeListManager()
    keepCommitMessageAfterConflict()

    var facedConflictInUltimate = false
    var facedConflictInCommunity = false
    vcsHelper.onMerge {
      assertFalse(facedConflictInCommunity && facedConflictInUltimate)
      if (ultimate.hasConflict("c.txt")) {
        assertFalse(facedConflictInUltimate)
        facedConflictInUltimate = true
        assertNoRebaseInProgress(community)
        ultimate.resolveConflicts()
      }
      else if (community.hasConflict("c.txt")) {
        assertFalse(facedConflictInCommunity)
        facedConflictInCommunity = true
        assertNoRebaseInProgress(ultimate)
        community.resolveConflicts()
      }
    }

    rebase("master")

    assertTrue(facedConflictInUltimate)
    assertTrue(facedConflictInCommunity)
    allRepositories.forEach {
      it.`assert feature rebased on master`()
      assertNoRebaseInProgress(it)
      it.assertNoLocalChanges()
    }
  }

  fun `test retry doesn't touch successful repositories`() {
    `fail with critical error while rebasing 2nd root`()

    GitRebaseUtils.continueRebase(project)

    assertSuccessfulRebaseNotification("Rebased feature on master")
    assertAllRebased()
    assertNoRebaseInProgress(allRepositories)
  }

  fun `test continue rebase shouldn't attempt to stash`() {
    ultimate.`diverge feature and master`()
    community.`prepare simple conflict`()
    contrib.`diverge feature and master`()

    refresh()
    updateChangeListManager()

    `do nothing on merge`()
    rebase("master")
    GitRebaseUtils.continueRebase(project)

    `assert conflict not resolved notification`()
    assertNotRebased("feature", "master", community)
  }

  fun `test continue rebase with unresolved conflicts should show merge dialog`() {
    ultimate.`diverge feature and master`()
    community.`prepare simple conflict`()
    contrib.`diverge feature and master`()

    refresh()
    updateChangeListManager()
    keepCommitMessageAfterConflict()

    `do nothing on merge`()
    rebase("master")

    var mergeDialogShown = false
    vcsHelper.onMerge {
      mergeDialogShown = true
      community.resolveConflicts()
    }
    GitRebaseUtils.continueRebase(project)

    assertTrue("Merge dialog was not shown", mergeDialogShown)
    assertAllRebased()
  }

  fun `test rollback if checkout with rebase fails on 2nd root`() {
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
      GitBranchWorker(project, git, uiHandler).rebaseOnCurrent(allRepositories, "feature")
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

    assertNotNull(confirmation, "Abort confirmation message was not shown")
    assertEquals("Incorrect confirmation message text",
                 cleanupForAssertion("Do you want to rollback the successful rebase in community?"),
                 cleanupForAssertion(confirmation!!))
    assertNoRebaseInProgress(allRepositories)
    allRepositories.forEach {
      it.`assert feature not rebased on master`()
      assertEquals("Incorrect current branch", "master", it.currentBranchName) }
  }

  private fun `fail with critical error while rebasing 2nd root`(localChange: LocalChange? = null) {
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

  private fun `fail with critical error while rebasing 2nd root after some commits are applied`(localChange: LocalChange? = null) {
    community.`diverge feature and master`()
    contrib.`make rebase fail on 2nd commit`()
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

  private fun rebase(onto: String) {
    GitTestingRebaseProcess(project, GitRebaseParams(vcs.version, onto), allRepositories).rebase()
  }

  private fun abortOngoingRebase() {
    GitRebaseUtils.abort(project, EmptyProgressIndicator())
  }

  private fun assertAllRebased() {
    assertRebased(ultimate, "feature", "master")
    assertRebased(community, "feature", "master")
    assertRebased(contrib, "feature", "master")
  }
}
