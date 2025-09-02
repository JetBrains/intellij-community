// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.remote.GitHttpAuthDataProvider
import git4idea.remote.hosting.GitHostingUrlUtil.matchHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubServerPath.DEFAULT_SERVER
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager

private class GHComHttpAuthDataProvider : GitHttpAuthDataProvider {
  override fun isSilent(): Boolean = false

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String, login: String): AuthData? {
    if (!matchHost(DEFAULT_SERVER.toURI(), url)) return null

    return runBlockingMaybeCancellable {
      getAuthDataOrCancel(project, url, login)
    }
  }

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String): AuthData? {
    if (!matchHost(DEFAULT_SERVER.toURI(), url)) return null

    return runBlockingMaybeCancellable {
      getAuthDataOrCancel(project, url, null)
    }
  }
}

private suspend fun getAuthDataOrCancel(project: Project, url: String, login: String?): AuthData {
  val accountManager = service<GHAccountManager>()
  val accountsWithTokens = accountManager.accountsState.value
    .filter { matchHost(it.server.toURI(), url) }
    .associateWith { accountManager.findCredentials(it) }

  return withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    when (accountsWithTokens.size) {
      0 -> GHAccountsUtil.requestNewAccount(DEFAULT_SERVER, login, project, loginSource = GHLoginSource.GIT)
      1 -> GHAccountsUtil.requestReLogin(accountsWithTokens.keys.first(), project = project, loginSource = GHLoginSource.GIT)
      else -> GHSelectAccountHttpAuthDataProvider(project, accountsWithTokens).getAuthData(null)
    }
  } ?: throw ProcessCanceledException()
}