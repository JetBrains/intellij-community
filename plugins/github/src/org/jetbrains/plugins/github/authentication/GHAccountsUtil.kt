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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GHLoginDialog
import org.jetbrains.plugins.github.authentication.ui.GHLoginModel
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
    val model = object : GHLoginModel {
      override fun isAccountUnique(server: GithubServerPath, login: String): Boolean =
        accountSelectionModel.items.none { it.name == login && it.server.equals(server, true) }

      override suspend fun saveLogin(server: GithubServerPath, login: String, token: String) {
        val account = GHAccountManager.createAccount(login, server)
        accountManager.updateAccount(account, token)
        withContext(Dispatchers.Main.immediate) {
          accountSelectionModel.add(account)
          accountSelectionModel.selectedItem = account
        }
      }
    }

    return DropDownLink(GithubBundle.message("accounts.add.dropdown.link")) {
      val group = createAddAccountActionGroup(model, project, it)
      JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(it),
                                JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
    }
  }

  internal fun createAddAccountActionGroup(model: GHLoginModel, project: Project, parentComponent: JComponent): ActionGroup {
    val group = DefaultActionGroup()
    group.add(
      DumbAwareAction.create(GithubBundle.message("action.Github.Accounts.AddGHAccount.text")) {
        GHLoginDialog.OAuth(model, project, parentComponent).apply {
          setServer(GithubServerPath.DEFAULT_HOST, false)
          showAndGet()
        }
      })

    group.add(
      DumbAwareAction.create(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
        GHLoginDialog.Token(model, project, parentComponent).apply {
          title = GithubBundle.message("dialog.title.add.github.account")
          setLoginButtonText(GithubBundle.message("accounts.add.button"))
          setServer(GithubServerPath.DEFAULT_HOST, false)
          showAndGet()
        }
      }
    )

    group.add(Separator())

    group.add(
      DumbAwareAction.create(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text")) {
        GHLoginDialog.Token(model, project, parentComponent).apply {
          title = GithubBundle.message("dialog.title.add.github.account")
          setServer("", true)
          setLoginButtonText(GithubBundle.message("accounts.add.button"))
          showAndGet()
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
  ): String? {
    val model = AccountManagerLoginModel(account)
    login(
      model,
      GHLoginRequest(
        text = GithubBundle.message("account.token.missing.for", account),
        server = account.server, login = account.name
      ),
      project, parentComponent,
    )
    return model.authData?.token
  }

  @RequiresEdt
  @JvmOverloads
  @JvmStatic
  fun requestReLogin(
    account: GithubAccount,
    project: Project?,
    parentComponent: Component? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED
  ): GHAccountAuthData? {
    val model = AccountManagerLoginModel(account)
    login(
      model, GHLoginRequest(server = account.server, login = account.name, authType = authType),
      project, parentComponent)
    return model.authData
  }

  @RequiresEdt
  @JvmOverloads
  @JvmStatic
  fun requestNewAccount(
    server: GithubServerPath? = null,
    login: String? = null,
    project: Project?,
    parentComponent: Component? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED
  ): GHAccountAuthData? {
    val model = AccountManagerLoginModel()
    login(
      model, GHLoginRequest(server = server, login = login, isLoginEditable = login != null, authType = authType),
      project, parentComponent
    )
    return model.authData
  }

  @RequiresEdt
  @JvmStatic
  internal fun login(model: GHLoginModel, request: GHLoginRequest, project: Project?, parentComponent: Component?) {
    if (request.server != GithubServerPath.DEFAULT_SERVER) {
      request.loginWithToken(model, project, parentComponent)
    }
    else when (request.authType) {
      AuthorizationType.OAUTH -> request.loginWithOAuth(model, project, parentComponent)
      AuthorizationType.TOKEN -> request.loginWithToken(model, project, parentComponent)
      AuthorizationType.UNDEFINED -> request.loginWithOAuthOrToken(model, project, parentComponent)
    }
  }
}

class GHAccountAuthData(val account: GithubAccount, login: String, token: String) : AuthData(login, token) {
  val server: GithubServerPath get() = account.server
  val token: String get() = password!!
}

internal class GHLoginRequest(
  val text: @NlsContexts.DialogMessage String? = null,
  val error: Throwable? = null,

  val server: GithubServerPath? = null,
  val isServerEditable: Boolean = server == null,

  val login: String? = null,
  val isLoginEditable: Boolean = true,

  val authType: AuthorizationType = AuthorizationType.UNDEFINED
)

private fun GHLoginRequest.configure(dialog: GHLoginDialog) {
  error?.let { dialog.setError(it) }
  server?.let { dialog.setServer(it.toString(), isServerEditable) }
  login?.let { dialog.setLogin(it, isLoginEditable) }
}

private fun GHLoginRequest.loginWithToken(model: GHLoginModel, project: Project?, parentComponent: Component?) {
  val dialog = GHLoginDialog.Token(model, project, parentComponent)
  configure(dialog)
  DialogManager.show(dialog)
}

private fun GHLoginRequest.loginWithOAuth(model: GHLoginModel, project: Project?, parentComponent: Component?) {
  val dialog = GHLoginDialog.OAuth(model, project, parentComponent)
  configure(dialog)
  DialogManager.show(dialog)
}

private fun GHLoginRequest.loginWithOAuthOrToken(model: GHLoginModel, project: Project?, parentComponent: Component?) {
  when (promptOAuthLogin(this, project, parentComponent)) {
    Messages.YES -> loginWithOAuth(model, project, parentComponent)
    Messages.NO -> loginWithToken(model, project, parentComponent)
  }
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

private class AccountManagerLoginModel(private val account: GithubAccount? = null) : GHLoginModel {
  private val accountManager: GHAccountManager = service()

  var authData: GHAccountAuthData? = null

  override fun isAccountUnique(server: GithubServerPath, login: String): Boolean =
    accountManager.accountsState.value.filter {
      it != account
    }.none {
      it.name == login && it.server.equals(server, true)
    }

  override suspend fun saveLogin(server: GithubServerPath, login: String, token: String) {
    val acc = account ?: GHAccountManager.createAccount(login, server)
    acc.name = login
    accountManager.updateAccount(acc, token)
    authData = GHAccountAuthData(acc, login, token)
  }
}