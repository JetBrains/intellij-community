// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.remote.GitHttpAuthDataProvider
import git4idea.remote.hosting.GitHostingUrlUtil.match
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubServerPath.DEFAULT_SERVER
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager

private class GHComHttpAuthDataProvider : GitHttpAuthDataProvider {
  override fun isSilent(): Boolean = false

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String, login: String): AuthData? {
    if (!match(DEFAULT_SERVER.toURI(), url)) return null

    return runBlocking {
      getAuthDataOrCancel(project, url, login)
    }
  }

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String): AuthData? {
    if (!match(DEFAULT_SERVER.toURI(), url)) return null

    return runBlocking {
      getAuthDataOrCancel(project, url, null)
    }
  }
}

private suspend fun getAuthDataOrCancel(project: Project, url: String, login: String?): AuthData {
  val accountManager = service<GHAccountManager>()
  val accountsWithTokens = accountManager.accountsState.value
    .filter { match(it.server.toURI(), url) }
    .associateWith { accountManager.findCredentials(it) }

  return withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    when (accountsWithTokens.size) {
      0 -> GHAccountsUtil.requestNewAccountForServer(DEFAULT_SERVER, login, project)
      1 -> GHAccountsUtil.requestReLogin(accountsWithTokens.keys.first(), project = project)
      else -> GHSelectAccountHttpAuthDataProvider(project, accountsWithTokens).getAuthData(null)
    }
  } ?: throw ProcessCanceledException()
}