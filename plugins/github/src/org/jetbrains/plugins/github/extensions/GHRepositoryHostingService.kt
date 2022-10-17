// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.remote.GitRepositoryHostingService
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import org.jetbrains.plugins.github.extensions.GHHttpAuthDataProvider.Companion.getGitAuthenticationAccounts
import org.jetbrains.plugins.github.util.GithubUtil

internal class GHRepositoryHostingService : GitRepositoryHostingService() {
  override fun getServiceDisplayName(): String = GithubUtil.SERVICE_DISPLAY_NAME

  @RequiresBackgroundThread
  override fun getInteractiveAuthDataProvider(project: Project, url: String): InteractiveGitHttpAuthDataProvider? =
    getProvider(project, url, null)

  @RequiresBackgroundThread
  override fun getInteractiveAuthDataProvider(project: Project, url: String, login: String): InteractiveGitHttpAuthDataProvider? =
    getProvider(project, url, login)

  private fun getProvider(project: Project, url: String, login: String?): InteractiveGitHttpAuthDataProvider? {
    val accounts = getGitAuthenticationAccounts(project, url, login)

    return if (accounts.isNotEmpty()) GHSelectAccountHttpAuthDataProvider(project, accounts) else null
  }
}