// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.remote

import com.intellij.openapi.components.service
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.UriUtil
import com.intellij.util.io.URLUtil
import com.intellij.xml.util.XmlStringUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.GitHttpAuthService
import git4idea.commands.GitHttpAuthenticator
import git4idea.config.GitVersion
import git4idea.test.GitHttpAuthTestService
import git4idea.test.GitPlatformTest
import org.assertj.core.api.Assertions.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GitRemoteTest : GitPlatformTest() {

  private lateinit var authenticator: TestAuthenticator
  private lateinit var authTestService: GitHttpAuthTestService

  private lateinit var host: String
  private lateinit var token: String

  override fun setUp() {
    super.setUp()

    host = System.getenv("idea_test_github_host") ?: System.getenv("idea.test.github.host")
    token = System.getenv("idea_test_github_token1") ?: System.getenv("idea.test.github.token1")

    authenticator = TestAuthenticator()
    authTestService = service<GitHttpAuthService>() as GitHttpAuthTestService
    authTestService.register(authenticator)
  }

  private fun makeUrl(): String {
    return "${host}/testmaster/$PROJECT_NAME.git"
  }

  private fun makeUrlWithUsername(): String {
    return UriUtil.splitScheme(host).let {
      val scheme = it.first
      if (scheme.isNotEmpty()) it.first + URLUtil.SCHEME_SEPARATOR + USERNAME + "@" + it.second + "/testmaster/" + PROJECT_NAME + ".git"
      else USERNAME + "@" + it.second + "/testmaster/" + PROJECT_NAME + ".git"
    }
  }

  override fun tearDown() {
    try {
      authTestService.cleanup()
    }
    finally {
      super.tearDown()
    }
  }

  override fun hasRemoteGitOperation() = true

  fun `test clone from http with username`() {
    val cloneWaiter = cloneOnPooledThread(makeUrlWithUsername())

    assertPasswordAsked()
    authenticator.supplyPassword(token)

    assertCloneSuccessful(cloneWaiter)
  }

  fun `test clone from http without username`() {
    val cloneWaiter = cloneOnPooledThread(makeUrl())

    assertUsernameAsked()
    authenticator.supplyUsername(USERNAME)
    assertPasswordAsked()
    authenticator.supplyPassword(token)

    assertCloneSuccessful(cloneWaiter)
  }

  fun `test clone fails if incorrect password`() {
    val url = makeUrlWithUsername()

    val cloneWaiter = cloneOnPooledThread(url)

    assertPasswordAsked()
    authenticator.supplyPassword("incorrect")

    assertTrue("Clone didn't complete during the reasonable period of time", cloneWaiter.await(30, TimeUnit.SECONDS))
    assertThat(testNioRoot.resolve(PROJECT_NAME)).describedAs("Repository directory shouldn't be created").doesNotExist()

    val gitVersion = vcs.version
    val expectedAuthFailureMessage = when {
      gitVersion.isLaterOrEqual(GitVersion(2, 22, 0, 0)) -> {
        StringUtil.escapeXmlEntities("Authentication failed for '${makeUrl()}/'")
      }
      gitVersion.isLaterOrEqual(GitVersion(1, 8, 3, 0)) -> {
        StringUtil.escapeXmlEntities("Authentication failed for '$url/'")
      }
      else -> {
        "Authentication failed"
      }
    }
    assertErrorNotification("Clone failed", expectedAuthFailureMessage)
  }

  private fun cloneOnPooledThread(url: String): CountDownLatch {
    val cloneWaiter = CountDownLatch(1)
    executeOnPooledThread {
      val projectName = url.substring(url.lastIndexOf('/') + 1).replace(".git", "")
      GitCheckoutProvider.doClone(project, git, projectName, testNioRoot.toString(), url)
      cloneWaiter.countDown()
    }
    return cloneWaiter
  }

  private fun assertCloneSuccessful(cloneCompleted: CountDownLatch) {
    assertTrue("Clone didn't complete during the reasonable period of time", cloneCompleted.await(30, TimeUnit.SECONDS))
    assertThat(testNioRoot.resolve(PROJECT_NAME)).describedAs("Repository directory was not found").exists()
  }

  private fun assertPasswordAsked() {
    authenticator.waitUntilPasswordIsAsked()
    assertTrue("Password was not requested", authenticator.wasPasswordAsked())
  }

  private fun assertUsernameAsked() {
    authenticator.waitUntilUsernameIsAsked()
    assertTrue("Username was not requested", authenticator.wasUsernameAsked())
  }

  private class TestAuthenticator : GitHttpAuthenticator {
    private val TIMEOUT = 10

    private val passwordAskedWaiter = CountDownLatch(1)
    private val usernameAskedWaiter = CountDownLatch(1)
    private val passwordSuppliedWaiter = CountDownLatch(1)
    private val usernameSuppliedWaiter = CountDownLatch(1)

    @Volatile
    private var passwordAsked: Boolean = false
    @Volatile
    private var usernameAsked: Boolean = false

    @Volatile
    private lateinit var password: String
    @Volatile
    private lateinit var username: String

    override fun askPassword(url: String): String {
      passwordAsked = true
      passwordAskedWaiter.countDown()
      assertTrue("Password was not supplied during the reasonable period of time",
                 passwordSuppliedWaiter.await(TIMEOUT.toLong(), TimeUnit.SECONDS))
      return password
    }

    override fun askUsername(url: String): String {
      usernameAsked = true
      usernameAskedWaiter.countDown()
      assertTrue("Password was not supplied during the reasonable period of time",
                 usernameSuppliedWaiter.await(TIMEOUT.toLong(), TimeUnit.SECONDS))
      return username
    }


    internal fun supplyPassword(password: String) {
      this.password = password
      passwordSuppliedWaiter.countDown()
    }

    internal fun supplyUsername(username: String) {
      this.username = username
      usernameSuppliedWaiter.countDown()
    }

    internal fun waitUntilPasswordIsAsked() {
      assertTrue("Password was not asked during the reasonable period of time",
                 passwordAskedWaiter.await(TIMEOUT.toLong(), TimeUnit.SECONDS))
    }

    internal fun waitUntilUsernameIsAsked() {
      assertTrue("Username was not asked during the reasonable period of time",
                 usernameAskedWaiter.await(TIMEOUT.toLong(), TimeUnit.SECONDS))
    }

    override fun saveAuthData() {}

    override fun forgetPassword() {}

    override fun wasCancelled(): Boolean {
      return false
    }

    override fun wasRequested(): Boolean {
      return wasPasswordAsked() || wasUsernameAsked()
    }

    internal fun wasPasswordAsked(): Boolean {
      return passwordAsked
    }

    internal fun wasUsernameAsked(): Boolean {
      return usernameAsked
    }
  }

  companion object {
    private const val PROJECT_NAME = "GitRemoteTest"
    private const val USERNAME = "x-oauth-basic"
  }
}