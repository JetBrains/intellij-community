// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.auth.ui.AccountsPanelFactory.Companion.addWarningForPersistentCredentials
import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.collaboration.auth.ui.login.TokenLoginDialog
import com.intellij.collaboration.auth.ui.login.TokenLoginInputPanelFactory
import com.intellij.collaboration.auth.ui.login.TokenLoginPanelModel
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabChooseAccountDialog
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Component
import javax.swing.JComponent

object GitLabLoginUtil {

  @RequiresEdt
  internal fun logInViaToken(
    project: Project, parentComponent: JComponent?,
    serverPath: GitLabServerPath = GitLabServerPath.DEFAULT_SERVER,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): Pair<GitLabAccount, String>? = logInViaToken(project, parentComponent, serverPath, null, uniqueAccountPredicate)

  @RequiresEdt
  internal fun logInViaToken(
    project: Project, parentComponent: JComponent?,
    serverPath: GitLabServerPath = GitLabServerPath.DEFAULT_SERVER, requiredUsername: String? = null,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): Pair<GitLabAccount, String>? {

    val model = GitLabTokenLoginPanelModel(requiredUsername, uniqueAccountPredicate).apply {
      serverUri = serverPath.uri
    }
    val loginState = showLoginDialog(project, parentComponent, model,
                                     GitLabBundle.message("account.add.dialog.title"), false)
    if (loginState is LoginModel.LoginState.Connected) {
      return GitLabAccount(name = loginState.username, server = model.getServerPath()) to model.token
    }
    return null
  }

  @RequiresEdt
  internal fun updateToken(
    project: Project, parentComponent: JComponent?,
    account: GitLabAccount,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): String? = updateToken(project, parentComponent, account, null, uniqueAccountPredicate)

  @RequiresEdt
  internal fun updateToken(
    project: Project, parentComponent: JComponent?,
    account: GitLabAccount, requiredUsername: String? = null,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): String? {
    val predicateWithoutCurrent: (GitLabServerPath, String) -> Boolean = { serverPath, username ->
      if (serverPath == account.server && username == account.name) true
      else uniqueAccountPredicate(serverPath, username)
    }

    val model = GitLabTokenLoginPanelModel(requiredUsername, predicateWithoutCurrent).apply {
      serverUri = account.server.uri
    }
    val loginState = showLoginDialog(project, parentComponent, model,
                                     GitLabBundle.message("account.update.dialog.title"), true)
    if (loginState is LoginModel.LoginState.Connected) {
      return model.token
    }
    return null
  }

  private fun showLoginDialog(
    project: Project,
    parentComponent: JComponent?,
    model: TokenLoginPanelModel,
    title: @NlsContexts.DialogTitle String,
    serverFieldDisabled: Boolean
  ): LoginModel.LoginState {
    TokenLoginDialog(project, parentComponent, model, title) {
      val cs = this
      TokenLoginInputPanelFactory(model).createIn(
        cs,
        serverFieldDisabled,
        tokenNote = CollaborationToolsBundle.message("clone.dialog.insufficient.scopes", GitLabSecurityUtil.MASTER_SCOPES),
        footer = {
            addWarningForPersistentCredentials(
              cs,
              service<GitLabAccountManager>().canPersistCredentials,
              ::panel
            )
        }
      )
    }.showAndGet()
    return model.loginState.value
  }

  @RequiresEdt
  internal fun chooseAccount(project: Project,
                             parentComponent: Component?,
                             description: @Nls String?,
                             accounts: Collection<GitLabAccount>): GitLabAccount? {
    val dialog = GitLabChooseAccountDialog(project, parentComponent, accounts, false, true, description = description)
    return if (dialog.showAndGet()) {
      val account = dialog.account
      if (dialog.setDefault) {
        project.service<GitLabProjectDefaultAccountHolder>().account = account
      }
      account
    }
    else {
      null
    }
  }

  fun isAccountUnique(accounts: Collection<GitLabAccount>, server: GitLabServerPath, username: String): Boolean =
    accounts.none { it.server == server && it.name == username }
}