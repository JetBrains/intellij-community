// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.ide.BrowserUtil.browse
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.util.GHAccessTokenCreator
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil.DEFAULT_CLIENT_NAME
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil.buildNewTokenUrl
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubParseException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils
import org.jetbrains.plugins.github.ui.util.Validator
import java.net.UnknownHostException
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

internal sealed class GithubCredentialsUI {
  abstract fun getPreferredFocus(): JComponent
  abstract fun getValidator(): Validator
  abstract fun createExecutor(): GithubApiRequestExecutor
  abstract fun acquireLoginAndToken(server: GithubServerPath,
                                    executor: GithubApiRequestExecutor,
                                    indicator: ProgressIndicator): Pair<String, String>

  abstract fun handleAcquireError(error: Throwable): ValidationInfo
  abstract fun setBusy(busy: Boolean)

  var footer: LayoutBuilder.() -> Unit = { }

  fun getPanel(): JPanel =
    panel {
      centerPanel()
      footer()
    }.apply {
      // Border is required to have more space - otherwise there could be issues with focus ring.
      // `getRegularPanelInsets()` is used to simplify border calculation for dialogs where this panel is used.
      border = JBEmptyBorder(getRegularPanelInsets())
    }

  protected abstract fun LayoutBuilder.centerPanel()

  internal class PasswordUI(
    private val serverTextField: ExtendableTextField,
    private val executorFactory: GithubApiRequestExecutor.Factory,
    private val isAccountUnique: UniqueLoginPredicate
  ) : GithubCredentialsUI() {

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
      row(GithubBundle.message("credentials.server.field")) { serverTextField(pushX, growX) }
      row(GithubBundle.message("credentials.login.field")) { loginTextField(pushX, growX) }
      row(GithubBundle.message("credentials.password.field")) {
        passwordField(comment = GithubBundle.message("credentials.password.not.saved"),
                      constraints = *arrayOf(pushX, growX))
      }
    }

    override fun getPreferredFocus() = if (loginTextField.isEditable && loginTextField.text.isEmpty()) loginTextField else passwordField

    override fun getValidator() = DialogValidationUtils.chain(
      { DialogValidationUtils.notBlank(loginTextField, GithubBundle.message("credentials.login.cannot.be.empty")) },
      { DialogValidationUtils.notBlank(passwordField, GithubBundle.message("credentials.password.cannot.be.empty")) })


    override fun createExecutor(): GithubApiRequestExecutor.WithBasicAuth {
      val modalityState = ModalityState.stateForComponent(passwordField)
      return executorFactory.create(loginTextField.text, passwordField.password, Supplier {
        invokeAndWaitIfNeeded(modalityState) {
          Messages.showInputDialog(passwordField,
                                   GithubBundle.message("credentials.2fa.dialog.code.field"),
                                   GithubBundle.message("credentials.2fa.dialog.title"),
                                   null)
        }
      })
    }

    override fun acquireLoginAndToken(server: GithubServerPath,
                                      executor: GithubApiRequestExecutor,
                                      indicator: ProgressIndicator): Pair<String, String> {
      val login = loginTextField.text.trim()
      if (!isAccountUnique(login, server)) throw LoginNotUniqueException(login)
      val token = GHAccessTokenCreator(server, executor, indicator).createMaster(DEFAULT_CLIENT_NAME).token
      return login to token
    }

    override fun handleAcquireError(error: Throwable): ValidationInfo {
      return when (error) {
        is LoginNotUniqueException -> ValidationInfo(GithubBundle.message("login.account.already.added", loginTextField.text),
                                                     loginTextField).withOKEnabled()
        is UnknownHostException -> ValidationInfo(GithubBundle.message("server.unreachable")).withOKEnabled()
        is GithubAuthenticationException -> ValidationInfo(GithubBundle.message("credentials.incorrect", error.message.orEmpty()))
          .withOKEnabled()
        is GithubParseException -> ValidationInfo(error.message ?: GithubBundle.message("credentials.invalid.server.path"),
                                                  serverTextField)
        else -> ValidationInfo(GithubBundle.message("credentials.invalid.auth.data", error.message.orEmpty())).withOKEnabled()
      }
    }

    override fun setBusy(busy: Boolean) {
      loginTextField.isEnabled = !busy
      passwordField.isEnabled = !busy
    }
  }

  internal class TokenUI(
    private val serverTextField: ExtendableTextField,
    val factory: GithubApiRequestExecutor.Factory,
    val isAccountUnique: UniqueLoginPredicate
  ) : GithubCredentialsUI() {

    private val tokenTextField = JBTextField()
    private var fixedLogin: String? = null

    fun setToken(token: String) {
      tokenTextField.text = token
    }

    override fun LayoutBuilder.centerPanel() {
      row(GithubBundle.message("credentials.server.field")) { serverTextField(pushX, growX) }
      row(GithubBundle.message("credentials.token.field")) {
        cell {
          tokenTextField(
            comment = GithubBundle.message("login.insufficient.scopes", GHSecurityUtil.MASTER_SCOPES),
            constraints = *arrayOf(pushX, growX)
          )
          button(GithubBundle.message("credentials.button.generate")) { browseNewTokenUrl() }
            .enableIf(serverTextField.serverValid)
        }
      }
    }

    private fun browseNewTokenUrl() = browse(buildNewTokenUrl(serverTextField.tryParseServer()!!))

    override fun getPreferredFocus() = tokenTextField

    override fun getValidator(): () -> ValidationInfo? = {
      DialogValidationUtils.notBlank(tokenTextField, GithubBundle.message("login.token.cannot.be.empty"))
    }

    override fun createExecutor() = factory.create(tokenTextField.text)

    override fun acquireLoginAndToken(server: GithubServerPath,
                                      executor: GithubApiRequestExecutor,
                                      indicator: ProgressIndicator): Pair<String, String> {
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

    override fun handleAcquireError(error: Throwable): ValidationInfo {
      return when (error) {
        is LoginNotUniqueException -> ValidationInfo(GithubBundle.message("login.account.already.added", error.login)).withOKEnabled()
        is UnknownHostException -> ValidationInfo(GithubBundle.message("server.unreachable")).withOKEnabled()
        is GithubAuthenticationException -> ValidationInfo(
          GithubBundle.message("credentials.incorrect", error.message.orEmpty())).withOKEnabled()
        is GithubParseException -> ValidationInfo(error.message ?: GithubBundle.message("credentials.invalid.server.path"), serverTextField)
        else -> ValidationInfo(GithubBundle.message("credentials.invalid.auth.data", error.message.orEmpty())).withOKEnabled()
      }
    }

    override fun setBusy(busy: Boolean) {
      tokenTextField.isEnabled = !busy
    }

    fun setFixedLogin(fixedLogin: String?) {
      this.fixedLogin = fixedLogin
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