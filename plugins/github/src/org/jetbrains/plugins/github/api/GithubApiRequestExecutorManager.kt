// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import javax.swing.JComponent

/**
 * Allows to acquire API executor without exposing the auth token to external code
 */
class GithubApiRequestExecutorManager(private val authenticationManager: GithubAuthenticationManager,
                                      private val requestExecutorFactory: GithubApiRequestExecutor.Factory) {
  @CalledInAwt
  fun getExecutor(account: GithubAccount, project: Project): GithubApiRequestExecutor? {
    return authenticationManager.getOrRequestTokenForAccount(account, project = project)
             ?.let(requestExecutorFactory::create)
  }

  @CalledInAwt
  fun getExecutor(account: GithubAccount, parentComponent: JComponent): GithubApiRequestExecutor? {
    return authenticationManager.getOrRequestTokenForAccount(account, parentComponent = parentComponent)
             ?.let(requestExecutorFactory::create)
  }

  @CalledInAwt
  @Throws(GithubMissingTokenException::class)
  fun getExecutor(account: GithubAccount): GithubApiRequestExecutor {
    return authenticationManager.getTokenForAccount(account)?.let(requestExecutorFactory::create)
           ?: throw GithubMissingTokenException(account)
  }

  companion object {
    @JvmStatic
    fun getInstance(): GithubApiRequestExecutorManager = service()
  }
}