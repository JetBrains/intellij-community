// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.auth.ui.login.LoginModel.LoginState
import com.intellij.collaboration.auth.ui.login.TokenLoginDialog
import com.intellij.collaboration.auth.ui.login.TokenLoginInputPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.toHttpsNormalizedURI
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabChooseAccountDialog
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel
import org.jetbrains.plugins.gitlab.ui.util.GitLabPluginProjectScopeProvider
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Component
import javax.swing.JComponent

object GitLabLoginUtil {
  private val LOG = logger<GitLabLoginUtil>()

  @RequiresEdt
  internal fun logInViaOAuth(
    project: Project,
    serverPath: GitLabServerPath = GitLabServerPath.DEFAULT_SERVER, requiredUsername: String? = null,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult {
    val dialogTitle = GitLabBundle.message("account.add.dialog.title")
    return performOAuthLogin(project, requiredUsername, serverPath, uniqueAccountPredicate, dialogTitle, loginSource)
  }

  @ApiStatus.Internal
  @RequiresEdt
  fun logInViaToken(
    project: Project, parentComponent: JComponent?,
    serverPath: GitLabServerPath = GitLabServerPath.DEFAULT_SERVER,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult = logInViaToken(project, parentComponent, serverPath, null, loginSource, uniqueAccountPredicate)

  @RequiresEdt
  private fun logInViaToken(
    project: Project, parentComponent: JComponent?,
    serverPath: GitLabServerPath = GitLabServerPath.DEFAULT_SERVER, requiredUsername: String? = null,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult {

    val model = GitLabTokenLoginPanelModel(requiredUsername, uniqueAccountPredicate).apply {
      serverUri = serverPath.uri
    }

    val dialogTitle = GitLabBundle.message("account.add.dialog.title")
    val exitCode = showTokenLoginDialog(project, parentComponent, model, dialogTitle, false, loginSource == GitLabLoginSource.GIT)
    return getLoginResult(
      model.loginState.value,
      exitCode,
      model.getServerPath(),
      loginSource,
      GitLabCredentials.Token(model.token)
    )
  }

  private fun getLoginResult(
    loginState: LoginState,
    exitCode: Int,
    serverPath: GitLabServerPath,
    loginSource: GitLabLoginSource,
    credentials: GitLabCredentials?,
    accountId: String? = null,
  ): LoginResult = when (exitCode) {
    DialogWrapper.OK_EXIT_CODE -> {
      createSuccessResult(loginState, serverPath, loginSource, credentials, accountId)
    }
    DialogWrapper.NEXT_USER_EXIT_CODE -> LoginResult.OtherMethod
    else -> LoginResult.Failure
  }

  private fun createSuccessResult(
    loginState: LoginState,
    serverPath: GitLabServerPath,
    loginSource: GitLabLoginSource,
    credentials: GitLabCredentials?,
    accountId: String? = null,
  ): LoginResult {
    val connected = loginState.asSafely<LoginState.Connected>()
                    ?: return LoginResult.Failure

    credentials ?: return LoginResult.Failure

    val isReLogin = accountId != null
    val loginData = GitLabLoginData(loginSource, isReLogin, serverPath.isDefault)
    GitLabLoginCollector.login(loginData)

    val account = if (accountId != null) {
      GitLabAccount(accountId, connected.username, serverPath)
    }
    else {
      GitLabAccount(name = connected.username, server = serverPath)
    }
    return LoginResult.Success(account, credentials)
  }

  @RequiresEdt
  internal fun reLogInViaToken(
    project: Project, parentComponent: JComponent?,
    account: GitLabAccount,
    requiredUsername: String? = null,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult {
    val predicateWithoutCurrent: (GitLabServerPath, String) -> Boolean = { serverPath, username ->
      if (serverPath == account.server && username == account.name) true
      else uniqueAccountPredicate(serverPath, username)
    }

    val model = GitLabTokenLoginPanelModel(requiredUsername, predicateWithoutCurrent).apply {
      serverUri = account.server.uri
    }
    val title = GitLabBundle.message("account.update.dialog.title")
    val exitState = showTokenLoginDialog(project, parentComponent, model, title, true, loginSource == GitLabLoginSource.GIT)
    val loginState = model.loginState.value
    return getLoginResult(
      loginState,
      exitState,
      model.getServerPath(),
      loginSource,
      GitLabCredentials.Token(model.token),
      account.id
    )
  }

  @RequiresEdt
  internal fun reLogInViaOAuth(
    project: Project,
    account: GitLabAccount,
    requiredUsername: String? = null,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult {
    val predicateWithoutCurrent: (GitLabServerPath, String) -> Boolean = { serverPath, username ->
      if (serverPath == account.server && username == account.name) true
      else uniqueAccountPredicate(serverPath, username)
    }
    val dialogTitle = GitLabBundle.message("account.relogin.dialog.title")
    return performOAuthLogin(project, requiredUsername, account.server, predicateWithoutCurrent, dialogTitle, loginSource, account.id)
  }

  @RequiresEdt
  @ApiStatus.Internal
  fun reLogIn(
    project: Project,
    parentComponent: JComponent?,
    account: GitLabAccount,
    requiredUsername: String? = null,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult {
    if (account.server != GitLabServerPath.DEFAULT_SERVER) {
      return reLogInViaToken(project, parentComponent, account, requiredUsername, loginSource, uniqueAccountPredicate)
    }
    return when (promptLogin(project, parentComponent)) {
      Messages.YES -> reLogInViaOAuth(project,
                                      account,
                                      requiredUsername,
                                      loginSource,
                                      uniqueAccountPredicate)
      Messages.NO -> reLogInViaToken(project, parentComponent, account, requiredUsername, loginSource, uniqueAccountPredicate)
      else -> LoginResult.OtherMethod
    }
  }

  @ApiStatus.Internal
  @RequiresEdt
  fun loginWithOAuthOrToken(
    project: Project,
    parentComponent: JComponent?,
    serverPath: GitLabServerPath = GitLabServerPath.DEFAULT_SERVER,
    requiredUsername: String? = null,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult {
    if (serverPath != GitLabServerPath.DEFAULT_SERVER) {
      return logInViaToken(project, parentComponent, serverPath, requiredUsername, loginSource, uniqueAccountPredicate)
    }
    return when (promptLogin(project, parentComponent)) {
      Messages.YES -> logInViaOAuth(project,
                                    serverPath,
                                    requiredUsername,
                                    loginSource,
                                    uniqueAccountPredicate)
      Messages.NO -> logInViaToken(project,
                                   parentComponent,
                                   serverPath,
                                   requiredUsername,
                                   loginSource,
                                   uniqueAccountPredicate)
      else -> LoginResult.OtherMethod
    }
  }

  @RequiresEdt
  private fun showTokenLoginDialog(
    project: Project,
    parentComponent: JComponent?,
    model: GitLabTokenLoginPanelModel,
    title: @NlsContexts.DialogTitle String,
    serverFieldDisabled: Boolean,
    canLogInWithGit: Boolean,
  ): Int {
    val scopeProvider = project.service<GitLabPluginProjectScopeProvider>()
    val dialog = scopeProvider.constructDialog("GitLab token login dialog") {
      TokenLoginDialog(project, this, parentComponent, model, title, model.tryGitAuthorizationSignal) {
        val cs = this
        TokenLoginInputPanelFactory(model).createIn(
          cs,
          serverFieldDisabled,
          tokenNote = CollaborationToolsBundle.message("clone.dialog.insufficient.scopes", GitLabSecurityUtil.MASTER_SCOPES),
          errorPresenter = GitLabLoginErrorStatusPresenter(cs, model, canLogInWithGit)
        )
      }
    }
    dialog.showAndGet()

    return dialog.exitCode
  }

  @RequiresEdt
  private fun performOAuthLogin(
    project: Project,
    requiredUsername: String? = null,
    serverPath: GitLabServerPath,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
    title: @NlsContexts.DialogTitle String,
    loginSource: GitLabLoginSource,
    accountId: String? = null,
  ): LoginResult {
    return try {
      runWithModalProgressBlocking(project, title) {
        val credentials = GitLabOAuthService.instance.authorize(serverPath)
        val username =
          GitLabSecurityUtil.validateAndResolveUsername(requiredUsername, serverPath, credentials.accessToken, uniqueAccountPredicate)
        createSuccessResult(LoginState.Connected(username), serverPath, loginSource, credentials, accountId)
      }
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.warn("GitLab OAuth login failed", e)
      LoginResult.Failure
    }
  }

  private fun promptLogin(project: Project, parentComponent: JComponent?): Int {
    val message = if (PasswordSafe.instance.isMemoryOnly) {
      HtmlBuilder()
        .append(HtmlChunk.p().addText(CollaborationToolsBundle.message("accounts.error.password-not-saved")))
        .append(HtmlChunk.br())
        .append(HtmlChunk.p().addText(CollaborationToolsBundle.message("accounts.error.password-not-saved.solution")))
        .toString()
    }
    else GitLabBundle.message("account.add.dialog.continue.text")

    val builder = MessageDialogBuilder
      .yesNoCancel(title = GitLabBundle.message("account.add.dialog.title"),
                   message = message)
      .yesText(GitLabBundle.message("account.add.popup.text"))
      .noText(CollaborationToolsBundle.message("accounts.action.add.account.with.token"))

    if (PasswordSafe.instance.isMemoryOnly) {
      builder.asWarning()
    }

    if (parentComponent != null) {
      return builder.show(parentComponent)
    }
    else {
      return builder.show(project)
    }
  }

  @RequiresEdt
  internal fun chooseAccount(
    project: Project,
    parentComponent: Component?,
    description: @Nls String?,
    accounts: Collection<GitLabAccount>,
  ): GitLabAccount? {
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
  data class Success(val account: GitLabAccount, val credentials: GitLabCredentials) : LoginResult
  data object Failure : LoginResult
  data object OtherMethod : LoginResult
}

internal suspend fun GitLabAccountManager.save(loginResult: LoginResult.Success) {
  updateAccount(loginResult.account, loginResult.credentials)
}