// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.test

import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.GitHttpAuthService
import git4idea.commands.GitHttpAuthenticator
import git4idea.config.GitConfigUtil
import git4idea.repo.GitRepository
import git4idea.test.GitHttpAuthTestService
import git4idea.test.git
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import org.jetbrains.plugins.github.util.GithubUtil

abstract class GithubGitRepoTest : GithubTest() {

  private lateinit var ghRepositoryManager: GHHostedRepositoriesManager

  protected lateinit var repository: GitRepository

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    ghRepositoryManager = project.service()
  }

  override fun setCurrentAccount(accountData: AccountData?) {
    super.setCurrentAccount(accountData)
    val gitHttpAuthService = service<GitHttpAuthService>() as GitHttpAuthTestService
    if (accountData == null) {
      gitHttpAuthService.cleanup()
      return
    }

    gitHttpAuthService.register(object : GitHttpAuthenticator {
      override fun askPassword(url: String) = GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE
      override fun askUsername(url: String) = accountData.token
      override fun saveAuthData() {}
      override fun forgetPassword() {}
      override fun wasCancelled() = false
      override fun wasRequested(): Boolean = true
    })
  }

  protected fun findGitRepo() {
    repository = repositoryManager.getRepositoryForFileQuick(projectRoot)!!
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

  protected fun checkGitExists() {
    assertNotNull("Git repository does not exist", repository)
  }

  protected fun checkRemoteConfigured() {
    assertNotNull(repository)
    val mappings = runBlocking {
      ghRepositoryManager.knownRepositoriesFlow.first()
    }
    assertTrue("GitHub remote is not configured, current mappings: $mappings", mappings.any {
      it.remote.repository == repository
    })
  }

  protected fun checkLastCommitPushed() {
    assertNotNull(repository)

    val hash = repository.git("log -1 --pretty=%h")
    val ans = repository.git("branch --contains $hash -a")
    assertTrue(ans.contains("remotes/origin"))
  }
}