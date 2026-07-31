// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

import com.intellij.collaboration.auth.ui.login.LoginModel.LoginState
import com.intellij.collaboration.auth.ui.login.LoginModel.LoginState.Connected
import com.intellij.collaboration.auth.ui.login.TokenLoginDialog
import com.intellij.collaboration.auth.ui.login.TokenLoginInputPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.toHttpsNormalizedURI
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabChooseAccountDialog
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabOAuthLoginDialogComponentFactory
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabOAuthLoginOutcome
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel
import org.jetbrains.plugins.gitlab.authentication.ui.YesNoCancelWithOptionsDialog
import org.jetbrains.plugins.gitlab.ui.util.GitLabPluginProjectScopeProvider
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Component
import javax.swing.JComponent

object GitLabLoginUtil {
  private val LOG = logger<GitLabLoginUtil>()

  @RequiresEdt
  internal fun logInViaOAuth(
    project: Project,
    requiredUsername: String? = null,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult {
    val dialogTitle = GitLabBundle.message("account.add.dialog.title")
    return performOAuthLogin(project, requiredUsername, uniqueAccountPredicate, dialogTitle, loginSource)
  }

  @RequiresEdt
  internal fun logInViaOAuthToCustomServer(
    project: Project,
    parentComponent: JComponent?,
    requiredUsername: String? = null,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult {
    if (serverPath?.isDefault == true) return LoginResult.Failure
    val dialogTitle = GitLabBundle.message("account.add.dialog.title")
    return performOAuthLoginToCustomServer(project = project,
                                           parentComponent = parentComponent,
                                           requiredUsername = requiredUsername,
                                           dialogTitle = dialogTitle,
                                           uniqueAccountPredicate = uniqueAccountPredicate,
                                           serverFieldDisabled = false,
                                           loginSource = loginSource)
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
    if (!account.server.isDefault) return LoginResult.Failure
    val predicateWithoutCurrent: (GitLabServerPath, String) -> Boolean = { serverPath, username ->
      if (serverPath == account.server && username == account.name) true
      else uniqueAccountPredicate(serverPath, username)
    }
    val dialogTitle = GitLabBundle.message("account.relogin.dialog.title")
    return performOAuthLogin(project, requiredUsername, predicateWithoutCurrent, dialogTitle, loginSource, account.id)
  }

  @RequiresEdt
  internal fun reLogInViaOAuthToCustomServer(
    project: Project,
    parentComponent: JComponent?,
    account: GitLabAccount,
    requiredUsername: String? = null,
    loginSource: GitLabLoginSource,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): LoginResult {
    if (account.server.isDefault) return LoginResult.Failure
    val predicateWithoutCurrent: (GitLabServerPath, String) -> Boolean = { serverPath, username ->
      if (serverPath == account.server && username == account.name) true
      else uniqueAccountPredicate(serverPath, username)
    }
    val dialogTitle = GitLabBundle.message("account.relogin.dialog.title")
    return performOAuthLoginToCustomServer(project = project,
                                           parentComponent = parentComponent,
                                           requiredUsername = requiredUsername,
                                           dialogTitle = dialogTitle,
                                           uniqueAccountPredicate = predicateWithoutCurrent,
                                           serverFieldDisabled = true,
                                           loginSource = loginSource,
                                           requiredServerPath = account.server,
                                           accountId = account.id)
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
    return when (promptLogin(project, parentComponent, account.server)) {
      LoginMethod.OAUTH -> reLogInViaOAuth(project, account, requiredUsername, loginSource, uniqueAccountPredicate)
      LoginMethod.OAUTH_CUSTOM_SERVER -> reLogInViaOAuthToCustomServer(project,
                                                                       parentComponent,
                                                                       account,
                                                                       requiredUsername,
                                                                       loginSource,
                                                                       uniqueAccountPredicate)
      LoginMethod.TOKEN -> reLogInViaToken(project, parentComponent, account, requiredUsername, loginSource, uniqueAccountPredicate)
      null -> LoginResult.OtherMethod
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
    return when (promptLogin(project, parentComponent, serverPath)) {
      LoginMethod.OAUTH -> logInViaOAuth(project, requiredUsername, loginSource, uniqueAccountPredicate)
      LoginMethod.OAUTH_CUSTOM_SERVER -> logInViaOAuthToCustomServer(project,
                                                                     parentComponent,
                                                                     requiredUsername,
                                                                     loginSource,
                                                                     uniqueAccountPredicate)
      LoginMethod.TOKEN -> logInViaToken(project, parentComponent, serverPath, requiredUsername, loginSource, uniqueAccountPredicate)
      null -> LoginResult.OtherMethod
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
          errorPresenter = GitLabLoginErrorStatusPresenter(model, canLogInWithGit)
        )
      }
    }
    dialog.showAndGet()
    return dialog.exitCode
  }

  private fun performOAuthLoginToCustomServer(
    project: Project,
    parentComponent: JComponent?,
    requiredUsername: String?,
    dialogTitle: @NlsContexts.DialogTitle String,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
    serverFieldDisabled: Boolean,
    loginSource: GitLabLoginSource,
    requiredServerPath: GitLabServerPath? = null,
    accountId: String? = null,
  ): LoginResult {
    val outcome = GitLabOAuthLoginDialogComponentFactory.showIn(
      project = project,
      parentComponent = parentComponent,
      title = dialogTitle,
      requiredServerPath = requiredServerPath,
      serverFieldDisabled = serverFieldDisabled,
      canLogInWithGit = loginSource == GitLabLoginSource.GIT,
      requiredUsername = requiredUsername,
      uniqueAccountPredicate = uniqueAccountPredicate
    )

    return when (outcome) {
      is GitLabOAuthLoginOutcome.Success -> with(outcome) {
        createSuccessResult(Connected(username), serverPath, loginSource, credentials, accountId)
      }
      GitLabOAuthLoginOutcome.Cancelled -> LoginResult.Failure
      GitLabOAuthLoginOutcome.OtherMethod -> LoginResult.OtherMethod
    }
  }

  @RequiresEdt
  private fun performOAuthLogin(
    project: Project,
    requiredUsername: String? = null,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
    title: @NlsContexts.DialogTitle String,
    loginSource: GitLabLoginSource,
    accountId: String? = null,
  ): LoginResult {
    return try {
      runWithModalProgressBlocking(project, title) {
        val credentials = GitLabOAuthService.instance.authorizeToGitLabDotCom()
        val username =
          GitLabSecurityUtil.validateAndResolveUsername(requiredUsername,
                                                        GitLabServerPath.DEFAULT_SERVER,
                                                        credentials.accessToken,
                                                        uniqueAccountPredicate)
        createSuccessResult(LoginState.Connected(username), GitLabServerPath.DEFAULT_SERVER, loginSource, credentials, accountId)
      }
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.warn("GitLab OAuth login failed", e)
      LoginResult.Failure
    }
  }

  private fun promptLogin(project: Project, parentComponent: JComponent?, serverPath: GitLabServerPath?): LoginMethod? {
    val willPasswordNotBeSaved = PasswordSafe.instance.isMemoryOnly
    val message = if (willPasswordNotBeSaved) {
      HtmlBuilder()
        .append(HtmlChunk.p().addText(CollaborationToolsBundle.message("accounts.error.password-not-saved")))
        .append(HtmlChunk.br())
        .append(HtmlChunk.p().addText(CollaborationToolsBundle.message("accounts.error.password-not-saved.solution")))
        .wrapWithHtmlBody()
        .toString()
    }
    else GitLabBundle.message("account.add.dialog.continue.text")

    val oauthChoices = buildList {
      when (serverPath) {
        null -> {
          add(GitLabBundle.message("account.add.popup.text") to LoginMethod.OAUTH)
          add(GitLabBundle.message("account.add.custom.server.popup.text") to LoginMethod.OAUTH_CUSTOM_SERVER)
        }
        GitLabServerPath.DEFAULT_SERVER -> {
          add(GitLabBundle.message("account.add.popup.text") to LoginMethod.OAUTH)
        }
        else -> {
          add(GitLabBundle.message("account.add.custom.server.popup.text") to LoginMethod.OAUTH_CUSTOM_SERVER)
        }
      }
    }

    val dialog = YesNoCancelWithOptionsDialog(
      project = project,
      parentComponent = parentComponent,
      title = GitLabBundle.message("account.add.dialog.title"),
      message = message,
      yesChoices = oauthChoices,
      noChoice = CollaborationToolsBundle.message("accounts.action.add.account.with.token") to LoginMethod.TOKEN,
      icon = if (willPasswordNotBeSaved) UIUtil.getWarningIcon() else UIUtil.getQuestionIcon()
    )
    dialog.show()
    return dialog.chosenValue
  }

  private enum class LoginMethod {
    OAUTH, OAUTH_CUSTOM_SERVER, TOKEN
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