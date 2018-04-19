// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.GitHttpAuthDataProvider
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.util.GithubUtil

class GithubHttpAuthDataProvider : GitHttpAuthDataProvider {
  //TODO: should ask user what account to use
  override fun getAuthData(project: Project, url: String): AuthData? {
    val authenticationManager = GithubAuthenticationManager.getInstance()

    val account = authenticationManager.getDefaultAccount(project)?.takeIf { it.server.matches(url) }
                  ?: authenticationManager.getAccounts().singleOrNull { it.server.matches(url) }

    return if (account != null) {
      AuthData(authenticationManager.getTokenForAccount(account), GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE)
    }
    else null
  }

  override fun forgetPassword(url: String) {
    //TODO: save account-url pair to ignore
  }
}