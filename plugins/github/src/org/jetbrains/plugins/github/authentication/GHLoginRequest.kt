// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.openapi.project.Project
import git4idea.DialogManager
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager.Companion.createAccount
import org.jetbrains.plugins.github.authentication.ui.GithubLoginDialog
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
  val dialog = GithubLoginDialog(
    GithubApiRequestExecutor.Factory.getInstance(),
    project,
    parentComponent,
    { login, server -> !isCheckLoginUnique || GithubAuthenticationManager.getInstance().isAccountUnique(login, server) },
    message = text
  )

  error?.let { dialog.withError(it) }
  server?.let { dialog.withServer(it.toString(), isServerEditable) }
  login?.let { dialog.withLogin(it, isLoginEditable) }
  password?.let { dialog.withPassword(it) }
  token?.let { dialog.withToken(it) }

  DialogManager.show(dialog)
  if (!dialog.isOK) return null

  return GHAccountAuthData(createAccount(dialog.login, dialog.server), dialog.login, dialog.token)
}