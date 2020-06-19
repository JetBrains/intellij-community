// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GithubUtil
import java.awt.Component

internal class InteractiveCreateGithubAccountHttpAuthDataProvider(
  private val project: Project,
  private val serverPath: GithubServerPath,
  private val login: String? = null
) : InteractiveGitHttpAuthDataProvider {

  private val authenticationManager get() = GithubAuthenticationManager.getInstance()

  @CalledInAwt
  override fun getAuthData(parentComponent: Component?): AuthData? {
    if (login == null) {
      val account = authenticationManager.requestNewAccountForServer(serverPath, project, parentComponent)
                    ?: return null
      val token = getToken(account, parentComponent) ?: return null
      return GHAccountAuthData(account, GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE, token)
    }
    else {
      val account = authenticationManager.requestNewAccountForServer(serverPath, login, project, parentComponent)
                    ?: return null
      val token = getToken(account, parentComponent) ?: return null
      return GHAccountAuthData(account, login, token)
    }
  }

  private fun getToken(account: GithubAccount, parentComponent: Component?) =
    authenticationManager.getTokenForAccount(account) ?: authenticationManager.requestNewToken(account, project, parentComponent)
}