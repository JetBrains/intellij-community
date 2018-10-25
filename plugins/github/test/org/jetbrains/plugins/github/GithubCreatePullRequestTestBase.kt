// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Clock
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.VfsTestUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.DateFormatUtil
import git4idea.actions.GitInit
import git4idea.commands.Git
import git4idea.test.TestDialogHandler
import git4idea.test.git
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.test.GithubGitRepoTest
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog
import java.util.*

abstract class GithubCreatePullRequestTestBase : GithubGitRepoTest() {
  private lateinit var branchName: String

  override fun setUp() {
    super.setUp()

    val rnd = Random()
    val time = Clock.getTime()
    branchName = "branch_" + getTestName(false) + "_" + DateFormatUtil.formatDate(time).replace('/', '-') + "_" + rnd.nextLong()

    cd(projectRoot.path)
    cloneRepo()
    createBranch()
    createChanges()

    findGitRepo()
    repository.update()
  }

  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { deleteRemoteBranch() })
      .append(ThrowableRunnable { super.tearDown() })
      .run()
  }

  private fun deleteRemoteBranch() {
    service<Git>().push(repository, "origin", PROJECT_URL, ":$branchName", false)
  }

  protected fun registerDefaultCreatePullRequestDialogHandler(branch: String, user: String) {
    dialogManager.registerDialogHandler(GithubCreatePullRequestDialog::class.java, TestDialogHandler { dialog ->
      dialog.testSetRequestTitle(branchName)
      dialog.testSetFork(GithubFullPath(user, PROJECT_NAME))
      dialog.testSetBranch(branch)
      dialog.testCreatePullRequest()
      DialogWrapper.OK_EXIT_CODE
    })
  }

  private fun cloneRepo() {
    // project dir can be not empty
    git("init")
    git("remote add origin $PROJECT_URL")
    git("fetch")
    git("checkout -t origin/master")

    setGitIdentity(projectRoot)
    GitInit.refreshAndConfigureVcsMappings(myProject, projectRoot, projectRoot.path)
  }

  private fun createBranch() {
    git("branch $branchName")
    git("checkout $branchName")
  }

  private fun createChanges() {
    VfsTestUtil.createFile(projectRoot, "file.txt", "file.txt content")
    git("add file.txt")
    git("commit -m changes")
  }

  companion object {
    const val PROJECT_URL = "https://github.com/ideatest1/PullRequestTest"
    const val PROJECT_NAME = "PullRequestTest"
  }
}
