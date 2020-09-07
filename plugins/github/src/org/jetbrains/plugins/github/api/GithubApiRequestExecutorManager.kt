// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import java.awt.Component

/**
 * Allows to acquire API executor without exposing the auth token to external code
 */
class GithubApiRequestExecutorManager {
  private val executors = mutableMapOf<GithubAccount, GithubApiRequestExecutor.WithTokenAuth>()

  companion object {
    @JvmStatic
    fun getInstance(): GithubApiRequestExecutorManager = service()
  }

  internal fun tokenChanged(account: GithubAccount) {
    val token = service<GithubAccountManager>().getTokenForAccount(account)
    if (token == null) executors.remove(account)
    else executors[account]?.token = token
  }

  @RequiresEdt
  fun getExecutor(account: GithubAccount, project: Project): GithubApiRequestExecutor.WithTokenAuth? {
    return getOrTryToCreateExecutor(account) { GithubAuthenticationManager.getInstance().requestNewToken(account, project) }
  }

  @RequiresEdt
  fun getExecutor(account: GithubAccount, parentComponent: Component): GithubApiRequestExecutor.WithTokenAuth? {
    return getOrTryToCreateExecutor(account) { GithubAuthenticationManager.getInstance().requestNewToken(account, null, parentComponent) }
  }

  @RequiresEdt
  @Throws(GithubMissingTokenException::class)
  fun getExecutor(account: GithubAccount): GithubApiRequestExecutor.WithTokenAuth {
    return getOrTryToCreateExecutor(account) { throw GithubMissingTokenException(account) }!!
  }

  private fun getOrTryToCreateExecutor(account: GithubAccount,
                                       missingTokenHandler: () -> String?): GithubApiRequestExecutor.WithTokenAuth? {

    return executors.getOrPut(account) {
      (GithubAuthenticationManager.getInstance().getTokenForAccount(account) ?: missingTokenHandler())
        ?.let(GithubApiRequestExecutor.Factory.getInstance()::create) ?: return null
    }
  }
}