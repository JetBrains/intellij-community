// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.GitHttpAuthDataProvider
import org.jetbrains.plugins.github.api.GithubServerPath.DEFAULT_SERVER
import org.jetbrains.plugins.github.extensions.GHHttpAuthDataProvider.Companion.getGitAuthenticationAccounts

private class GHComHttpAuthDataProvider : GitHttpAuthDataProvider {
  override fun isSilent(): Boolean = false

  override fun getAuthData(project: Project, url: String, login: String): AuthData? {
    if (!DEFAULT_SERVER.matches(url)) return null

    return getAuthDataOrCancel(project, url, login)
  }

  override fun getAuthData(project: Project, url: String): AuthData? {
    if (!DEFAULT_SERVER.matches(url)) return null

    return getAuthDataOrCancel(project, url, null)
  }
}

private fun getAuthDataOrCancel(project: Project, url: String, login: String?): AuthData {
  val accounts = getGitAuthenticationAccounts(project, url, login)
  val provider =
    if (accounts.isEmpty()) GHCreateAccountHttpAuthDataProvider(project, DEFAULT_SERVER, login)
    else GHSelectAccountHttpAuthDataProvider(project, accounts)
  val authData = invokeAndWaitIfNeeded { provider.getAuthData(null) }

  return authData ?: throw ProcessCanceledException()
}