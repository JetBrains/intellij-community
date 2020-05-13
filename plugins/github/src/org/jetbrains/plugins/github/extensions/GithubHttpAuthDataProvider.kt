// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.GitHttpAuthDataProvider
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider

private val LOG = logger<GithubHttpAuthDataProvider>()

class GithubHttpAuthDataProvider : GitHttpAuthDataProvider {
  override fun getAuthData(project: Project, url: String): GithubAccountAuthData? {
    return getSuitableAccounts(project, url, null).singleOrNull()?.let { account ->
      try {
        val token = GithubAuthenticationManager.getInstance().getTokenForAccount(account) ?: return null
        val username = service<GithubAccountInformationProvider>().getInformation(GithubApiRequestExecutor.Factory.getInstance().create(token),
                                                                 DumbProgressIndicator(),
                                                                 account).login
        GithubAccountAuthData(account, username, token)
      }
      catch (e: Exception) {
        if (e !is ProcessCanceledException) LOG.info("Cannot load username for $account", e)
        null
      }
    }
  }

  override fun isSilent(): Boolean = true

  override fun getAuthData(project: Project, url: String, login: String): GithubAccountAuthData? {
    return getSuitableAccounts(project, url, login).singleOrNull()?.let { account ->
      return GithubAuthenticationManager.getInstance().getTokenForAccount(account)?.let { GithubAccountAuthData(account, login, it) }
    }
  }

  override fun forgetPassword(project: Project, url: String, authData: AuthData) {
    if (authData is GithubAccountAuthData) {
      project.service<GithubAccountGitAuthenticationFailureManager>().ignoreAccount(url, authData.account)
    }
  }

  fun getSuitableAccounts(project: Project, url: String, login: String?): Set<GithubAccount> {
    val authenticationFailureManager = project.service<GithubAccountGitAuthenticationFailureManager>()
    val authenticationManager = GithubAuthenticationManager.getInstance()
    var potentialAccounts = authenticationManager.getAccounts()
      .filter { it.server.matches(url) }
      .filter { !authenticationFailureManager.isAccountIgnored(url, it) }

    if (login != null) {
      potentialAccounts = potentialAccounts.filter {
        try {
          service<GithubAccountInformationProvider>().getInformation(GithubApiRequestExecutorManager.getInstance().getExecutor(it),
                                                    DumbProgressIndicator(),
                                                    it).login == login
        }
        catch (e: Exception) {
          if (e !is ProcessCanceledException) LOG.info("Cannot load username for $it", e)
          false
        }
      }
    }

    val defaultAccount = authenticationManager.getDefaultAccount(project)
    if (defaultAccount != null && potentialAccounts.contains(defaultAccount)) return setOf(defaultAccount)
    return potentialAccounts.toSet()
  }

  class GithubAccountAuthData(val account: GithubAccount,
                              login: String,
                              password: String) : AuthData(login, password)
}