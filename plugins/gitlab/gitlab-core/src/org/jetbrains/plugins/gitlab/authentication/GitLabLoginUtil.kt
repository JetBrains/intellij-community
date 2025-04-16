// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.collaboration.auth.ui.login.TokenLoginDialog
import com.intellij.collaboration.auth.ui.login.TokenLoginInputPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.toHttpsNormalizedURI
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabChooseAccountDialog
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel
import org.jetbrains.plugins.gitlab.ui.util.GitLabPluginProjectScopeProvider
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Component
import javax.swing.JComponent

object GitLabLoginUtil {

  @ApiStatus.Internal
  @RequiresEdt
  fun logInViaToken(
    project: Project, parentComponent: JComponent?,
    serverPath: GitLabServerPath = GitLabServerPath.DEFAULT_SERVER,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): LoginResult = logInViaToken(project, parentComponent, serverPath, null, uniqueAccountPredicate)

  @RequiresEdt
  internal fun logInViaToken(
    project: Project, parentComponent: JComponent?,
    serverPath: GitLabServerPath = GitLabServerPath.DEFAULT_SERVER, requiredUsername: String? = null,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): LoginResult {

    val model = GitLabTokenLoginPanelModel(requiredUsername, uniqueAccountPredicate).apply {
      serverUri = serverPath.uri
    }

    val dialogTitle = GitLabBundle.message("account.add.dialog.title")
    val exitCode = showLoginDialog(project, parentComponent, model, dialogTitle, false)
    return when (exitCode) {
      DialogWrapper.OK_EXIT_CODE -> {
        val loginResult = model.loginState.value.asSafely<LoginModel.LoginState.Connected>() ?: return LoginResult.Failure
        return LoginResult.Success(GitLabAccount(name = loginResult.username, server = model.getServerPath()), model.token)
      }
      DialogWrapper.NEXT_USER_EXIT_CODE -> LoginResult.OtherMethod
      else -> LoginResult.Failure
    }
  }

  @RequiresEdt
  internal fun updateToken(
    project: Project, parentComponent: JComponent?,
    account: GitLabAccount,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): LoginResult = updateToken(project, parentComponent, account, null, uniqueAccountPredicate)

  @RequiresEdt
  internal fun updateToken(
    project: Project, parentComponent: JComponent?,
    account: GitLabAccount, requiredUsername: String? = null,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean
  ): LoginResult {
    val predicateWithoutCurrent: (GitLabServerPath, String) -> Boolean = { serverPath, username ->
      if (serverPath == account.server && username == account.name) true
      else uniqueAccountPredicate(serverPath, username)
    }

    val model = GitLabTokenLoginPanelModel(requiredUsername, predicateWithoutCurrent).apply {
      serverUri = account.server.uri
    }
    val title = GitLabBundle.message("account.update.dialog.title")
    val exitState = showLoginDialog(project, parentComponent, model, title, true)
    val loginState = model.loginState.value
    if (exitState == DialogWrapper.OK_EXIT_CODE && loginState is LoginModel.LoginState.Connected) {
      return LoginResult.Success(
        GitLabAccount(id = account.id, name = loginState.username, server = model.getServerPath()),
        model.token
      )
    }

    return LoginResult.Failure
  }

  @RequiresEdt
  private fun showLoginDialog(
    project: Project,
    parentComponent: JComponent?,
    model: GitLabTokenLoginPanelModel,
    title: @NlsContexts.DialogTitle String,
    serverFieldDisabled: Boolean
  ): Int {
    val scopeProvider = project.service<GitLabPluginProjectScopeProvider>()
    val dialog = scopeProvider.constructDialog("GitLab token login dialog") {
      TokenLoginDialog(project, this, parentComponent, model, title, model.tryGitAuthorizationSignal) {
        val cs = this
        TokenLoginInputPanelFactory(model).createIn(
          cs,
          serverFieldDisabled,
          tokenNote = CollaborationToolsBundle.message("clone.dialog.insufficient.scopes", GitLabSecurityUtil.MASTER_SCOPES),
          errorPresenter = GitLabLoginErrorStatusPresenter(cs, model)
        )
      }
    }
    dialog.showAndGet()

    return dialog.exitCode
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
    accounts.none { it.server.toHttpsNormalizedURI() == server.toHttpsNormalizedURI() && it.name == username }
}

sealed interface LoginResult {
  data class Success(val account: GitLabAccount, val token: String) : LoginResult
  data object Failure : LoginResult
  data object OtherMethod : LoginResult
}