// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import java.awt.Component

/**
 * Allows to acquire API executor without exposing the auth token to external code
 */
class GithubApiRequestExecutorManager internal constructor(private val accountManager: GithubAccountManager,
                                                           private val authenticationManager: GithubAuthenticationManager,
                                                           private val requestExecutorFactory: GithubApiRequestExecutor.Factory)
  : AccountTokenChangedListener {

  private val executors = mutableMapOf<GithubAccount, GithubApiRequestExecutor.WithTokenAuth>()

  init {
    ApplicationManager.getApplication().messageBus
      .connect()
      .subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, this)
  }

  override fun tokenChanged(account: GithubAccount) {
    val token = accountManager.getTokenForAccount(account)
    if (token == null) executors.remove(account)
    else executors[account]?.token = token
  }

  @CalledInAwt
  fun getExecutor(account: GithubAccount, project: Project): GithubApiRequestExecutor.WithTokenAuth? {
    return getOrTryToCreateExecutor(account) { authenticationManager.requestNewToken(account, project) }
  }

  @CalledInAwt
  fun getExecutor(account: GithubAccount, parentComponent: Component): GithubApiRequestExecutor.WithTokenAuth? {
    return getOrTryToCreateExecutor(account) { authenticationManager.requestNewToken(account, null, parentComponent) }
  }

  @CalledInAwt
  @Throws(GithubMissingTokenException::class)
  fun getExecutor(account: GithubAccount): GithubApiRequestExecutor.WithTokenAuth {
    return getOrTryToCreateExecutor(account) { throw GithubMissingTokenException(account) }!!
  }

  private fun getOrTryToCreateExecutor(account: GithubAccount,
                                       missingTokenHandler: () -> String?): GithubApiRequestExecutor.WithTokenAuth? {

    return executors.getOrPut(account) {
      (authenticationManager.getTokenForAccount(account) ?: missingTokenHandler())
        ?.let(requestExecutorFactory::create) ?: return null
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): GithubApiRequestExecutorManager = service()
  }
}