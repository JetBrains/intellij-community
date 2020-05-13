// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.test

import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Clock
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.VfsTestUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.text.DateFormatUtil
import git4idea.test.GitPlatformTest
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import java.util.concurrent.ThreadLocalRandom

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

  private lateinit var organisation: String
  protected lateinit var mainAccount: AccountData
  protected lateinit var secondaryAccount: AccountData

  private val mainRepos = mutableSetOf<String>()
  private val secondaryRepos = mutableSetOf<String>()

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

    organisation = System.getenv("idea.test.github.org")
    assertNotNull(organisation)
    mainAccount = createAccountData(host, token1)
    secondaryAccount = createAccountData(host, token2)
    setCurrentAccount(mainAccount)
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
    RunAll()
      .append(ThrowableRunnable { deleteRepos(secondaryAccount, secondaryRepos) })
      .append(ThrowableRunnable { deleteRepos(mainAccount, mainRepos) })
      .append(ThrowableRunnable { setCurrentAccount(null) })
      .append(ThrowableRunnable { if (::authenticationManager.isInitialized) authenticationManager.clearAccounts() })
      .append(ThrowableRunnable { super.tearDown() })
      .run()
  }

  override fun getDebugLogCategories(): Collection<String> {
    return super.getDebugLogCategories() + "#org.jetbrains.plugins.github.api.GithubApiRequestExecutor"
  }

  protected open fun setCurrentAccount(accountData: AccountData?) {
    authenticationManager.setDefaultAccount(myProject, accountData?.account)
  }

  protected fun createUserRepo(account: AccountData, autoInit: Boolean = false): GithubRepo {
    setCurrentAccount(account)
    val repo = account.executor.execute(
      GithubApiRequests.CurrentUser.Repos.create(account.account.server, createRepoName(), "", false, autoInit))
    recycleRepo(account, repo.url)
    return repo
  }

  protected fun forkRepo(account: AccountData, upstream: GithubRepo): GithubRepo {
    setCurrentAccount(account)
    val repo = account.executor.execute(
      GithubApiRequests.Repos.Forks.create(account.account.server, upstream.userName, upstream.name))
    recycleRepo(account, repo.url)
    return repo
  }

  protected fun createOrgRepo(): GithubRepo {
    setCurrentAccount(mainAccount)
    val repo = mainAccount.executor.execute(
      GithubApiRequests.Organisations.Repos.create(mainAccount.account.server, organisation, createRepoName(), "", false))
    recycleRepo(mainAccount, repo.url)
    return repo
  }

  private fun recycleRepo(account: AccountData, url: String) {
    if (account === mainAccount) {
      mainRepos.add(url)
    }
    else {
      secondaryRepos.add(url)
    }
  }

  fun createRepoName(): String {
    val rnd = ThreadLocalRandom.current()
    val time = Clock.getTime()
    return getTestName(false) +
           "_" + DateFormatUtil.formatDate(time).replace('/', '-') +
           "_" + rnd.nextLong()
  }

  protected fun checkRepoExists(account: AccountData, repoPath: GHRepositoryPath) {
    val repo = account.executor.execute(
      GithubApiRequests.Repos.get(account.account.server, repoPath.owner, repoPath.repository))
    assertNotNull("GitHub repository does not exist", repo)
    recycleRepo(account, repo!!.url)
  }

  private fun deleteRepos(account: AccountData, repos: Collection<String>) {
    setCurrentAccount(account)
    for (repo in repos) {
      account.executor.execute(GithubApiRequests.Repos.delete(repo))
    }
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

  companion object {
    private const val RETRIES = 3

    internal fun retry(LOG: Logger, action: () -> Unit) {
      for (i in 1..RETRIES) {
        try {
          LOG.debug("Attempt #$i")
          return action()
        }
        catch (e: Throwable) {
          if (i == RETRIES) throw e
          Thread.sleep(1000L)
        }
      }
    }
  }
}
