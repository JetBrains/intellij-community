// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.util.GHAccessTokenCreator
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubParseException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils
import org.jetbrains.plugins.github.ui.util.Validator
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.net.UnknownHostException
import java.util.function.Supplier
import javax.swing.*

sealed class GithubCredentialsUI {
  abstract fun getPanel(): JPanel
  abstract fun getPreferredFocus(): JComponent
  abstract fun getValidator(): Validator
  abstract fun createExecutor(): GithubApiRequestExecutor
  abstract fun acquireLoginAndToken(server: GithubServerPath,
                                    executor: GithubApiRequestExecutor,
                                    indicator: ProgressIndicator): Pair<String, String>

  abstract fun handleAcquireError(error: Throwable): ValidationInfo
  abstract fun setBusy(busy: Boolean)

  protected val loginButton = JButton(GitBundle.message("login.dialog.button.login")).apply { isVisible = false }
  protected val cancelButton = JButton(Messages.getCancelButton()).apply { isVisible = false }

  open fun setLoginAction(actionListener: ActionListener) {
    loginButton.addActionListener(actionListener)
    loginButton.setMnemonic('l')
  }

  fun setCancelAction(actionListener: ActionListener) {
    cancelButton.addActionListener(actionListener)
    cancelButton.setMnemonic('c')
  }

  fun setLoginButtonVisible(visible: Boolean) {
    loginButton.isVisible = visible
  }

  fun setCancelButtonVisible(visible: Boolean) {
    cancelButton.isVisible = visible
  }

  internal class PasswordUI(private val serverTextField: ExtendableTextField,
                            private val clientName: String,
                            switchUi: () -> Unit,
                            private val executorFactory: GithubApiRequestExecutor.Factory,
                            private val isAccountUnique: (login: String, server: GithubServerPath) -> Boolean,
                            private val dialogMode: Boolean) : GithubCredentialsUI() {
    private val loginTextField = JBTextField()
    private val passwordField = JPasswordField()
    private val switchUiLink = LinkLabel.create(GithubBundle.message("login.use.token"), switchUi)

    fun setLogin(login: String, editable: Boolean = true) {
      loginTextField.text = login
      loginTextField.isEditable = editable
    }

    fun setPassword(password: String) {
      passwordField.text = password
    }

    override fun setLoginAction(actionListener: ActionListener) {
      super.setLoginAction(actionListener)
      passwordField.setEnterPressedAction(actionListener)
      loginTextField.setEnterPressedAction(actionListener)
      serverTextField.setEnterPressedAction(actionListener)
    }

    override fun getPanel(): JPanel = panel {
      buildTitleAndLinkRow(this, dialogMode, switchUiLink)
      row(GithubBundle.message("credentials.server.field")) { serverTextField(pushX, growX) }
      row(GithubBundle.message("credentials.login.field")) { loginTextField(pushX, growX) }
      row(GithubBundle.message("credentials.password.field")) {
        passwordField(comment = GithubBundle.message("credentials.password.not.saved"),
                      constraints = *arrayOf(pushX, growX))
      }
      row("") {
        cell {
          loginButton()
          cancelButton()
        }
      }
    }.apply {
      border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
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
      val token = GHAccessTokenCreator(server, executor, indicator).createMaster(clientName).token
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
        else -> ValidationInfo(GithubBundle.message(GithubBundle.message("credentials.invalid.auth.data", error.message.orEmpty())))
          .withOKEnabled()
      }
    }

    override fun setBusy(busy: Boolean) {
      loginTextField.isEnabled = !busy
      passwordField.isEnabled = !busy
      switchUiLink.isEnabled = !busy
    }
  }

  internal class TokenUI(val factory: GithubApiRequestExecutor.Factory,
                         val isAccountUnique: (name: String, server: GithubServerPath) -> Boolean,
                         private val serverTextField: ExtendableTextField,
                         switchUi: () -> Unit,
                         private val dialogMode: Boolean) : GithubCredentialsUI() {

    private val tokenTextField = JBTextField()
    private val switchUiLink = LinkLabel.create(GithubBundle.message("login.use.credentials"), switchUi)
    private var fixedLogin: String? = null

    fun setToken(token: String) {
      tokenTextField.text = token
    }

    override fun getPanel() = panel {
      buildTitleAndLinkRow(this, dialogMode, switchUiLink)
      row(GithubBundle.message("credentials.server.field")) { serverTextField(pushX, growX) }
      row(GithubBundle.message("credentials.token.field")) {
        tokenTextField(
          comment = GithubBundle.message("login.insufficient.scopes", GHSecurityUtil.MASTER_SCOPES),
          constraints = *arrayOf(pushX, growX))
      }
      row("") {
        cell {
          loginButton()
          cancelButton()
        }
      }
    }.apply {
      border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
    }

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
      switchUiLink.isEnabled = !busy
    }

    fun setFixedLogin(fixedLogin: String?) {
      this.fixedLogin = fixedLogin
    }

    override fun setLoginAction(actionListener: ActionListener) {
      super.setLoginAction(actionListener)
      tokenTextField.setEnterPressedAction(actionListener)
      serverTextField.setEnterPressedAction(actionListener)
    }
  }
}

private fun buildTitleAndLinkRow(layoutBuilder: LayoutBuilder,
                                 dialogMode: Boolean,
                                 linkLabel: LinkLabel<*>) {
  layoutBuilder.row {
    cell(isFullWidth = true) {
      if (!dialogMode) {
        val jbLabel = JBLabel(GithubBundle.message("login.to.github"), UIUtil.ComponentStyle.LARGE).apply {
          font = JBFont.label().biggerOn(5.0f)
        }
        jbLabel()
      }
      JLabel(" ")(pushX, growX) // just to be able to align link to the right
      linkLabel()
    }
  }
}

private fun JComponent.setEnterPressedAction(actionListener: ActionListener) {
  registerKeyboardAction(actionListener, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED)
}
