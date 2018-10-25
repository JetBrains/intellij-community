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
import com.intellij.openapi.components.service
import com.intellij.testFramework.VfsTestUtil
import git4idea.test.GitPlatformTest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

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

  private lateinit var authenticationManager: GithubAuthenticationManager

  protected lateinit var mainAccount: AccountData
  protected lateinit var secondaryAccount: AccountData

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    val host = System.getenv("idea.test.github.host")
    val token1 = System.getenv("idea.test.github.token1")
    val token2 = System.getenv("idea.test.github.token2")

    assertNotNull(host)
    assertNotNull(token1)
    assertNotNull(token2)

    authenticationManager = service()

    mainAccount = createAccountData(host, token1)
    secondaryAccount = createAccountData(host, token2)

    try {
      beforeTest()
      setCurrentAccount(mainAccount)
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

  private fun createAccountData(host: String, token: String): AccountData {
    val executorManager = service<GithubApiRequestExecutorManager>()
    val account = authenticationManager.registerAccount("token", host, token)
    val executor = executorManager.getExecutor(account)
    val username = executor.execute(GithubApiRequests.CurrentUser.get(account.server)).login

    return AccountData(token, account, username, executor)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      afterTest()
      setCurrentAccount(null)
      authenticationManager.clearAccounts()
    }
    finally {
      super.tearDown()
    }
  }

  @Throws(Exception::class)
  protected open fun beforeTest() {
  }

  @Throws(Exception::class)
  protected open fun afterTest() {
  }

  protected open fun setCurrentAccount(accountData: AccountData?) {
    authenticationManager.setDefaultAccount(myProject, accountData?.account)
  }

  protected fun createProjectFiles() {
    VfsTestUtil.createFile(projectRoot, "file.txt", "file.txt content")
    VfsTestUtil.createFile(projectRoot, "file", "file content")
    VfsTestUtil.createFile(projectRoot, "folder/file1", "file1 content")
    VfsTestUtil.createFile(projectRoot, "folder/file2", "file2 content")
    VfsTestUtil.createFile(projectRoot, "folder/empty_file")
    VfsTestUtil.createFile(projectRoot, "folder/dir/file3", "file3 content")
    VfsTestUtil.createDir(projectRoot, "folder/empty_folder")
  }

  override fun hasRemoteGitOperation() = true

  protected fun checkNotification(type: NotificationType, title: String?, content: String?) {
    val actualNotification = vcsNotifier.lastNotification
    assertNotNull("No notification was shown", actualNotification)

    if (title != null) {
      assertEquals("Notification has wrong title (content: " + actualNotification.content + ")", title, actualNotification.title)
    }
    if (content != null) {
      assertEquals("Notification has wrong content", content, actualNotification.content)
    }
    assertEquals("Notification has wrong type", type, actualNotification.type)
  }

  override fun runInDispatchThread() = true

  protected data class AccountData(val token: String,
                                   val account: GithubAccount,
                                   val username: String,
                                   val executor: GithubApiRequestExecutor)
}
