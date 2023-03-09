// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.ide.BrowserUtil.browse
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.layout.ComponentPredicate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

internal class GHTokenCredentialsUi(
  private val serverTextField: ExtendableTextField,
  val factory: GithubApiRequestExecutor.Factory,
  val isAccountUnique: UniqueLoginPredicate
) : GHCredentialsUi() {

  private val tokenTextField = JBPasswordField()
  private var fixedLogin: String? = null

  override fun Panel.centerPanel() {
    row(message("credentials.server.field")) { cell(serverTextField).align(AlignX.FILL) }
    row(message("credentials.token.field")) {
      cell(tokenTextField)
        .comment(message("login.insufficient.scopes", GHSecurityUtil.MASTER_SCOPES))
        .align(AlignX.FILL)
        .resizableColumn()
      button(message("credentials.button.generate")) { browseNewTokenUrl() }
        .enabledIf(serverTextField.serverValid)
    }
  }

  private fun browseNewTokenUrl() = browse(buildNewTokenUrl(serverTextField.tryParseServer()!!))

  override fun getPreferredFocusableComponent(): JComponent = tokenTextField

  override fun getValidator(): Validator = { notBlank(tokenTextField, message("login.token.cannot.be.empty")) }

  override suspend fun login(server: GithubServerPath): Pair<String, String> = withContext(Dispatchers.Main.immediate) {
    val token = tokenTextField.text
    val executor = factory.create(token)
    val login = acquireLogin(server, executor, isAccountUnique, fixedLogin)
    login to token
  }

  override fun handleAcquireError(error: Throwable): ValidationInfo =
    when (error) {
      is GithubParseException -> ValidationInfo(error.message ?: message("credentials.invalid.server.path"), serverTextField)
      else -> handleError(error)
    }

  override fun setBusy(busy: Boolean) {
    tokenTextField.isEnabled = !busy
  }

  fun setFixedLogin(fixedLogin: String?) {
    this.fixedLogin = fixedLogin
  }

  companion object {
    suspend fun acquireLogin(
      server: GithubServerPath,
      executor: GithubApiRequestExecutor,
      isAccountUnique: UniqueLoginPredicate,
      fixedLogin: String?
    ): String {
      val (details, scopes) = withContext(Dispatchers.IO) {
        coroutineToIndicator {
          GHSecurityUtil.loadCurrentUserWithScopes(executor, server)
        }
      }
      if (scopes == null || !GHSecurityUtil.isEnoughScopes(scopes))
        throw GithubAuthenticationException("Insufficient scopes granted to token.")

      val login = details.login
      if (fixedLogin != null && fixedLogin != login) throw GithubAuthenticationException("Token should match username \"$fixedLogin\"")
      if (!isAccountUnique(login, server)) throw LoginNotUniqueException(login)

      return login
    }

    fun handleError(error: Throwable): ValidationInfo =
      when (error) {
        is LoginNotUniqueException -> ValidationInfo(message("login.account.already.added", error.login)).withOKEnabled()
        is UnknownHostException -> ValidationInfo(message("server.unreachable")).withOKEnabled()
        is GithubAuthenticationException -> ValidationInfo(message("credentials.incorrect", error.message.orEmpty())).withOKEnabled()
        else -> ValidationInfo(message("credentials.invalid.auth.data", error.message.orEmpty())).withOKEnabled()
      }
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