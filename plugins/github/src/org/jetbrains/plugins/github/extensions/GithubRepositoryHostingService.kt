// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.dvcs.hosting.RepositoryListLoader
import com.intellij.dvcs.hosting.RepositoryListLoadingException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import git4idea.remote.GitRepositoryHostingService
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import one.util.streamex.StreamEx
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
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
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream

internal class GithubRepositoryHostingService : GitRepositoryHostingService() {
  private val authenticationManager get() = GithubAuthenticationManager.getInstance()
  private val executorManager get() = GithubApiRequestExecutorManager.getInstance()
  private val gitHelper get() = GithubGitHelper.getInstance()

  override fun getServiceDisplayName(): String = GithubUtil.SERVICE_DISPLAY_NAME

  override fun getRepositoryListLoader(project: Project): RepositoryListLoader {
    return object : RepositoryListLoader {
      private val myExecutors = mutableMapOf<GithubAccount, GithubApiRequestExecutor>()

      override fun isEnabled(): Boolean {
        for (account in authenticationManager.getAccounts()) {
          try {
            myExecutors[account] = executorManager.getExecutor(account)
          }
          catch (e: GithubMissingTokenException) {
            // skip
          }
        }
        return !myExecutors.isEmpty()
      }

      override fun enable(parentComponent: Component?): Boolean {
        if (!GithubAccountsMigrationHelper.getInstance().migrate(project, parentComponent)) return false
        if (!authenticationManager.ensureHasAccounts(project, parentComponent)) return false
        var atLeastOneHasToken = false
        for (account in authenticationManager.getAccounts()) {
          val executor = executorManager.getExecutor(account, project) ?: continue
          myExecutors[account] = executor
          atLeastOneHasToken = true
        }
        return atLeastOneHasToken
      }

      override fun getAvailableRepositoriesFromMultipleSources(progressIndicator: ProgressIndicator): RepositoryListLoader.Result {
        val urls = mutableListOf<String>()
        val exceptions = mutableListOf<RepositoryListLoadingException>()

        for ((account, executor) in myExecutors) {
          val server = account.server
          try {
            val streamAssociated = loadAll(executor, progressIndicator, GithubApiRequests.CurrentUser.Repos.pages(server)).stream()
            var streamWatched: Stream<GithubRepo> = StreamEx.empty()
            try {
              streamWatched = loadAll(executor, progressIndicator, GithubApiRequests.CurrentUser.RepoSubs.pages(server)).stream()
            }
            catch (ignore: GithubAuthenticationException) {
              // We already can return something useful from getUserRepos, so let's ignore errors.
              // One of this may not exist in GitHub enterprise
            }
            catch (ignore: GithubStatusCodeException) {
            }
            urls.addAll(
              Stream.concat(streamAssociated, streamWatched)
                .sorted(Comparator.comparing { repo: GithubRepo -> repo.userName }.thenComparing { repo: GithubRepo -> repo.name })
                .map(Function { repo: GithubRepo -> gitHelper.getRemoteUrl(server, repo.userName, repo.name) })
                .collect(Collectors.toList())
            )
          }
          catch (e: Exception) {
            exceptions.add(RepositoryListLoadingException("Cannot load repositories from GitHub", e))
          }
        }
        return RepositoryListLoader.Result(urls, exceptions)
      }
    }
  }

  @CalledInBackground
  override fun getInteractiveAuthDataProvider(project: Project, url: String): InteractiveGitHttpAuthDataProvider? =
    getProvider(project, url, null)

  @CalledInBackground
  override fun getInteractiveAuthDataProvider(project: Project, url: String, login: String): InteractiveGitHttpAuthDataProvider? =
    getProvider(project, url, login)

  private fun getProvider(project: Project, url: String, login: String?): InteractiveGitHttpAuthDataProvider? {
    val potentialAccounts = getGitAuthenticationAccounts(project, url, login)
    if (!potentialAccounts.isEmpty()) {
      return InteractiveSelectGithubAccountHttpAuthDataProvider(project, potentialAccounts, authenticationManager)
    }
    if (GithubServerPath.DEFAULT_SERVER.matches(url)) {
      return InteractiveCreateGithubAccountHttpAuthDataProvider(project, authenticationManager, GithubServerPath.DEFAULT_SERVER, login)
    }
    return null
  }
}