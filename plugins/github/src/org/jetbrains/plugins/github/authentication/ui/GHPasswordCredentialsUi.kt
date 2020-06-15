// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages.showInputDialog
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.layout.*
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.util.GHAccessTokenCreator
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil.DEFAULT_CLIENT_NAME
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubParseException
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank
import java.net.UnknownHostException
import java.util.function.Supplier
import javax.swing.JPasswordField

internal class GHPasswordCredentialsUi(
  private val serverTextField: ExtendableTextField,
  private val executorFactory: GithubApiRequestExecutor.Factory,
  private val isAccountUnique: UniqueLoginPredicate
) : GHCredentialsUi() {

  private val loginTextField = JBTextField()
  private val passwordField = JPasswordField()

  fun setLogin(login: String, editable: Boolean = true) {
    loginTextField.text = login
    loginTextField.isEditable = editable
  }

  fun setPassword(password: String) {
    passwordField.text = password
  }

  override fun LayoutBuilder.centerPanel() {
    row(message("credentials.server.field")) { serverTextField(pushX, growX) }
    row(message("credentials.login.field")) { loginTextField(pushX, growX) }
    row(message("credentials.password.field")) {
      passwordField(
        comment = message("credentials.password.not.saved"),
        constraints = *arrayOf(pushX, growX)
      )
    }
  }

  override fun getPreferredFocus() = if (loginTextField.isEditable && loginTextField.text.isEmpty()) loginTextField else passwordField

  override fun getValidator() =
    DialogValidationUtils.chain(
      { notBlank(loginTextField, message("credentials.login.cannot.be.empty")) },
      { notBlank(passwordField, message("credentials.password.cannot.be.empty")) }
    )

  override fun createExecutor(): GithubApiRequestExecutor.WithBasicAuth {
    val modalityState = ModalityState.stateForComponent(passwordField)
    return executorFactory.create(loginTextField.text, passwordField.password, Supplier {
      invokeAndWaitIfNeeded(modalityState) {
        showInputDialog(passwordField, message("credentials.2fa.dialog.code.field"), message("credentials.2fa.dialog.title"), null)
      }
    })
  }

  override fun acquireLoginAndToken(
    server: GithubServerPath,
    executor: GithubApiRequestExecutor,
    indicator: ProgressIndicator
  ): Pair<String, String> {
    val login = loginTextField.text.trim()
    if (!isAccountUnique(login, server)) throw LoginNotUniqueException(login)

    val token = GHAccessTokenCreator(server, executor, indicator).createMaster(DEFAULT_CLIENT_NAME).token
    return login to token
  }

  override fun handleAcquireError(error: Throwable): ValidationInfo =
    when (error) {
      is LoginNotUniqueException -> ValidationInfo(message("login.account.already.added", loginTextField.text),
                                                   loginTextField).withOKEnabled()
      is UnknownHostException -> ValidationInfo(message("server.unreachable")).withOKEnabled()
      is GithubAuthenticationException -> ValidationInfo(message("credentials.incorrect", error.message.orEmpty())).withOKEnabled()
      is GithubParseException -> ValidationInfo(error.message ?: message("credentials.invalid.server.path"), serverTextField)
      else -> ValidationInfo(message("credentials.invalid.auth.data", error.message.orEmpty())).withOKEnabled()
    }

  override fun setBusy(busy: Boolean) {
    loginTextField.isEnabled = !busy
    passwordField.isEnabled = !busy
  }
}