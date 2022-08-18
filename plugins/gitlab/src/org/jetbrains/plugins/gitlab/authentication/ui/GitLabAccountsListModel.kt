// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsListModel
import com.intellij.collaboration.auth.ui.AccountsListModelBase
import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import javax.swing.JComponent

internal class GitLabAccountsListModel(private val project: Project)
  : AccountsListModelBase<GitLabAccount, String>(),
    AccountsListModel.WithDefault<GitLabAccount, String> {

  override var defaultAccount: GitLabAccount? = null

  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    // ignoring the point since we know there will be a simple dialog for now
    val (account, token) = GitLabLoginUtil.logInViaToken(project, parentComponent, ::isAccountUnique) ?: return
    add(account, token)
  }

  override fun editAccount(parentComponent: JComponent, account: GitLabAccount) {
    val token = GitLabLoginUtil.updateToken(project, parentComponent, account, ::isAccountUnique) ?: return
    update(account, token)
  }

  private fun isAccountUnique(serverPath: GitLabServerPath, username: String) =
    accounts.none { it.server == serverPath || it.name == username }
}