// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI.PanelFactory.grid
import com.intellij.util.ui.UI.PanelFactory.panel
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.GithubApiTaskExecutor
import org.jetbrains.plugins.github.api.GithubApiUtil
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.GithubTask
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubParseException
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.chain
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank
import org.jetbrains.plugins.github.ui.util.Validator
import org.jetbrains.plugins.github.util.GithubUtil
import java.awt.Cursor
import java.net.UnknownHostException
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

class GithubLoginDialog @JvmOverloads constructor(private val project: Project,
                                                  parent: JComponent? = null,
                                                  private val isAccountUnique: (name: String, server: GithubServerPath) -> Boolean = { _, _ -> true },
                                                  title: String = "Log In to Github",
                                                  private val message: String? = null)
  : DialogWrapper(project, parent, false, IdeModalityType.PROJECT) {

  private val serverTextField = ExtendableTextField(GithubApiUtil.DEFAULT_GITHUB_HOST)
  private var centerPanel = Wrapper()

  private var southAdditionalPanel = Wrapper().apply { JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP) }

  private var passwordUi = LoginPasswordCredentialsUI()
  private var tokenUi = TokenCredentialsUI()

  private lateinit var currentUi: CredentialsUI

  private var progressIndicator: ProgressIndicator? = null
  private val progressIcon = AnimatedIcon.Default()
  private val progressExtension = ExtendableTextComponent.Extension({ progressIcon })

  private lateinit var login: String
  private lateinit var token: String

  var tokenNote = GithubUtil.DEFAULT_TOKEN_NOTE
  private var tokenAcquisitionError: ValidationInfo? = null

  init {
    this.title = title
    setOKButtonText("Log In")
    applyUi(passwordUi)
    init()
    Disposer.register(disposable, Disposable { progressIndicator?.cancel() })
  }

  @JvmOverloads
  fun withServer(path: String, editable: Boolean = true): GithubLoginDialog {
    serverTextField.apply {
      text = path
      isEditable = editable
    }
    return this
  }

  @JvmOverloads
  fun withCredentials(login: String? = null, password: String? = null): GithubLoginDialog {
    if (login != null) passwordUi.setLogin(login)
    if (password != null) passwordUi.setPassword(password)
    applyUi(passwordUi)
    return this
  }

  @JvmOverloads
  fun withToken(token: String? = null): GithubLoginDialog {
    if (token != null) tokenUi.setToken(token)
    applyUi(tokenUi)
    return this
  }

  fun withError(exception: Throwable): GithubLoginDialog {
    tokenAcquisitionError = currentUi.handleAcquireError(exception)
    startTrackingValidation()
    return this
  }

  fun getServer(): GithubServerPath = GithubServerPath.from(serverTextField.text)
  fun getLogin(): String = login
  fun getToken(): String = token

  override fun doOKAction() {
    setBusy(true)
    tokenAcquisitionError = null

    service<ProgressManager>().runProcessWithProgressAsynchronously(object : Task.Backgroundable(project, "Not Visible") {
      override fun run(indicator: ProgressIndicator) {
        val (newLogin, newToken) = currentUi.acquireLoginAndToken(indicator)
        login = newLogin
        token = newToken
      }

      override fun onSuccess() {
        close(OK_EXIT_CODE, true)
        setBusy(false)
      }

      override fun onThrowable(error: Throwable) {
        startTrackingValidation()
        tokenAcquisitionError = currentUi.handleAcquireError(error)
        setBusy(false)
      }
    }, progressIndicator!!)
  }

  private fun setBusy(busy: Boolean) {
    if (busy) {
      if (!serverTextField.extensions.contains(progressExtension)) serverTextField.addExtension(progressExtension)
      progressIndicator?.cancel()
      progressIndicator = EmptyProgressIndicator(ModalityState.stateForComponent(window))
      Disposer.register(disposable, Disposable { progressIndicator?.cancel() })
    }
    else {
      serverTextField.removeExtension(progressExtension)
      progressIndicator = null
    }
    serverTextField.isEnabled = !busy
    currentUi.setBusy(busy)
  }

  override fun createNorthPanel(): JComponent? {
    return message?.let {
      JTextArea().apply {
        font = UIUtil.getLabelFont()
        text = it
        isEditable = false
        isFocusable = false
        isOpaque = false
        border = JBUI.Borders.emptyBottom(UIUtil.DEFAULT_VGAP * 2)
        margin = JBUI.emptyInsets()
      }
    }
  }

  override fun createSouthAdditionalPanel() = southAdditionalPanel
  override fun createCenterPanel() = centerPanel
  override fun getPreferredFocusedComponent() = currentUi.getPreferredFocus()

  private fun applyUi(ui: CredentialsUI) {
    currentUi = ui
    centerPanel.setContent(currentUi.getPanel())
    southAdditionalPanel.setContent(currentUi.getSouthPanel())
    setErrorText(null)
    currentUi.getPreferredFocus().requestFocus()
    tokenAcquisitionError = null
  }

  override fun doValidateAll(): List<ValidationInfo> {
    return listOf(chain(chain({ notBlank(serverTextField, "Server cannot be empty") },
                              serverPathValidator(serverTextField)),
                        currentUi.getValidator()),
                  { tokenAcquisitionError })
      .mapNotNull { it() }
  }

  private fun serverPathValidator(textField: JTextField): Validator {
    return {
      val text = textField.text
      try {
        GithubServerPath.from(text)
        null
      }
      catch (e: Exception) {
        ValidationInfo("$text is not a valid server path:\n${e.message}", textField)
      }
    }
  }

  private inner class LoginPasswordCredentialsUI : CredentialsUI() {
    private val loginTextField = JBTextField()
    private val passwordField = JPasswordField()
    private val contextHelp = JEditorPane()

    init {
      contextHelp.apply {
        editorKit = UIUtil.getHTMLEditorKit()
        val linkColor = JBColor.link()
        //language=CSS
        (editorKit as HTMLEditorKit).styleSheet.addRule("a {color: rgb(${linkColor.red}, ${linkColor.green}, ${linkColor.blue})}")
        //language=HTML
        text = "<html>Password is not saved and used only to <br>acquire Github token. <a href=''>Enter token</a></html>"
        addHyperlinkListener { e ->
          if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            applyUi(tokenUi)
          }
          else {
            cursor = if (e.eventType == HyperlinkEvent.EventType.ENTERED) Cursor(Cursor.HAND_CURSOR)
            else Cursor(Cursor.DEFAULT_CURSOR)
          }
        }
        isEditable = false
        isFocusable = false
        isOpaque = false
        border = null
        margin = JBUI.emptyInsets()
        foreground = JBColor.GRAY
      }
    }

    fun setLogin(login: String) {
      loginTextField.text = login
    }

    fun setPassword(password: String) {
      passwordField.text = password
    }

    override fun getPanel() = grid()
      .add(panel(serverTextField).withLabel("Server:"))
      .add(panel(loginTextField).withLabel("Login:"))
      .add(panel(passwordField).withLabel("Password:"))
      .add(panel(contextHelp)).createPanel()

    override fun getPreferredFocus() = loginTextField

    override fun getValidator() = chain({ notBlank(loginTextField, "Login cannot be empty") },
                                        { notBlank(passwordField, "Password cannot be empty") })

    override fun getSouthPanel() = JBUI.Panels.simplePanel()
      .addToCenter(LinkLabel.create("Sign up for Github", Runnable { BrowserUtil.browse("https://github.com") }))
      .addToRight(JBLabel(AllIcons.Ide.External_link_arrow))

    override fun acquireLoginAndToken(indicator: ProgressIndicator): Pair<String, String> {
      val server = getServer()
      if (!isAccountUnique(loginTextField.text, server)) throw LoginNotUniqueException()

      val token = GithubApiTaskExecutor.execute(indicator, server, loginTextField.text, passwordField.password,
                                                GithubTask { GithubApiUtil.getMasterToken(it, tokenNote) })
      return loginTextField.text to token
    }

    override fun handleAcquireError(error: Throwable): ValidationInfo {
      return when (error) {
        is LoginNotUniqueException -> ValidationInfo("Account already added", loginTextField)
        is UnknownHostException -> ValidationInfo("Server is unreachable", false)
        is GithubAuthenticationException -> ValidationInfo("Incorrect credentials.", false)
        is GithubParseException -> ValidationInfo(error.message ?: "Invalid server path", serverTextField)
        else -> ValidationInfo("Invalid authentication data.\n ${error.message}", false)
      }
    }

    override fun setBusy(busy: Boolean) {
      loginTextField.isEnabled = !busy
      passwordField.isEnabled = !busy
      contextHelp.isEnabled = !busy
    }
  }

  private inner class TokenCredentialsUI : CredentialsUI() {
    private val tokenTextField = JBTextField()
    private val switchUiLink = LinkLabel.create("Log In with Username", { applyUi(passwordUi) })

    fun setToken(token: String) {
      tokenTextField.text = token
    }

    override fun getPanel() = grid()
      .add(panel(serverTextField).withLabel("Server:"))
      .add(panel(tokenTextField).withLabel("Token:")).createPanel()

    override fun getPreferredFocus() = tokenTextField

    override fun getValidator() = { notBlank(tokenTextField, "Token cannot be empty") }

    override fun getSouthPanel() = switchUiLink

    override fun acquireLoginAndToken(indicator: ProgressIndicator): Pair<String, String> {
      val login = GithubApiTaskExecutor.execute(indicator, getServer(), tokenTextField.text,
                                                GithubTask { GithubApiUtil.getCurrentUser(it).login })

      if (!isAccountUnique(login, getServer())) throw LoginNotUniqueException(login)

      return login to tokenTextField.text
    }

    override fun handleAcquireError(error: Throwable): ValidationInfo {
      return when (error) {
        is LoginNotUniqueException -> ValidationInfo("Account ${error.login} already added", false)
        is UnknownHostException -> ValidationInfo("Server is unreachable", false)
        is GithubAuthenticationException -> ValidationInfo("Incorrect credentials.", false)
        is GithubParseException -> ValidationInfo(error.message ?: "Invalid server path", serverTextField)
        else -> ValidationInfo("Invalid authentication data.\n ${error.message}", false)
      }
    }

    override fun setBusy(busy: Boolean) {
      tokenTextField.isEnabled = !busy
      switchUiLink.isEnabled = !busy
    }
  }

  private abstract inner class CredentialsUI {
    abstract fun getPanel(): JPanel
    abstract fun getPreferredFocus(): JComponent
    abstract fun getSouthPanel(): JComponent
    abstract fun getValidator(): Validator
    abstract fun acquireLoginAndToken(indicator: ProgressIndicator): Pair<String, String>
    abstract fun handleAcquireError(error: Throwable): ValidationInfo
    abstract fun setBusy(busy: Boolean)
  }

  private class LoginNotUniqueException(val login: String? = null) : RuntimeException()
}
