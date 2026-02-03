// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsPanelActionsController
import com.intellij.ide.DataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.GHLoginData
import org.jetbrains.plugins.github.authentication.GHLoginRequest
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import javax.swing.JComponent

internal class GHAccountsPanelActionsController(private val project: Project, private val model: GHAccountsListModel)
  : AccountsPanelActionsController<GithubAccount> {

  override val isAddActionWithPopup: Boolean = true

  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    val loginModel = AccountsListModelLoginModel(model)
    val group = GHAccountsUtil.createAddAccountActionGroup(loginModel, project, parentComponent, GHLoginSource.SETTINGS)


    val actualPoint = point ?: RelativePoint.getCenterOf(parentComponent)
    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(parentComponent),
                              JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
      .show(actualPoint)
  }

  override fun editAccount(parentComponent: JComponent, account: GithubAccount) {
    val loginModel = AccountsListModelLoginModel(model, account)
    GHAccountsUtil.login(loginModel,
                         GHLoginRequest(server = account.server, isServerEditable = false, loginData = GHLoginData(GHLoginSource.SETTINGS)),
                         project, parentComponent)
  }

  private class AccountsListModelLoginModel(private val model: GHAccountsListModel,
                                            private val account: GithubAccount? = null)
    : GHLoginModel {

    override fun isAccountUnique(server: GithubServerPath, login: String): Boolean =
      model.accounts.filter {
        it != account
      }.none {
        it.name == login && it.server.equals(server, true)
      }

    override suspend fun saveLogin(server: GithubServerPath, login: String, token: String) {
      withContext(Dispatchers.Main) {
        if (account == null) {
          val account = GHAccountManager.createAccount(login, server)
          model.add(account, token)
        }
        else {
          account.name = login
          model.update(account, token)
        }
      }
    }
  }
}