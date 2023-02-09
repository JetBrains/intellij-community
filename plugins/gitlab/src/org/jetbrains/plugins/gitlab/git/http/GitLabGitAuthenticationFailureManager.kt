// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.git.http

import com.intellij.collaboration.async.disposingScope
import com.intellij.collaboration.auth.AccountUrlAuthenticationFailuresHolder
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager

@Service(Service.Level.PROJECT)
class GitLabGitAuthenticationFailureManager(project: Project) : Disposable {
  private val holder = AccountUrlAuthenticationFailuresHolder(disposingScope()) {
    service<GitLabAccountManager>()
  }.also {
    Disposer.register(this, it)
  }

  fun ignoreAccount(url: String, account: GitLabAccount) {
    holder.markFailed(account, url)
  }

  fun isAccountIgnored(url: String, account: GitLabAccount): Boolean = holder.isFailed(account, url)

  override fun dispose() = Unit
}