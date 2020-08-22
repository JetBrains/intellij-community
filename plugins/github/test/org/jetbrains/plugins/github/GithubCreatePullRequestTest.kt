// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.testFramework.VfsTestUtil
import com.intellij.util.ThreeState
import git4idea.actions.GitInit
import git4idea.test.TestDialogHandler
import git4idea.test.git
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.test.GithubGitRepoTest
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager
import org.jetbrains.plugins.github.util.GithubSettings

class GithubCreatePullRequestTest : GithubGitRepoTest() {
  private lateinit var mainRepo: GithubRepo

  override fun setUp() {
    super.setUp()

    mainRepo = createUserRepo(mainAccount, true)
    registerDefaultCreatePullRequestDialogHandler()
  }

  private fun registerDefaultCreatePullRequestDialogHandler() {
    dialogManager.registerDialogHandler(GithubCreatePullRequestDialog::class.java, TestDialogHandler { dialog ->
      dialog.testSetRequestTitle("TestPR")
      dialog.testSetFork(mainRepo.fullPath)
      dialog.testSetBranch("master")
      dialog.testCreatePullRequest()
      DialogWrapper.OK_EXIT_CODE
    })
  }

  fun testBranch() {
    cloneRepo(mainRepo)
    createBranch()
    createChanges()
    repository.update()

    val coordinatesSet = myProject.service<GHProjectRepositoriesManager>().knownRepositories
    assertSize(1, coordinatesSet)
    val coordinates = coordinatesSet.first().gitRemote

    GithubCreatePullRequestAction.createPullRequest(myProject, repository, coordinates.remote, coordinates.url, mainAccount.account)

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null)
    checkRemoteConfigured()
    checkLastCommitPushed()
  }

  private fun createBranch() {
    git("branch prBranch")
    git("checkout prBranch")
  }

  fun testFork() {
    val fork = forkRepo(secondaryAccount, mainRepo)

    cloneRepo(fork)
    createChanges()

    val coordinatesSet = myProject.service<GHProjectRepositoriesManager>().knownRepositories
    assertSize(1, coordinatesSet)
    val coordinates = coordinatesSet.first().gitRemote

    service<GithubSettings>().createPullRequestCreateRemote = ThreeState.YES
    GithubCreatePullRequestAction.createPullRequest(myProject, repository, coordinates.remote, coordinates.url, secondaryAccount.account)

    checkNotification(NotificationType.INFORMATION, "Successfully created pull request", null)
    checkRemoteConfigured()
    checkLastCommitPushed()
  }

  private fun cloneRepo(repo: GithubRepo) {
    cd(projectRoot)
    git("init")
    setGitIdentity(projectRoot)
    GitInit.refreshAndConfigureVcsMappings(myProject, projectRoot, projectRoot.path)
    findGitRepo()

    repository.apply {
      git("remote add origin ${repo.cloneUrl}")
      // fork is initialized in background and there's nothing to poll
      for (i in 1..10) {
        try {
          git("fetch")
          break
        }
        catch (ise: IllegalStateException) {
          if (i == 5) throw ise
          Thread.sleep(50L)
        }
      }
      git("checkout -t origin/master")
      update()
    }
  }

  private fun createChanges() {
    VfsTestUtil.createFile(projectRoot, "file.txt", "file.txt content")
    repository.apply {
      git("add file.txt")
      git("commit -m changes")
    }
  }
}
