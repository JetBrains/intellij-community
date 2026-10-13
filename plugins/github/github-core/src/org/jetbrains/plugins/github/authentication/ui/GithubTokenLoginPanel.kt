// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.ide.BrowserUtil.browse
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil.buildNewTokenUrl
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubParseException
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank
import java.net.UnknownHostException
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

internal typealias UniqueLoginPredicate = (login: String, server: GithubServerPath) -> Boolean

internal class GithubTokenLoginPanel(
  private val executorFactory: GithubApiRequestExecutor.Factory,
  private val isAccountUnique: UniqueLoginPredicate,
  footer: Panel.() -> Unit = {},
) : Wrapper() {

  private val serverTextField = ExtendableTextField(GithubServerPath.DEFAULT_HOST, 0)
  private val tokenTextField = JBPasswordField()
  private var fixedLogin: String? = null
  private var tokenAcquisitionError: ValidationInfo? = null
  private val progressIcon = AnimatedIcon.Default()
  private val progressExtension = ExtendableTextComponent.Extension { progressIcon }

  init {
    setContent(createPanel(footer))
  }

  private fun createPanel(footer: Panel.() -> Unit): JPanel =
    panel {
      row(message("credentials.server.field")) { cell(serverTextField).align(AlignX.FILL) }
      row(message("credentials.token.field")) {
        cell(tokenTextField)
          .comment(CollaborationToolsBundle.message("clone.dialog.insufficient.scopes", GHSecurityUtil.MASTER_SCOPES), maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
          .align(AlignX.FILL)
          .resizableColumn()
        button(message("credentials.button.generate")) { browseNewTokenUrl() }
          .enabledIf(serverTextField.serverValid)
      }
      footer()
    }.apply {
      // Border is required to have more space - otherwise there could be issues with focus ring.
      // `getRegularPanelInsets()` is used to simplify border calculation for dialogs where this panel is used.
      border = JBEmptyBorder(getRegularPanelInsets())
    }

  private fun browseNewTokenUrl() = browse(buildNewTokenUrl(serverTextField.tryParseServer()!!))

  fun getPreferredFocusableComponent(): JComponent =
    serverTextField.takeIf { it.isEditable && it.text.isBlank() }
    ?: tokenTextField

  fun doValidateAll(): List<ValidationInfo> {
    val uiError =
      notBlank(serverTextField, message("credentials.server.cannot.be.empty"))
      ?: validateServerPath(serverTextField)
      ?: notBlank(tokenTextField, message("login.token.cannot.be.empty"))

    return listOfNotNull(uiError, tokenAcquisitionError)
  }

  private fun validateServerPath(field: JTextField): ValidationInfo? =
    try {
      GithubServerPath.from(field.text)
      null
    }
    catch (_: Exception) {
      ValidationInfo(message("credentials.server.path.invalid"), field)
    }

  private fun setBusy(busy: Boolean) {
    serverTextField.apply { if (busy) addExtension(progressExtension) else removeExtension(progressExtension) }
    serverTextField.isEnabled = !busy
    tokenTextField.isEnabled = !busy
  }

  suspend fun acquireLoginAndToken(): Pair<String, String> =
    withContext(Dispatchers.Main.immediate + ModalityState.stateForComponent(this).asContextElement()) {
      try {
        setBusy(true)
        tokenAcquisitionError = null
        val server = getServer()
        val token = tokenTextField.text
        val executor = executorFactory.create(server, token)
        val login = GHAccountsUtil.acquireLogin(server, executor, isAccountUnique, fixedLogin)
        login to token
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        setError(e)
        throw e
      }
      finally {
        setBusy(false)
      }
    }

  fun getServer(): GithubServerPath = GithubServerPath.from(serverTextField.text.trim())

  fun setServer(path: String, editable: Boolean) {
    serverTextField.text = path
    serverTextField.isEditable = editable
  }

  fun setLogin(login: String?, editable: Boolean) {
    fixedLogin = if (editable) null else login
  }

  fun setError(exception: Throwable?) {
    tokenAcquisitionError = exception?.let { handleAcquireError(it) }
  }

  private fun handleAcquireError(error: Throwable): ValidationInfo =
    when (error) {
      is GithubParseException -> ValidationInfo(error.message ?: message("credentials.invalid.server.path"), serverTextField)
      is LoginNotUniqueException -> ValidationInfo(message("login.account.already.added", error.login)).withOKEnabled()
      is UnknownHostException -> ValidationInfo(message("server.unreachable")).withOKEnabled()
      is GithubAuthenticationException -> ValidationInfo(message("credentials.incorrect", error.message.orEmpty())).withOKEnabled()
      else -> ValidationInfo(message("credentials.invalid.auth.data", error.message.orEmpty())).withOKEnabled()
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
  catch (_: GithubParseException) {
    null
  }