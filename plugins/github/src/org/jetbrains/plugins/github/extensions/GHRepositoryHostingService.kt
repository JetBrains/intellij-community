// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.remote.GitRepositoryHostingService
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.github.util.GithubUtil

internal class GHRepositoryHostingService : GitRepositoryHostingService() {
  override fun getServiceDisplayName(): String = GithubUtil.SERVICE_DISPLAY_NAME

  @RequiresBackgroundThread
  override fun getInteractiveAuthDataProvider(project: Project, url: String)
    : InteractiveGitHttpAuthDataProvider? = runBlocking {
    GHHttpAuthDataProvider.getAccountsWithTokens(project, url).takeIf { it.isNotEmpty() }?.let {
      GHSelectAccountHttpAuthDataProvider(project, it)
    }
  }

  @RequiresBackgroundThread
  override fun getInteractiveAuthDataProvider(project: Project, url: String, login: String)
    : InteractiveGitHttpAuthDataProvider? = runBlocking {
    GHHttpAuthDataProvider.getAccountsWithTokens(project, url).mapNotNull { (acc, token) ->
      if (token == null) return@mapNotNull null
      val details = GHHttpAuthDataProvider.getAccountDetails(acc, token) ?: return@mapNotNull null
      if (details.login != login) return@mapNotNull null
      acc to token
    }.takeIf { it.isNotEmpty() }?.let {
      GHSelectAccountHttpAuthDataProvider(project, it.toMap())
    }
  }
}