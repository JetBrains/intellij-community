// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.DataManager
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.DropDownLink
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.DialogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHLoginData.Companion.reLogin
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GHLoginModel
import org.jetbrains.plugins.github.authentication.ui.GHTokenLoginDialog
import org.jetbrains.plugins.github.authentication.ui.GHTokenLoginViewModel
import org.jetbrains.plugins.github.authentication.ui.LoginNotUniqueException
import org.jetbrains.plugins.github.authentication.ui.UniqueLoginPredicate
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.GHPluginProjectScopeProvider
import java.awt.Component
import java.util.concurrent.CancellationException
import javax.swing.JButton
import javax.swing.JComponent

private val LOG = logger<GHAccountsUtil>()

private val accountManager: GHAccountManager get() = service()

object GHAccountsUtil {
  @JvmStatic
  val accounts: Set<GithubAccount>
    get() = accountManager.accountsState.value

  @JvmStatic
  val accountsState: StateFlow<Set<GithubAccount>>
    get() = accountManager.accountsState

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

  /**
   * Ask the user to log into GitHub with a new account
   *
   * @param server the target server if needed
   * @param login the target login if needed (the account login will be compared with this login)
   * @param project parent project for the login dialog
   * @param parentComponent parent component for the login dialog
   * @param reason reason for the login request (a url or a service name for example)
   * * @param authType type of authorization to use
   * @param loginSource source of the login request
   *
   * @return a new account if the login was successful, `null` otherwise
   */
  @RequiresEdt
  @JvmOverloads
  @JvmStatic
  fun logIn(
    server: GithubServerPath? = null,
    login: String? = null,
    project: Project?,
    parentComponent: Component? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED,
    reason: @NlsContexts.DialogMessage String? = null,
    loginSource: GHLoginSource = GHLoginSource.UNKNOWN,
  ): GithubAccount? {
    return doLogIn(
      server,
      login,
      project,
      parentComponent,
      reason,
      authType,
      loginSource
    )?.account
  }

  /**
   * Ask the user to re-login into the [account]
   *
   * @param project parent project for the login dialog
   * @param parentComponent parent component for the login dialog
   * @param reason reason for the login request (a missing or an incorrect token, for example)
   * @param authType type of authorization to use
   * @param loginSource source of the login request
   *
   * @return `true` if the re-login was successful, `false` otherwise
   */
  @RequiresEdt
  @JvmOverloads
  @JvmStatic
  fun reLogIn(
    account: GithubAccount,
    project: Project?,
    parentComponent: Component? = null,
    reason: @NlsContexts.DialogMessage String? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED,
    loginSource: GHLoginSource = GHLoginSource.UNKNOWN,
  ): Boolean {
    return doReLogIn(
      account,
      project,
      parentComponent,
      reason,
      authType,
      loginSource
    ) != null
  }

