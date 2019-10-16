// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.authentication.util.GithubTokenCreator
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubParseException
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

  protected val loginButton = JButton("Log In").apply { isVisible = false }
  protected val cancelButton = JButton("Cancel").apply { isVisible = false }

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
    private val switchUiLink = LinkLabel.create("Use Token", switchUi)

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
      row("Server:") { serverTextField(pushX, growX) }
      row("Login:") { loginTextField(pushX, growX) }
      row("Password:") {
        passwordField(comment = "The password is not saved and is only used to generate a GitHub token",
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
      { DialogValidationUtils.notBlank(loginTextField, "Login cannot be empty") },
      { DialogValidationUtils.notBlank(passwordField, "Password cannot be empty") })


    override fun createExecutor(): GithubApiRequestExecutor.WithBasicAuth {
      val modalityState = ModalityState.stateForComponent(passwordField)
      return executorFactory.create(loginTextField.text, passwordField.password, Supplier {
        invokeAndWaitIfNeeded(modalityState) {
          Messages.showInputDialog(passwordField,
                                   "Authentication code:",
                                   "GitHub Two-Factor Authentication",
                                   null)
        }
      })
    }

    override fun acquireLoginAndToken(server: GithubServerPath,
                                      executor: GithubApiRequestExecutor,
                                      indicator: ProgressIndicator): Pair<String, String> {
      val login = loginTextField.text.trim()
      if (!isAccountUnique(login, server)) throw LoginNotUniqueException(login)
      val token = GithubTokenCreator(server, executor, indicator).createMaster(clientName).token
      return login to token
    }

    override fun handleAcquireError(error: Throwable): ValidationInfo {
      return when (error) {
        is LoginNotUniqueException -> ValidationInfo("Account already added", loginTextField).withOKEnabled()
        is UnknownHostException -> ValidationInfo("Server is unreachable").withOKEnabled()
        is GithubAuthenticationException -> ValidationInfo("Incorrect credentials. ${error.message.orEmpty()}").withOKEnabled()
        is GithubParseException -> ValidationInfo(error.message ?: "Invalid server path", serverTextField)
        else -> ValidationInfo("Invalid authentication data.\n ${error.message}").withOKEnabled()
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
    private val GIST_SCOPE_PATTERN = Regex("(?:^|, )repo(?:,|$)")
    private val REPO_SCOPE_PATTERN = Regex("(?:^|, )gist(?:,|$)")

    private val tokenTextField = JBTextField()
    private val switchUiLink = LinkLabel.create("Use Credentials", switchUi)
    private var fixedLogin: String? = null

    fun setToken(token: String) {
      tokenTextField.text = token
    }

    override fun getPanel() = panel {
      buildTitleAndLinkRow(this, dialogMode, switchUiLink)
      row("Server:") { serverTextField(pushX, growX) }
      row("Token:") { tokenTextField(pushX, growX) }
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
      DialogValidationUtils.notBlank(tokenTextField, "Token cannot be empty")
    }

    override fun createExecutor() = factory.create(tokenTextField.text)

    override fun acquireLoginAndToken(server: GithubServerPath,
                                      executor: GithubApiRequestExecutor,
                                      indicator: ProgressIndicator): Pair<String, String> {
      var scopes: String? = null
      val login = executor.execute(indicator,
                                   object : GithubApiRequest.Get.Json<GithubAuthenticatedUser>(
                                     GithubApiRequests.getUrl(server,
                                                              GithubApiRequests.CurrentUser.urlSuffix),
                                     GithubAuthenticatedUser::class.java) {
                                     override fun extractResult(response: GithubApiResponse): GithubAuthenticatedUser {
                                       scopes = response.findHeader("X-OAuth-Scopes")
                                       return super.extractResult(response)
                                     }
                                   }.withOperationName("get profile information")).login
      if (scopes.isNullOrEmpty()
          || !GIST_SCOPE_PATTERN.containsMatchIn(scopes!!)
          || !REPO_SCOPE_PATTERN.containsMatchIn(scopes!!)) {
        throw GithubAuthenticationException("Access token should have `repo` and `gist` scopes.")
      }

      fixedLogin?.let {
        if (it != login) throw GithubAuthenticationException("Token should match username \"$it\"")
      }

      if (!isAccountUnique(login, server)) throw LoginNotUniqueException(login)
      return login to tokenTextField.text
    }

    override fun handleAcquireError(error: Throwable): ValidationInfo {
      return when (error) {
        is LoginNotUniqueException -> ValidationInfo("Account ${error.login} already added").withOKEnabled()
        is UnknownHostException -> ValidationInfo("Server is unreachable").withOKEnabled()
        is GithubAuthenticationException -> ValidationInfo("Incorrect credentials. ${error.message.orEmpty()}").withOKEnabled()
        is GithubParseException -> ValidationInfo(error.message ?: "Invalid server path", serverTextField)
        else -> ValidationInfo("Invalid authentication data.\n ${error.message}").withOKEnabled()
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
        val jbLabel = JBLabel("Log In to GitHub", UIUtil.ComponentStyle.LARGE).apply {
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
