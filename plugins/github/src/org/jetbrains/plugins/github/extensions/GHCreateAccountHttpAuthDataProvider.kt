// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountAuthData
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE
import java.awt.Component

private val authenticationManager get() = GithubAuthenticationManager.getInstance()

internal class GHCreateAccountHttpAuthDataProvider(
  private val project: Project,
  private val serverPath: GithubServerPath,
  private val login: String? = null
) : InteractiveGitHttpAuthDataProvider {

  @RequiresEdt
  override fun getAuthData(parentComponent: Component?): AuthData? {
    val account = requestNewAccount(parentComponent) ?: return null
    val token = getOrRequestToken(account, project, parentComponent) ?: return null

    return GHAccountAuthData(account, login ?: GIT_AUTH_PASSWORD_SUBSTITUTE, token)
  }

  private fun requestNewAccount(parentComponent: Component?): GithubAccount? =
    if (login != null) authenticationManager.requestNewAccountForServer(serverPath, login, project, parentComponent)
    else authenticationManager.requestNewAccountForServer(serverPath, project, parentComponent)

  companion object {
    fun getOrRequestToken(account: GithubAccount, project: Project, parentComponent: Component?): String? =
      authenticationManager.getTokenForAccount(account) ?: authenticationManager.requestNewToken(account, project, parentComponent)
  }
}