  fun createAddAccountLink(
    project: Project,
    accountSelectionModel: CollectionComboBoxModel<GithubAccount>,
    loginSource: GHLoginSource,
  ): JButton {
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
      val group = createAddAccountActionGroup(model, project, it, loginSource)
      JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(it),
                                JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
    }
  }

  internal fun createAddAccountActionGroup(
    model: GHLoginModel,
    project: Project,
    parentComponent: JComponent,
    loginSource: GHLoginSource,
  ): ActionGroup {
    val group = DefaultActionGroup()
    val loginData = GHLoginData(loginSource)
    group.add(createBrowserLoginAction(model, project, parentComponent, loginData))
    group.add(createTokenLoginAction(CollaborationToolsBundle.message("accounts.action.add.account.with.token"),
                                     model, project, parentComponent, loginData, isEnterprise = false))
    group.add(Separator())

    group.add(createTokenLoginAction(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text"),
                                     model, project, parentComponent, loginData, isEnterprise = true))
    return group
  }

  //region Login actions
  private fun createBrowserLoginAction(
    model: GHLoginModel, project: Project, parentComponent: JComponent, loginData: GHLoginData,
  ): AnAction = DumbAwareAction.create(GithubBundle.message("action.Github.Accounts.AddGHAccount.text")) {
    loginWithOAuth(model, project, parentComponent, loginData)
  }

  private fun createTokenLoginAction(
    @NlsActions.ActionText actionName: String,
    model: GHLoginModel,
    project: Project,
    parentComponent: JComponent,
    loginData: GHLoginData,
    isEnterprise: Boolean,
  ): AnAction = DumbAwareAction.create(actionName) {
    loginViaToken(model, project, parentComponent, loginData, isEnterprise)
  }

  private fun loginViaToken(
    model: GHLoginModel, project: Project, parentComponent: JComponent?, loginData: GHLoginData, isEnterprise: Boolean,
  ) {
    scopedDialog(project) {
      createTokenLoginDialog(
        model, project, parentComponent, loginData,
        server = if (isEnterprise) "" else GithubServerPath.DEFAULT_HOST,
        isServerEditable = isEnterprise,
      ).apply {
        title = GithubBundle.message("dialog.title.add.github.account")
        setLoginButtonText(GithubBundle.message("accounts.add.button"))
      }
    }.showAndGet()
  }
  //endregion

  /**
   * Internal login API, see [reLogIn] for public API
   */
  @RequiresEdt
  @JvmOverloads
  @ApiStatus.Internal
  fun requestNewToken(
    account: GithubAccount,
    project: Project?,
    parentComponent: Component? = null,
    loginSource: GHLoginSource,
  ): String? {
    return doReLogIn(account, project, parentComponent,
                     reason = CollaborationToolsBundle.message("account.token.missing.for", account),
                     loginSource = loginSource)?.token
  }

  /**
   * Internal login API, see [reLogIn] for public API
   */
  @RequiresEdt
  @JvmOverloads
  @JvmStatic
  @ApiStatus.Internal
  fun requestReLogin(
    account: GithubAccount,
    project: Project?,
    parentComponent: Component? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED,
    loginSource: GHLoginSource,
  ): GHAccountAuthData? {
    return doReLogIn(account, project, parentComponent, null, authType, loginSource)
  }

  private fun doReLogIn(
    account: GithubAccount,
    project: Project?,
    parentComponent: Component? = null,
    reason: @NlsContexts.DialogMessage String? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED,
    loginSource: GHLoginSource,
  ): GHAccountAuthData? {
    val model = AccountManagerLoginModel(account)
    login(
      model,
      GHLoginRequest(
        server = account.server,
        login = account.name,
        text = reason,
        authType = authType,
        loginData = GHLoginData(loginSource).reLogin()
      ),
      project,
      parentComponent
    )
    return model.authData
  }

  /**
   * Internal login API, see [logIn] for public API
   */
  @RequiresEdt
  @JvmOverloads
  @JvmStatic
  @ApiStatus.Internal
  fun requestNewAccount(
    server: GithubServerPath? = null,
    login: String? = null,
    project: Project?,
    parentComponent: Component? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED,
    loginSource: GHLoginSource = GHLoginSource.UNKNOWN,
  ): GHAccountAuthData? =
    doLogIn(server, login, project, parentComponent, reason = null, authType = authType, loginSource = loginSource)

  private fun doLogIn(
    server: GithubServerPath? = null,
    login: String? = null,
    project: Project?,
    parentComponent: Component? = null,
    reason: @NlsContexts.DialogMessage String? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED,
    loginSource: GHLoginSource = GHLoginSource.UNKNOWN,
  ): GHAccountAuthData? {
    val model = AccountManagerLoginModel()
    login(
      model,
      GHLoginRequest(
        server = server,
        login = login,
        text = reason,
        isLoginEditable = login != null,
        authType = authType,
        loginData = GHLoginData(loginSource)
      ),
      project,
      parentComponent
    )
    return model.authData
  }

  @RequiresEdt
  @JvmStatic
  @ApiStatus.Internal
  fun login(model: GHLoginModel, request: GHLoginRequest, project: Project?, parentComponent: Component?) {
    if (request.server != GithubServerPath.DEFAULT_SERVER) {
      request.loginWithToken(model, project, parentComponent)
    }
    else when (request.authType) {
      AuthorizationType.OAUTH -> request.loginWithOAuth(model, project, parentComponent)
      AuthorizationType.TOKEN -> request.loginWithToken(model, project, parentComponent)
      AuthorizationType.UNDEFINED -> request.loginWithOAuthOrToken(model, project, parentComponent)
    }
  }

  @ApiStatus.Internal
  @RequiresEdt
  fun loginViaOAuth(account: GithubAccount?, project: Project?, parentComponent: Component?, loginSource: GHLoginSource) {
    if (account != null) {
      requestReLogin(account,
                     project,
                     parentComponent,
                     AuthorizationType.OAUTH,
                     loginSource)
    }
    else {
      requestNewAccount(GithubServerPath.DEFAULT_SERVER,
                        login = null,
                        project,
                        parentComponent,
                        AuthorizationType.OAUTH,
                        loginSource)
    }
  }

  internal suspend fun acquireLogin(
    server: GithubServerPath,
    executor: GithubApiRequestExecutor,
    isAccountUnique: UniqueLoginPredicate,
    fixedLogin: String?,
  ): String {
    val details = executor.execute(GithubApiRequests.CurrentUser.get(server))
    val login = details.login
    if (fixedLogin != null && fixedLogin != login) throw GithubAuthenticationException("Token should match username \"$fixedLogin\"")
    if (!isAccountUnique(login, server)) throw LoginNotUniqueException(login)
    return login
  }
}

@ApiStatus.Internal
class GHAccountAuthData(val account: GithubAccount, login: String, token: String) : AuthData(login, token) {
  val server: GithubServerPath get() = account.server
  val token: String get() = password!!
}

