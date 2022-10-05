// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.DropDownLink
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GHOAuthLoginDialog
import org.jetbrains.plugins.github.authentication.ui.GHTokenLoginDialog
import org.jetbrains.plugins.github.authentication.ui.UniqueLoginPredicate
import org.jetbrains.plugins.github.i18n.GithubBundle
import javax.swing.JButton
import javax.swing.JComponent

object GHAccountsUtil {
  fun createAddAccountLink(project: Project, accountSelectionModel: CollectionComboBoxModel<GithubAccount>): JButton {
    val isAccountUnique: (login: String, server: GithubServerPath) -> Boolean = { name, server ->
      accountSelectionModel.items.none { it.name == name && it.server.equals(server, true) }
    }

    return DropDownLink(GithubBundle.message("accounts.add.dropdown.link")) {
      val group = createAddAccountActionGroup(project, it, isAccountUnique) { server, login, token ->
        val account = GHAccountManager.createAccount(login, server)
        service<GHAccountManager>().updateAccount(account, token)
        accountSelectionModel.add(account)
        accountSelectionModel.selectedItem = account
      }

      JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(it),
                                JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
    }
  }

  fun createAddAccountActionGroup(project: Project,
                                  parentComponent: JComponent,
                                  uniquePredicate: UniqueLoginPredicate,
                                  registerAccount: (server: GithubServerPath, login: String, token: String) -> Unit)
    : ActionGroup {

    val group = DefaultActionGroup()
    group.add(
      DumbAwareAction.create(GithubBundle.message("action.Github.Accounts.AddGHAccount.text")) {
        val dialog = GHOAuthLoginDialog(project, parentComponent, uniquePredicate)
        dialog.setServer(GithubServerPath.DEFAULT_HOST, false)

        if (dialog.showAndGet()) {
          registerAccount(dialog.server, dialog.login, dialog.token)
        }
      })

    group.add(
      DumbAwareAction.create(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
        val dialog = GHTokenLoginDialog(project, parentComponent, uniquePredicate).apply {
          title = GithubBundle.message("dialog.title.add.github.account")
          setLoginButtonText(GithubBundle.message("accounts.add.button"))
        }
        dialog.setServer(GithubServerPath.DEFAULT_HOST, false)

        if (dialog.showAndGet()) {
          registerAccount(dialog.server, dialog.login, dialog.token)
        }
      }
    )

    group.add(Separator())

    group.add(
      DumbAwareAction.create(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text")) {
        val dialog = GHTokenLoginDialog(project, parentComponent, uniquePredicate).apply {
          title = GithubBundle.message("dialog.title.add.github.account")
          setServer("", true)
          setLoginButtonText(GithubBundle.message("accounts.add.button"))
        }

        if (dialog.showAndGet()) {
          registerAccount(dialog.server, dialog.login, dialog.token)
        }
      }
    )
    return group
  }
}