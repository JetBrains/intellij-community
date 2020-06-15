// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.ide.BrowserUtil.browse
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.layout.*
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil.buildNewTokenUrl
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubParseException
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank
import org.jetbrains.plugins.github.ui.util.Validator
import java.net.UnknownHostException
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

internal class GHTokenCredentialsUi(
  private val serverTextField: ExtendableTextField,
  val factory: GithubApiRequestExecutor.Factory,
  val isAccountUnique: UniqueLoginPredicate
) : GHCredentialsUi() {

  private val tokenTextField = JBTextField()
  private var fixedLogin: String? = null

  fun setToken(token: String) {
    tokenTextField.text = token
  }

  override fun LayoutBuilder.centerPanel() {
    row(message("credentials.server.field")) { serverTextField(pushX, growX) }
    row(message("credentials.token.field")) {
      cell {
        tokenTextField(
          comment = message("login.insufficient.scopes", GHSecurityUtil.MASTER_SCOPES),
          constraints = *arrayOf(pushX, growX)
        )
        button(message("credentials.button.generate")) { browseNewTokenUrl() }
          .enableIf(serverTextField.serverValid)
      }
    }
  }

  private fun browseNewTokenUrl() = browse(buildNewTokenUrl(serverTextField.tryParseServer()!!))

  override fun getPreferredFocus() = tokenTextField

  override fun getValidator(): Validator = { notBlank(tokenTextField, message("login.token.cannot.be.empty")) }

  override fun createExecutor() = factory.create(tokenTextField.text)

  override fun acquireLoginAndToken(
    server: GithubServerPath,
    executor: GithubApiRequestExecutor,
    indicator: ProgressIndicator
  ): Pair<String, String> {
    val (details, scopes) = GHSecurityUtil.loadCurrentUserWithScopes(executor, indicator, server)
    if (scopes == null || !GHSecurityUtil.isEnoughScopes(scopes))
      throw GithubAuthenticationException("Insufficient scopes granted to token.")

    val login = details.login
    fixedLogin?.let {
      if (it != login) throw GithubAuthenticationException("Token should match username \"$it\"")
    }

    if (!isAccountUnique(login, server)) throw LoginNotUniqueException(login)
    return login to tokenTextField.text
  }

  override fun handleAcquireError(error: Throwable): ValidationInfo =
    when (error) {
      is LoginNotUniqueException -> ValidationInfo(message("login.account.already.added", error.login)).withOKEnabled()
      is UnknownHostException -> ValidationInfo(message("server.unreachable")).withOKEnabled()
      is GithubAuthenticationException -> ValidationInfo(message("credentials.incorrect", error.message.orEmpty())).withOKEnabled()
      is GithubParseException -> ValidationInfo(error.message ?: message("credentials.invalid.server.path"), serverTextField)
      else -> ValidationInfo(message("credentials.invalid.auth.data", error.message.orEmpty())).withOKEnabled()
    }

  override fun setBusy(busy: Boolean) {
    tokenTextField.isEnabled = !busy
  }

  fun setFixedLogin(fixedLogin: String?) {
    this.fixedLogin = fixedLogin
  }
}

private val JTextField.serverValid: ComponentPredicate
  get() = object : ComponentPredicate() {
    override fun invoke(): Boolean = tryParseServer() != null

    override fun addListener(listener: (Boolean) -> Unit) =
      document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) = listener(tryParseServer() != null)
      })
  }

private fun JTextField.tryParseServer(): GithubServerPath? =
  try {
    GithubServerPath.from(text.trim())
  }
  catch (e: GithubParseException) {
    null
  }