@ApiStatus.Internal
class GHLoginRequest(
  val text: @NlsContexts.DialogMessage String? = null,

  val server: GithubServerPath? = null,
  val isServerEditable: Boolean = server == null,

  val login: String? = null,
  val isLoginEditable: Boolean = true,

  val authType: AuthorizationType = AuthorizationType.UNDEFINED,
  val loginData: GHLoginData,
)

@OptIn(DelicateCoroutinesApi::class)
private fun scopedDialog(project: Project?, dialogConstructor: CoroutineScope.() -> GHTokenLoginDialog): GHTokenLoginDialog {
  if (project != null) {
    return project.service<GHPluginProjectScopeProvider>().constructDialog("GitHub login dialog", dialogConstructor)
  }
  else {
    val cs = GlobalScope.childScope(GHTokenLoginDialog::class.java.name)
    val dialog = cs.dialogConstructor()
    cs.cancelledWith(dialog.disposable)
    return dialog
  }
}

private fun CoroutineScope.createTokenLoginDialog(
  model: GHLoginModel,
  project: Project?,
  parentComponent: Component?,
  loginData: GHLoginData,
  server: String,
  isServerEditable: Boolean,
  fixedLogin: String? = null,
): GHTokenLoginDialog {
  val loginVm = GHTokenLoginViewModel(
    this,
    GithubApiRequestExecutor.Factory.getInstance(),
    server = server,
    fixedLogin = fixedLogin,
    isAccountUnique = { login, serverPath -> model.isAccountUnique(serverPath, login) },
  ) { serverPath, login, token ->
    model.saveLogin(serverPath, login, token)
    logLoginEvent(serverPath, loginData)
  }
  return GHTokenLoginDialog(loginVm, project, this, parentComponent, isServerEditable)
}

private fun GHLoginRequest.loginWithToken(model: GHLoginModel, project: Project?, parentComponent: Component?) {
  val dialog = scopedDialog(project) {
    createTokenLoginDialog(
      model, project, parentComponent, loginData,
      server = server?.toString() ?: GithubServerPath.DEFAULT_HOST,
      isServerEditable = server == null || isServerEditable,
      fixedLogin = login.takeUnless { isLoginEditable },
    )
  }
  DialogManager.show(dialog)
}

private fun GHLoginRequest.loginWithOAuth(model: GHLoginModel, project: Project?, parentComponent: Component?) {
  loginWithOAuth(model, project, parentComponent, loginData)
}

@RequiresEdt
private fun loginWithOAuth(model: GHLoginModel, project: Project?, parentComponent: Component?, loginData: GHLoginData) {
  val modalTaskOwner = when {
    parentComponent != null -> ModalTaskOwner.component(parentComponent)
    project != null -> ModalTaskOwner.project(project)
    else -> ModalTaskOwner.guess()
  }
  try {
    runWithModalProgressBlocking(modalTaskOwner, GithubBundle.message("login.to.github")) {
      try {
        val server = GithubServerPath.DEFAULT_SERVER
        val token = GHOAuthService.instance.authorize()
        val executor = GithubApiRequestExecutor.Factory.getInstance().create(server, token)
        val login = GHAccountsUtil.acquireLogin(server,
                                                executor,
                                                isAccountUnique = { login, server -> model.isAccountUnique(server, login) },
                                                fixedLogin = null)
        model.saveLogin(server, login, token)
        GHLoginCollector.login(loginData.copy(authType = GHAuthType.OAUTH, serverOffering = GHServerOffering.of(server)))
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.warn("GitHub OAuth login failed", e)
      }
    }
  }
  catch (_: CancellationException) {
  }
}

private fun GHLoginRequest.loginWithOAuthOrToken(model: GHLoginModel, project: Project?, parentComponent: Component?) {
  when (promptOAuthLogin(this, project, parentComponent)) {
    Messages.YES -> loginWithOAuth(model, project, parentComponent)
    Messages.NO -> loginWithToken(model, project, parentComponent)
  }
}

private fun promptOAuthLogin(request: GHLoginRequest, project: Project?, parentComponent: Component?): Int {
  val message = if (PasswordSafe.instance.isMemoryOnly) {
    HtmlBuilder()
      .append(HtmlChunk.p().addText(CollaborationToolsBundle.message("accounts.error.password-not-saved")))
      .append(HtmlChunk.br())
      .append(HtmlChunk.p().addText(CollaborationToolsBundle.message("accounts.error.password-not-saved.solution")))
      .toString()
  }
  else {
    request.text ?: GithubBundle.message("dialog.message.login.to.continue")
  }

  val builder = MessageDialogBuilder
    .yesNoCancel(title = GithubBundle.message("login.to.github"),
                 message = message)
    .yesText(GithubBundle.message("login.via.github.action"))
    .noText(GithubBundle.message("button.use.token"))

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

private fun logLoginEvent(server: GithubServerPath, loginData: GHLoginData) {
  GHLoginCollector.login(loginData.copy(
    authType = GHAuthType.TOKEN,
    serverOffering = GHServerOffering.of(server))
  )
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