/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.test

import com.intellij.notification.NotificationType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import git4idea.commands.GitHttpAuthService
import git4idea.commands.GitHttpAuthenticator
import git4idea.config.GitConfigUtil
import git4idea.repo.GitRepository
import git4idea.test.GitHttpAuthTestService
import git4idea.test.GitPlatformTest
import git4idea.test.git
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubUtil
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue

/**
 *
 * The base class for JUnit platform tests of the github plugin.<br></br>
 * Extend this test to write a test on GitHub which has the following features/limitations:
 *
 *  * This is a "platform test case", which means that IDEA [almost] production platform is set up before the test starts.
 *  * Project base directory is the root of everything.
 *
 *
 * All tests inherited from this class are required to have a token to access the Github server.
 * They are set up in Environment variables: <br></br>
 * `idea.test.github.host<br></br>
 * idea.test.github.token1<br></br> // token test user
 * idea.test.github.token2` // token user with configured test repositories
 *
 */
abstract class GithubTest : GitPlatformTest() {

  protected var myRepository: GitRepository? = null

  private var myHttpAuthService: GitHttpAuthTestService
  protected var myAuthenticationManager: GithubAuthenticationManager

  protected var myAccount: GithubAccount
  protected var myExecutor: GithubApiRequestExecutor
  protected var myAccount2: GithubAccount
  protected var myExecutor2: GithubApiRequestExecutor

  protected var myUsername: String
  protected var myUsername2: String

  private var myToken: String? = null

  protected fun createProjectFiles() {
    VfsTestUtil.createFile(projectRoot, "file.txt", "file.txt content")
    VfsTestUtil.createFile(projectRoot, "file", "file content")
    VfsTestUtil.createFile(projectRoot, "folder/file1", "file1 content")
    VfsTestUtil.createFile(projectRoot, "folder/file2", "file2 content")
    VfsTestUtil.createFile(projectRoot, "folder/empty_file")
    VfsTestUtil.createFile(projectRoot, "folder/dir/file3", "file3 content")
    VfsTestUtil.createDir(projectRoot, "folder/empty_folder")
  }

  override fun hasRemoteGitOperation(): Boolean {
    return true
  }

  protected fun checkNotification(type: NotificationType, title: String?, content: String?) {
    val actualNotification = vcsNotifier.lastNotification
    TestCase.assertNotNull("No notification was shown", actualNotification)

    if (title != null) {
      TestCase.assertEquals("Notification has wrong title (content: " + actualNotification.content + ")", title, actualNotification.title)
    }
    if (content != null) {
      TestCase.assertEquals("Notification has wrong content", content, actualNotification.content)
    }
    TestCase.assertEquals("Notification has wrong type", type, actualNotification.type)
  }

  protected fun registerHttpAuthService() {
    val myHttpAuthService = ServiceManager.getService(GitHttpAuthService::class.java) as GitHttpAuthTestService
    myHttpAuthService.register(object : GitHttpAuthenticator {
      override fun askPassword(url: String): String {
        return GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE
      }

      override fun askUsername(url: String): String {
        return myToken
      }

      override fun saveAuthData() {}

      override fun forgetPassword() {}

      override fun wasCancelled(): Boolean {
        return false
      }
    })
  }

  // workaround: user on test server got "" as username, so git can't generate default identity
  protected fun setGitIdentity(root: VirtualFile) {
    try {
      GitConfigUtil.setValue(myProject, root, "user.name", "Github Test")
      GitConfigUtil.setValue(myProject, root, "user.email", "githubtest@jetbrains.com")
    }
    catch (e: VcsException) {
      e.printStackTrace()
    }

  }

  protected fun initGitChecks() {
    myRepository = repositoryManager.getRepositoryForFile(projectRoot)
  }

  protected fun checkGitExists() {
    TestCase.assertNotNull("Git repository does not exist", myRepository)
  }

  protected fun checkRemoteConfigured() {
    TestCase.assertNotNull(myRepository)

    TestCase.assertTrue("GitHub remote is not configured", GithubGitHelper.getInstance().hasAccessibleRemotes(myRepository!!))
  }

  protected fun checkLastCommitPushed() {
    TestCase.assertNotNull(myRepository)

    val hash = myRepository.git("log -1 --pretty=%h")
    val ans = myRepository.git("branch --contains $hash -a")
    TestCase.assertTrue(ans.contains("remotes/origin"))
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    val host = System.getenv("idea.test.github.host")
    val token1 = System.getenv("idea.test.github.token1")
    val token2 = System.getenv("idea.test.github.token2")

    // TODO change to assert when a stable Github testing server is ready
    assumeNotNull(host)
    assumeTrue(token1 != null && token2 != null)
    myAuthenticationManager = GithubAuthenticationManager.getInstance()
    val executorManager = GithubApiRequestExecutorManager.getInstance()
    myAccount = myAuthenticationManager.registerAccount("account1", host, token1!!)
    myExecutor = executorManager.getExecutor(myAccount)
    myAccount2 = myAuthenticationManager.registerAccount("account2", host, token2!!)
    myExecutor2 = executorManager.getExecutor(myAccount2)
    myToken = token1

    myUsername = myExecutor.execute<GithubAuthenticatedUser>(GithubApiRequests.CurrentUser.get(myAccount.server)).getLogin()
    myUsername2 = myExecutor2.execute<GithubAuthenticatedUser>(GithubApiRequests.CurrentUser.get(myAccount2.server)).getLogin()

    myHttpAuthService = ServiceManager.getService(GitHttpAuthService::class.java) as GitHttpAuthTestService

    try {
      beforeTest()
    }
    catch (e: Exception) {
      try {
        tearDown()
      }
      catch (e2: Exception) {
        e2.printStackTrace()
      }

      throw e
    }

  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      afterTest()
      myAuthenticationManager.setDefaultAccount(myProject, null)
      myAuthenticationManager.clearAccounts()
    }
    finally {
      if (myHttpAuthService != null) myHttpAuthService.cleanup()
      super.tearDown()
    }
  }

  protected open fun beforeTest() {}

  @Throws(Exception::class)
  protected open fun afterTest() {
  }

  override fun runInDispatchThread(): Boolean {
    return true
  }


  protected fun git(command: String) {
    this.git(command, false)
  }
}
