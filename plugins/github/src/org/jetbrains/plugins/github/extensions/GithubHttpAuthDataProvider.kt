// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.GitHttpAuthDataProvider
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import java.io.IOException

class GithubHttpAuthDataProvider(private val authenticationManager: GithubAuthenticationManager,
                                 private val accountInformationProvider: GithubAccountInformationProvider,
                                 private val authenticationFailureManager: GithubAccountGitAuthenticationFailureManager) : GitHttpAuthDataProvider {
  private val LOG = logger<GithubHttpAuthDataProvider>()

  override fun getAuthData(project: Project, url: String): GithubAccountAuthData? {
    return getSuitableAccounts(project, url, null).singleOrNull()?.let {
      try {
        val username = accountInformationProvider.getAccountUsername(DumbProgressIndicator(), it)
        GithubAccountAuthData(it, username, authenticationManager.getTokenForAccount(it))
      }
      catch (e: IOException) {
        LOG.info("Cannot load username for $it", e)
        null
      }
    }
  }

  override fun getAuthData(project: Project, url: String, login: String): GithubAccountAuthData? {
    return getSuitableAccounts(project, url, login).singleOrNull()?.let {
      try {
        GithubAccountAuthData(it, login, authenticationManager.getTokenForAccount(it))
      }
      catch (e: GithubAuthenticationException) {
        LOG.info(e)
        null
      }
    }
  }

  override fun forgetPassword(url: String, authData: AuthData) {
    if (authData is GithubAccountAuthData) {
      authenticationFailureManager.ignoreAccount(url, authData.account)
    }
  }

  fun getSuitableAccounts(project: Project, url: String, login: String?): Set<GithubAccount> {
    var potentialAccounts = authenticationManager.getAccounts()
      .filter { it.server.matches(url) }
      .filter { !authenticationFailureManager.isAccountIgnored(url, it) }

    if (login != null) {
      potentialAccounts = potentialAccounts.filter {
        try {
          accountInformationProvider.getAccountUsername(DumbProgressIndicator(), it) == login
        }
        catch (e: IOException) {
          LOG.info("Cannot load username for $it", e)
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