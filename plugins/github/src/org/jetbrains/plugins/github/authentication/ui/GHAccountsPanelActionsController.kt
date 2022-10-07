// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsPanelActionsController
import com.intellij.ide.DataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.GHLoginRequest
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import javax.swing.JComponent

internal class GHAccountsPanelActionsController(private val project: Project, private val model: GHAccountsListModel)
  : AccountsPanelActionsController<GithubAccount> {

  override val isAddActionWithPopup: Boolean = true

  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    val isAccountUnique: (login: String, server: GithubServerPath) -> Boolean = { name, server ->
      model.accounts.none { it.name == name && it.server.equals(server, true) }
    }

    val group = GHAccountsUtil.createAddAccountActionGroup(project, parentComponent, isAccountUnique) { server, login, token ->
      val account = GHAccountManager.createAccount(login, server)
      model.add(account, token)
    }


    val actualPoint = point ?: RelativePoint.getCenterOf(parentComponent)
    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(parentComponent),
                              JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
      .show(actualPoint)
  }

  override fun editAccount(parentComponent: JComponent, account: GithubAccount) {
    val authData = GHAccountsUtil.login(project, parentComponent,
                                        GHLoginRequest(server = account.server, login = account.name))
    if (authData == null) return

    account.name = authData.login
    model.update(authData.account, authData.token)
  }
}