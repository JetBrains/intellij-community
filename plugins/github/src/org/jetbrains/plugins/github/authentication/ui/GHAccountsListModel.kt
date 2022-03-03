// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsListModel
import com.intellij.collaboration.auth.ui.AccountsListModelBase
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHLoginRequest
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import javax.swing.JComponent

class GHAccountsListModel(private val project: Project)
  : AccountsListModelBase<GithubAccount, String>(),
    AccountsListModel.WithDefault<GithubAccount, String>,
    GHAccountsHost {

  private val actionManager = ActionManager.getInstance()

  override var defaultAccount: GithubAccount? = null

  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    val group = actionManager.getAction("Github.Accounts.AddAccount") as ActionGroup
    val popup = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group)

    val actualPoint = point ?: RelativePoint.getCenterOf(parentComponent)
    popup.setTargetComponent(parentComponent)
    JBPopupMenu.showAt(actualPoint, popup.component)
  }

  override fun editAccount(parentComponent: JComponent, account: GithubAccount) {
    val authData = GithubAuthenticationManager.getInstance().login(
      project, parentComponent,
      GHLoginRequest(server = account.server, login = account.name)
    )
    if (authData == null) return

    account.name = authData.login
    newCredentials[account] = authData.token
    notifyCredentialsChanged(account)
  }

  override fun addAccount(server: GithubServerPath, login: String, token: String) {
    val account = GHAccountManager.createAccount(login, server)
    accountsListModel.add(account)
    newCredentials[account] = token
    notifyCredentialsChanged(account)
  }

  override fun isAccountUnique(login: String, server: GithubServerPath): Boolean =
    accountsListModel.items.none { it.name == login && it.server.equals(server, true) }
}