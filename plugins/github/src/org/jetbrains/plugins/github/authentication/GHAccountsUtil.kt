// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.DropDownLink
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.DialogManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.BaseLoginDialog
import org.jetbrains.plugins.github.authentication.ui.GHOAuthLoginDialog
import org.jetbrains.plugins.github.authentication.ui.GHTokenLoginDialog
import org.jetbrains.plugins.github.authentication.ui.UniqueLoginPredicate
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Component
import javax.swing.JButton
import javax.swing.JComponent

private val accountManager: GHAccountManager get() = service()

object GHAccountsUtil {
  @JvmStatic
  val accounts: Set<GithubAccount>
    get() = accountManager.accountsState.value

  @JvmStatic
  fun getDefaultAccount(project: Project): GithubAccount? =
    project.service<GithubProjectDefaultAccountHolder>().account

  @JvmStatic
  fun setDefaultAccount(project: Project, account: GithubAccount?) {
    project.service<GithubProjectDefaultAccountHolder>().account = account
  }

  @JvmStatic
  fun getSingleOrDefaultAccount(project: Project): GithubAccount? =
    getDefaultAccount(project) ?: accounts.singleOrNull()

  fun createAddAccountLink(project: Project, accountSelectionModel: CollectionComboBoxModel<GithubAccount>): JButton {
    val isAccountUnique: (login: String, server: GithubServerPath) -> Boolean = { name, server ->
      accountSelectionModel.items.none { it.name == name && it.server.equals(server, true) }
    }

    return DropDownLink(GithubBundle.message("accounts.add.dropdown.link")) {
      val group = createAddAccountActionGroup(project, it, isAccountUnique) { server, login, token ->
        val account = GHAccountManager.createAccount(login, server)
        accountManager.updateAccount(account, token)
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

  @RequiresEdt
  @JvmOverloads
  @JvmStatic
  internal fun requestNewToken(
    account: GithubAccount,
    project: Project?,
    parentComponent: Component? = null
  ): String? =
    login(
      project, parentComponent,
      GHLoginRequest(
        text = GithubBundle.message("account.token.missing.for", account),
        server = account.server, login = account.name
      )
    )?.updateAccount(account)

  @RequiresEdt
  @JvmOverloads
  @JvmStatic
  fun requestReLogin(
    account: GithubAccount,
    project: Project?,
    parentComponent: Component? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED
  ): GHAccountAuthData? =
    login(
      project, parentComponent,
      GHLoginRequest(
        server = account.server, login = account.name, authType = authType
      )
    )?.apply {
      updateAccount(account)
    }

  @RequiresEdt
  @JvmOverloads
  @JvmStatic
  fun requestNewAccountForServer(
    server: GithubServerPath,
    login: String? = null,
    project: Project?,
    parentComponent: Component? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED
  ): GHAccountAuthData? =
    login(
      project, parentComponent,
      GHLoginRequest(server = server, login = login, isLoginEditable = login != null, isCheckLoginUnique = true, authType = authType)
    )?.apply {
      registerAccount()
    }

  @RequiresEdt
  @JvmStatic
  internal fun login(project: Project?, parentComponent: Component?, request: GHLoginRequest): GHAccountAuthData? {
    return when (request.authType) {
      AuthorizationType.OAUTH -> request.loginWithOAuth(project, parentComponent)
      AuthorizationType.TOKEN -> request.loginWithToken(project, parentComponent)
      AuthorizationType.UNDEFINED -> request.loginWithOAuthOrToken(project, parentComponent)
    }
  }
}

class GHAccountAuthData(val account: GithubAccount, login: String, token: String) : AuthData(login, token) {
  val server: GithubServerPath get() = account.server
  val token: String get() = password!!
}

internal fun GHAccountAuthData.registerAccount(): GithubAccount {
  val account = GHAccountManager.createAccount(login, server)
  accountManager.updateAccount(account, token)
  return account
}

internal fun GHAccountAuthData.updateAccount(account: GithubAccount): String {
  account.name = login
  accountManager.updateAccount(account, token)
  return token
}

internal class GHLoginRequest(
  @NlsContexts.DialogMessage
  val text: String? = null,
  val error: Throwable? = null,

  val server: GithubServerPath? = null,
  val isServerEditable: Boolean = server == null,

  val login: String? = null,
  val isLoginEditable: Boolean = true,
  val isCheckLoginUnique: Boolean = false,

  val authType: AuthorizationType = AuthorizationType.UNDEFINED
)

private fun GHLoginRequest.loginWithToken(project: Project?, parentComponent: Component?): GHAccountAuthData? {
  val dialog = GHTokenLoginDialog(project, parentComponent, isLoginUniqueChecker)
  configure(dialog)

  return dialog.getAuthData()
}

private fun GHLoginRequest.loginWithOAuth(project: Project?, parentComponent: Component?): GHAccountAuthData? {
  val dialog = GHOAuthLoginDialog(project, parentComponent, isLoginUniqueChecker)
  configure(dialog)

  return dialog.getAuthData()
}

private fun GHLoginRequest.loginWithOAuthOrToken(project: Project?, parentComponent: Component?): GHAccountAuthData? =
  when (promptOAuthLogin(this, project, parentComponent)) {
    Messages.YES -> loginWithOAuth(project, parentComponent)
    Messages.NO -> loginWithToken(project, parentComponent)
    else -> null
  }

private val GHLoginRequest.isLoginUniqueChecker: UniqueLoginPredicate
  get() = { login, server ->
    !isCheckLoginUnique ||
    GHAccountsUtil.accounts.none {
      it.name == login && it.server.equals(server, true)
    }
  }

private fun GHLoginRequest.configure(dialog: BaseLoginDialog) {
  error?.let { dialog.setError(it) }
  server?.let { dialog.setServer(it.toString(), isServerEditable) }
  login?.let { dialog.setLogin(it, isLoginEditable) }
}

private fun BaseLoginDialog.getAuthData(): GHAccountAuthData? {
  DialogManager.show(this)
  return if (isOK) GHAccountAuthData(GHAccountManager.createAccount(login, server), login, token) else null
}

private fun promptOAuthLogin(request: GHLoginRequest, project: Project?, parentComponent: Component?): Int {
  val builder = MessageDialogBuilder.yesNoCancel(title = GithubBundle.message("login.to.github"),
                                                 message = request.text ?: GithubBundle.message("dialog.message.login.to.continue"))
    .yesText(GithubBundle.message("login.via.github.action"))
    .noText(GithubBundle.message("button.use.token"))
    .icon(Messages.getWarningIcon())
  if (parentComponent != null) {
    return builder.show(parentComponent)
  }
  else {
    return builder.show(project)
  }
}