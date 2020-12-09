// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.*
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank
import org.jetbrains.plugins.github.util.completionOnEdt
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JTextField

internal typealias UniqueLoginPredicate = (login: String, server: GithubServerPath) -> Boolean

internal class GithubLoginPanel(
  executorFactory: GithubApiRequestExecutor.Factory,
  isAccountUnique: UniqueLoginPredicate
) : Wrapper() {

  private val serverTextField = ExtendableTextField(GithubServerPath.DEFAULT_HOST, 0)
  private var tokenAcquisitionError: ValidationInfo? = null

  private lateinit var currentUi: GHCredentialsUi
  private var passwordUi = GHPasswordCredentialsUi(serverTextField, executorFactory, isAccountUnique)
  private var tokenUi = GHTokenCredentialsUi(serverTextField, executorFactory, isAccountUnique)
  private var oauthUi = GHOAuthCredentialsUi(executorFactory, isAccountUnique)

  private val progressIcon = AnimatedIcon.Default()
  private val progressExtension = ExtendableTextComponent.Extension { progressIcon }

  var footer: LayoutBuilder.() -> Unit
    get() = tokenUi.footer
    set(value) {
      passwordUi.footer = value
      tokenUi.footer = value
      oauthUi.footer = value
      applyUi(currentUi)
    }

  init {
    applyUi(passwordUi)
  }

  private fun applyUi(ui: GHCredentialsUi) {
    currentUi = ui
    setContent(currentUi.getPanel())
    currentUi.getPreferredFocusableComponent()?.requestFocus()
    tokenAcquisitionError = null
  }

  fun createSwitchUiLink(): LinkLabel<*> {
    @NlsContexts.LinkLabel
    fun switchUiText(): String = if (currentUi == passwordUi) message("login.use.token") else message("login.use.credentials")
    fun nextUi(): GHCredentialsUi = if (currentUi == passwordUi) tokenUi else passwordUi

    return LinkLabel<Any?>(switchUiText(), null) { link, _ ->
      applyUi(nextUi())
      link.text = switchUiText()
    }
  }

  fun getPreferredFocusableComponent(): JComponent? =
    serverTextField.takeIf { it.isEditable && it.text.isBlank() }
    ?: currentUi.getPreferredFocusableComponent()

  fun doValidateAll(): List<ValidationInfo> {
    val uiError =
      notBlank(serverTextField, message("credentials.server.cannot.be.empty"))
      ?: validateServerPath(serverTextField)
      ?: currentUi.getValidator().invoke()

    return listOfNotNull(uiError, tokenAcquisitionError)
  }

  private fun validateServerPath(field: JTextField): ValidationInfo? =
    try {
      GithubServerPath.from(field.text)
      null
    }
    catch (e: Exception) {
      ValidationInfo(message("credentials.server.path.invalid"), field)
    }

  private fun setBusy(busy: Boolean) {
    serverTextField.apply { if (busy) addExtension(progressExtension) else removeExtension(progressExtension) }
    serverTextField.isEnabled = !busy

    currentUi.setBusy(busy)
  }

  fun acquireLoginAndToken(progressIndicator: ProgressIndicator): CompletableFuture<Pair<String, String>> {
    setBusy(true)
    tokenAcquisitionError = null

    val server = getServer()
    val executor = currentUi.createExecutor()

    return service<ProgressManager>()
      .submitIOTask(progressIndicator) { currentUi.acquireLoginAndToken(server, executor, it) }
      .completionOnEdt(progressIndicator.modalityState) { setBusy(false) }
      .errorOnEdt(progressIndicator.modalityState) { setError(it) }
  }

  fun getServer(): GithubServerPath = GithubServerPath.from(serverTextField.text.trim())

  fun setServer(path: String, editable: Boolean) {
    serverTextField.text = path
    serverTextField.isEditable = editable
  }

  fun setLogin(login: String?, editable: Boolean) {
    passwordUi.setLogin(login.orEmpty(), editable)
    tokenUi.setFixedLogin(if (editable) null else login)
  }

  fun setPassword(password: String?) = passwordUi.setPassword(password.orEmpty())
  fun setToken(token: String?) = tokenUi.setToken(token.orEmpty())

  fun setError(exception: Throwable) {
    tokenAcquisitionError = currentUi.handleAcquireError(exception)
  }

  fun setOAuthUi() = applyUi(oauthUi)
  fun setPasswordUi() = applyUi(passwordUi)
  fun setTokenUi() = applyUi(tokenUi)
}