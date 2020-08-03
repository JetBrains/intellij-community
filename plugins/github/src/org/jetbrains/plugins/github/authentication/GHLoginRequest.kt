// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.*
import git4idea.DialogManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager.Companion.createAccount
import org.jetbrains.plugins.github.authentication.ui.*
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import java.awt.Component

internal class GHLoginRequest(
  val text: String? = null,
  val error: Throwable? = null,

  val server: GithubServerPath? = null,
  val isServerEditable: Boolean = server == null,

  val login: String? = null,
  val isLoginEditable: Boolean = true,
  val isCheckLoginUnique: Boolean = false,

  val password: String? = null,
  val token: String? = null
)

internal fun GHLoginRequest.loginWithPasswordOrToken(project: Project?, parentComponent: Component?): GHAccountAuthData? {
  val dialog = GHPasswordTokenLoginDialog(project, parentComponent, isLoginUniqueChecker, text)
  configure(dialog)
  password?.let { dialog.setPassword(it) }

  return dialog.getAuthData()
}

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
  when (promptOAuthLogin(project, parentComponent)) {
    YES -> loginWithOAuth(project, parentComponent)
    NO -> loginWithToken(project, parentComponent)
    else -> null
  }

private val GHLoginRequest.isLoginUniqueChecker: UniqueLoginPredicate
  get() = { login, server -> !isCheckLoginUnique || GithubAuthenticationManager.getInstance().isAccountUnique(login, server) }

private fun GHLoginRequest.configure(dialog: BaseLoginDialog) {
  error?.let { dialog.setError(it) }
  server?.let { dialog.setServer(it.toString(), isServerEditable) }
  login?.let { dialog.setLogin(it, isLoginEditable) }
  token?.let { dialog.setToken(it) }
}

private fun BaseLoginDialog.getAuthData(): GHAccountAuthData? {
  DialogManager.show(this)

  return if (isOK) GHAccountAuthData(createAccount(login, server), login, token) else null
}

private fun GHLoginRequest.promptOAuthLogin(project: Project?, parentComponent: Component?): Int =
  if (parentComponent != null) promptOAuthLogin(parentComponent) else promptOAuthLogin(project!!)

private fun GHLoginRequest.promptOAuthLogin(project: Project): Int =
  showYesNoCancelDialog(
    project,
    text ?: message("dialog.message.login.to.continue"), message("login.to.github"),
    message("login.via.github.action"), message("button.use.token"), getCancelButton(),
    getWarningIcon()
  )

private fun GHLoginRequest.promptOAuthLogin(parentComponent: Component): Int =
  showYesNoCancelDialog(
    parentComponent,
    text ?: message("dialog.message.login.to.continue"), message("login.to.github"),
    message("login.via.github.action"), message("button.use.token"), getCancelButton(),
    getWarningIcon()
  )