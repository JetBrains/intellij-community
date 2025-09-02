// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Panel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank
import javax.swing.JComponent
import javax.swing.JTextField

internal typealias UniqueLoginPredicate = (login: String, server: GithubServerPath) -> Boolean

internal class GithubLoginPanel(
  cs: CoroutineScope,
  executorFactory: GithubApiRequestExecutor.Factory,
  isAccountUnique: UniqueLoginPredicate
) : Wrapper() {

  private val serverTextField = ExtendableTextField(GithubServerPath.DEFAULT_HOST, 0)
  private var tokenAcquisitionError: ValidationInfo? = null

  private lateinit var currentUi: GHCredentialsUi
  private var tokenUi = GHTokenCredentialsUi(serverTextField, executorFactory, isAccountUnique)
  private var oauthUi = GHOAuthCredentialsUi(executorFactory, isAccountUnique)

  private val progressIcon = AnimatedIcon.Default()
  private val progressExtension = ExtendableTextComponent.Extension { progressIcon }

  var footer: Panel.() -> Unit
    get() = tokenUi.footer
    set(value) {
      tokenUi.footer = value
      oauthUi.footer = value
      applyUi(currentUi)
    }

  init {
    applyUi(tokenUi)
  }

  private fun applyUi(ui: GHCredentialsUi) {
    currentUi = ui
    setContent(currentUi.getPanel())
    currentUi.getPreferredFocusableComponent()?.requestFocus()
    tokenAcquisitionError = null
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

  suspend fun acquireLoginAndToken(): Pair<String, String> =
    withContext(Dispatchers.Main.immediate + ModalityState.stateForComponent(this).asContextElement()) {
      try {
        setBusy(true)
        tokenAcquisitionError = null
        currentUi.login(getServer())
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
    tokenUi.setFixedLogin(if (editable) null else login)
  }

  fun setError(exception: Throwable?) {
    tokenAcquisitionError = exception?.let { currentUi.handleAcquireError(it) }
  }

  fun setOAuthUi() = applyUi(oauthUi)
  fun setTokenUi() = applyUi(tokenUi)

  @ApiStatus.Internal
  fun isOAuthUi() = currentUi == oauthUi
}