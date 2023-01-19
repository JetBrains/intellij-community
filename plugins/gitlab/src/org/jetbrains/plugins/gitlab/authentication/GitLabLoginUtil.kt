// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.collaboration.auth.ui.login.TokenLoginDialog
import com.intellij.collaboration.auth.ui.login.TokenLoginInputPanelFactory
import com.intellij.collaboration.auth.ui.login.TokenLoginPanelModel
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel
import javax.swing.JComponent

object GitLabLoginUtil {

  internal fun logInViaToken(
    project: Project, parentComponent: JComponent,
    serverPath: GitLabServerPath = GitLabServerPath.DEFAULT_SERVER,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): Pair<GitLabAccount, String>? {

    val model = GitLabTokenLoginPanelModel(uniqueAccountPredicate).apply {
      serverUri = serverPath.uri
    }
    val loginState = showLoginDialog(project, parentComponent, model, false)
    if (loginState is LoginModel.LoginState.Connected) {
      return GitLabAccount(name = loginState.username, server = model.getServerPath()) to model.token
    }
    return null
  }

  internal fun updateToken(
    project: Project, parentComponent: JComponent,
    account: GitLabAccount,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): String? {
    val predicateWithoutCurrent: (GitLabServerPath, String) -> Boolean = { serverPath, username ->
      if (serverPath == account.server && username == account.name) true
      else uniqueAccountPredicate(serverPath, username)
    }

    val model = GitLabTokenLoginPanelModel(predicateWithoutCurrent).apply {
      serverUri = account.server.uri
    }
    val loginState = showLoginDialog(project, parentComponent, model, true)
    if (loginState is LoginModel.LoginState.Connected) {
      return model.token
    }
    return null
  }

  private fun showLoginDialog(
    project: Project,
    parentComponent: JComponent,
    model: TokenLoginPanelModel,
    serverFieldDisabled: Boolean
  ): LoginModel.LoginState {
    TokenLoginDialog(project, parentComponent, model) {
      TokenLoginInputPanelFactory(model).create(serverFieldDisabled, null)
    }.showAndGet()
    return model.loginState.value
  }
}