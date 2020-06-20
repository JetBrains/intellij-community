// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.dvcs.hosting.RepositoryListLoader
import com.intellij.dvcs.hosting.RepositoryListLoadingException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import git4idea.remote.GitRepositoryHostingService
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader.loadAll
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.extensions.GithubHttpAuthDataProvider.Companion.getGitAuthenticationAccounts
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubUtil
import java.awt.Component

internal class GithubRepositoryHostingService : GitRepositoryHostingService() {
  override fun getServiceDisplayName(): String = GithubUtil.SERVICE_DISPLAY_NAME

  override fun getRepositoryListLoader(project: Project): RepositoryListLoader = GHRepositoryListLoader(project)

  @CalledInBackground
  override fun getInteractiveAuthDataProvider(project: Project, url: String): InteractiveGitHttpAuthDataProvider? =
    getProvider(project, url, null)

  @CalledInBackground
  override fun getInteractiveAuthDataProvider(project: Project, url: String, login: String): InteractiveGitHttpAuthDataProvider? =
    getProvider(project, url, login)

  private fun getProvider(project: Project, url: String, login: String?): InteractiveGitHttpAuthDataProvider? {
    val accounts = getGitAuthenticationAccounts(project, url, login)

    return if (accounts.isNotEmpty()) InteractiveSelectGithubAccountHttpAuthDataProvider(project, accounts) else null
  }
}

private class GHRepositoryListLoader(private val project: Project) : RepositoryListLoader {
  private val authenticationManager get() = GithubAuthenticationManager.getInstance()
  private val executorManager get() = GithubApiRequestExecutorManager.getInstance()
  private val gitHelper get() = GithubGitHelper.getInstance()

  private val executors = mutableMapOf<GithubAccount, GithubApiRequestExecutor>()

  override fun isEnabled(): Boolean {
    authenticationManager.getAccounts().forEach { account ->
      try {
        executors[account] = executorManager.getExecutor(account)
      }
      catch (ignored: GithubMissingTokenException) {
      }
    }
    return executors.isNotEmpty()
  }

  override fun enable(parentComponent: Component?): Boolean {
    if (!GithubAccountsMigrationHelper.getInstance().migrate(project, parentComponent)) return false
    if (!authenticationManager.ensureHasAccounts(project, parentComponent)) return false

    var atLeastOneHasToken = false
    for (account in authenticationManager.getAccounts()) {
      val executor = executorManager.getExecutor(account, project) ?: continue
      executors[account] = executor
      atLeastOneHasToken = true
    }
    return atLeastOneHasToken
  }

  override fun getAvailableRepositoriesFromMultipleSources(progressIndicator: ProgressIndicator): RepositoryListLoader.Result {
    val urls = mutableListOf<String>()
    val exceptions = mutableListOf<RepositoryListLoadingException>()

    executors.forEach { (account, executor) ->
      try {
        val associatedRepos = account.loadAssociatedRepos(executor, progressIndicator)
        // We already can return something useful from getUserRepos, so let's ignore errors.
        // One of this may not exist in GitHub enterprise
        val watchedRepos = account.loadWatchedReposSkipErrors(executor, progressIndicator)

        urls.addAll(
          (associatedRepos + watchedRepos)
            .sortedWith(compareBy({ repo -> repo.userName }, { repo -> repo.name }))
            .map { repo -> gitHelper.getRemoteUrl(account.server, repo.userName, repo.name) }
        )
      }
      catch (e: Exception) {
        exceptions.add(RepositoryListLoadingException("Cannot load repositories from GitHub", e))
      }
    }

    return RepositoryListLoader.Result(urls, exceptions)
  }
}

private fun GithubAccount.loadAssociatedRepos(executor: GithubApiRequestExecutor, indicator: ProgressIndicator): List<GithubRepo> =
  loadAll(executor, indicator, GithubApiRequests.CurrentUser.Repos.pages(server))

private fun GithubAccount.loadWatchedReposSkipErrors(executor: GithubApiRequestExecutor, indicator: ProgressIndicator): List<GithubRepo> =
  try {
    loadAll(executor, indicator, GithubApiRequests.CurrentUser.RepoSubs.pages(server))
  }
  catch (e: GithubAuthenticationException) {
    emptyList()
  }
  catch (e: GithubStatusCodeException) {
    emptyList()
  }