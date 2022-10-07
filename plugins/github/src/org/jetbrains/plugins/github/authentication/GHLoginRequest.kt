// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import git4idea.DialogManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager.Companion.createAccount
import org.jetbrains.plugins.github.authentication.ui.BaseLoginDialog
import org.jetbrains.plugins.github.authentication.ui.GHOAuthLoginDialog
import org.jetbrains.plugins.github.authentication.ui.GHTokenLoginDialog
import org.jetbrains.plugins.github.authentication.ui.UniqueLoginPredicate
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import java.awt.Component

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

internal fun GHLoginRequest.loginWithToken(project: Project?, parentComponent: Component?): GHAccountAuthData? {
  val dialog = GHTokenLoginDialog(project, parentComponent, isLoginUniqueChecker)
  configure(dialog)

  return dialog.getAuthData()
}

internal fun GHLoginRequest.loginWithOAuth(project: Project?, parentComponent: Component?): GHAccountAuthData? {
  val dialog = GHOAuthLoginDialog(project, parentComponent, isLoginUniqueChecker)
  configure(dialog)

  return dialog.getAuthData()
}

internal fun GHLoginRequest.loginWithOAuthOrToken(project: Project?, parentComponent: Component?): GHAccountAuthData? =
  when (promptOAuthLogin(this, project, parentComponent)) {
    Messages.YES -> loginWithOAuth(project, parentComponent)
    Messages.NO -> loginWithToken(project, parentComponent)
    else -> null
  }

private val GHLoginRequest.isLoginUniqueChecker: UniqueLoginPredicate
  get() = { login, server ->
    !isCheckLoginUnique ||
    service<GHAccountManager>().accountsState.value.none {
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
  return if (isOK) GHAccountAuthData(createAccount(login, server), login, token) else null
}

private fun promptOAuthLogin(request: GHLoginRequest, project: Project?, parentComponent: Component?): Int {
  val builder = MessageDialogBuilder.yesNoCancel(message("login.to.github"), request.text ?: message("dialog.message.login.to.continue"))
    .yesText(message("login.via.github.action"))
    .noText(message("button.use.token"))
    .icon(Messages.getWarningIcon())
  if (parentComponent != null) {
    return builder.show(parentComponent)
  }
  else {
    return builder.show(project)
  }

}