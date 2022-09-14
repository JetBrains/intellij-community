// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.collaboration.async.disposingMainScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import java.awt.Component

/**
 * Allows to acquire API executor without exposing the auth token to external code
 */
class GithubApiRequestExecutorManager : Disposable {
  private val executors = mutableMapOf<GithubAccount, GithubApiRequestExecutor.WithTokenAuth>()

  companion object {
    @JvmStatic
    fun getInstance(): GithubApiRequestExecutorManager = service()
  }

  init {
    disposingMainScope().launch {
      service<GHAccountManager>().accountsState.collect {
        it.forEach { (account, token) ->
          if (token == null) executors.remove(account)
          else executors[account]?.token = token
        }
      }
    }
  }

  @RequiresEdt
  fun getExecutor(account: GithubAccount, project: Project): GithubApiRequestExecutor? {
    return getOrTryToCreateExecutor(account) { GithubAuthenticationManager.getInstance().requestNewToken(account, project) }
  }

  @RequiresEdt
  fun getExecutor(account: GithubAccount, parentComponent: Component): GithubApiRequestExecutor? {
    return getOrTryToCreateExecutor(account) { GithubAuthenticationManager.getInstance().requestNewToken(account, null, parentComponent) }
  }

  @Throws(GithubMissingTokenException::class)
  fun getExecutor(account: GithubAccount): GithubApiRequestExecutor {
    return getOrTryToCreateExecutor(account) { throw GithubMissingTokenException(account) }!!
  }

  private fun getOrTryToCreateExecutor(account: GithubAccount,
                                       missingTokenHandler: () -> String?): GithubApiRequestExecutor? {

    return executors.getOrPut(account) {
      (GithubAuthenticationManager.getInstance().getTokenForAccount(account) ?: missingTokenHandler())
        ?.let(GithubApiRequestExecutor.Factory.getInstance()::create) ?: return null
    }
  }

  override fun dispose() = Unit
}