/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Clock
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.testFramework.VfsTestUtil
import com.intellij.util.text.DateFormatUtil
import git4idea.actions.GitInit
import git4idea.commands.Git
import git4idea.test.TestDialogHandler
import org.jetbrains.plugins.github.api.GithubFullPath
import org.jetbrains.plugins.github.test.GithubTest
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog
import org.jetbrains.plugins.github.util.GithubGitHelper
import java.util.*

/**
 * @author Aleksey Pivovarov
 */
abstract class GithubCreatePullRequestTestBase : GithubTest() {
  protected var BRANCH_NAME: String

  override fun beforeTest() {
    val rnd = Random()
    val time = Clock.getTime()
    BRANCH_NAME = "branch_" + getTestName(false) + "_" + DateFormatUtil.formatDate(time).replace('/', '-') + "_" + rnd.nextLong()

    registerHttpAuthService()

    cd(projectRoot.path)
    cloneRepo()
    createBranch()
    createChanges()
    initGitChecks()
    myRepository!!.update()
  }

  override fun afterTest() {
    deleteRemoteBranch()
  }

  protected fun deleteRemoteBranch() {
    val repository = GithubGitHelper.findGitRepository(myProject, projectRoot)
    if (repository != null) {
      Git.getInstance().push(repository, "origin", PROJECT_URL, ":$BRANCH_NAME", false)
    }
  }

  protected fun registerDefaultCreatePullRequestDialogHandler(branch: String, user: String) {
    dialogManager.registerDialogHandler(GithubCreatePullRequestDialog::class.java, TestDialogHandler { dialog ->
      dialog.testSetRequestTitle(BRANCH_NAME)
      dialog.testSetFork(GithubFullPath(user, PROJECT_NAME))
      dialog.testSetBranch(branch)
      dialog.testCreatePullRequest()
      DialogWrapper.OK_EXIT_CODE
    })
  }

  protected fun cloneRepo() {
    // project dir can be not empty
    git("init")
    git("remote add origin $PROJECT_URL")
    git("fetch")
    git("checkout -t origin/master")

    setGitIdentity(projectRoot)
    GitInit.refreshAndConfigureVcsMappings(myProject, projectRoot, projectRoot.path)
  }

  protected fun createBranch() {
    git("branch $BRANCH_NAME")
    git("checkout $BRANCH_NAME")
  }

  protected fun createChanges() {
    VfsTestUtil.createFile(projectRoot, "file.txt", "file.txt content")
    git("add file.txt")
    git("commit -m changes")
  }

  companion object {
    protected val PROJECT_URL = "https://github.com/ideatest1/PullRequestTest"
    protected val PROJECT_NAME = "PullRequestTest"
  }
}
