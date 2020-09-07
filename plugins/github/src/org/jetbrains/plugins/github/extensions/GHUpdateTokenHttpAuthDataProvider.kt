// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import org.jetbrains.plugins.github.authentication.GHAccountAuthData
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE
import java.awt.Component

private val authenticationManager get() = GithubAuthenticationManager.getInstance()

internal class GHUpdateTokenHttpAuthDataProvider(
  private val project: Project,
  private val account: GithubAccount
) : InteractiveGitHttpAuthDataProvider {

  @RequiresEdt
  override fun getAuthData(parentComponent: Component?): AuthData? {
    if (!authenticationManager.requestReLogin(account, project, parentComponent)) return null
    val token = authenticationManager.getTokenForAccount(account) ?: return null

    return GHAccountAuthData(account, GIT_AUTH_PASSWORD_SUBSTITUTE, token)
  }
}