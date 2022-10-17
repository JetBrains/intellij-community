// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsPanelActionsController
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.github.authentication.GHLoginRequest
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import javax.swing.JComponent

internal class GHAccountsPanelActionsController(private val project: Project, private val host: GHAccountsHost)
  : AccountsPanelActionsController<GithubAccount> {

  private val actionManager = ActionManager.getInstance()

  override val isAddActionWithPopup: Boolean = true

  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    val group = actionManager.getAction("Github.Accounts.AddAccount") as ActionGroup
    
    val actualPoint = point ?: RelativePoint.getCenterOf(parentComponent)
    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(parentComponent),
                              JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
      .show(actualPoint)
  }

  override fun editAccount(parentComponent: JComponent, account: GithubAccount) {
    val authData = GithubAuthenticationManager.getInstance().login(
      project, parentComponent,
      GHLoginRequest(server = account.server, login = account.name)
    )
    if (authData == null) return

    account.name = authData.login
    host.updateAccount(authData.account, authData.token)
  }
}