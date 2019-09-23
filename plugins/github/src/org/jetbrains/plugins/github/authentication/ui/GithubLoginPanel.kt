// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.Wrapper
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.util.GithubTokenCreator
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils
import org.jetbrains.plugins.github.ui.util.Validator
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.submitBackgroundTask
import java.awt.event.ActionListener
import java.util.concurrent.CompletableFuture
import javax.swing.JTextField

class GithubLoginPanel(executorFactory: GithubApiRequestExecutor.Factory,
                       isAccountUnique: (name: String, server: GithubServerPath) -> Boolean,
                       val project: Project?,
                       isDialogMode: Boolean = true) : Wrapper() {
  private var clientName: String = GithubTokenCreator.DEFAULT_CLIENT_NAME
  private val serverTextField = ExtendableTextField(GithubServerPath.DEFAULT_HOST, 0)
  private var tokenAcquisitionError: ValidationInfo? = null

  private lateinit var currentUi: GithubCredentialsUI
  private var passwordUi = GithubCredentialsUI.PasswordUI(serverTextField, clientName, ::switchToTokenUI, executorFactory, isAccountUnique,
                                                          isDialogMode)
  private var tokenUi = GithubCredentialsUI.TokenUI(executorFactory, isAccountUnique, serverTextField, ::switchToPasswordUI, isDialogMode)

  private val progressIcon = AnimatedIcon.Default()
  private val progressExtension = ExtendableTextComponent.Extension { progressIcon }

  init {
    applyUi(passwordUi)
  }

  private fun switchToPasswordUI() {
    applyUi(passwordUi)
  }

  private fun switchToTokenUI() {
    applyUi(tokenUi)
  }

  private fun applyUi(ui: GithubCredentialsUI) {
    currentUi = ui
    setContent(currentUi.getPanel())
    currentUi.getPreferredFocus().requestFocus()
    tokenAcquisitionError = null
  }

  fun getPreferredFocus() = currentUi.getPreferredFocus()

  fun doValidateAll(): List<ValidationInfo> {
    return listOf(DialogValidationUtils.chain(
      DialogValidationUtils.chain(
        { DialogValidationUtils.notBlank(serverTextField, "Server cannot be empty") },
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

  private fun setBusy(busy: Boolean) {
    if (busy) {
      if (!serverTextField.extensions.contains(progressExtension))
        serverTextField.addExtension(progressExtension)
    }
    else {
      serverTextField.removeExtension(progressExtension)
    }
    serverTextField.isEnabled = !busy
    currentUi.setBusy(busy)
  }

  fun acquireLoginAndToken(progressIndicator: ProgressIndicator): CompletableFuture<Pair<String, String>> {
    setBusy(true)
    tokenAcquisitionError = null

    val server = getServer()
    val executor = currentUi.createExecutor()

    return service<ProgressManager>()
      .submitBackgroundTask(project, "Not Visible", true, progressIndicator) {
        currentUi.acquireLoginAndToken(server, executor, it)
      }.whenComplete { _, throwable ->
        runInEdt {
          setBusy(false)
          if (throwable != null && !GithubAsyncUtil.isCancellation(throwable)) {
            tokenAcquisitionError = currentUi.handleAcquireError(throwable)
          }
        }
      }
  }

  fun getServer(): GithubServerPath = GithubServerPath.from(
    serverTextField.text.trim())

  fun setServer(path: String, editable: Boolean = true) {
    serverTextField.apply {
      text = path
      isEditable = editable
    }
  }

  fun setCredentials(login: String? = null, password: String? = null, editableLogin: Boolean = true) {
    if (login != null) {
      passwordUi.setLogin(login, editableLogin)
      tokenUi.setFixedLogin(if (editableLogin) null else login)
    }
    if (password != null) passwordUi.setPassword(password)
    applyUi(passwordUi)
  }

  fun setToken(token: String? = null) {
    if (token != null) tokenUi.setToken(token)
    applyUi(tokenUi)
  }

  fun setError(exception: Throwable) {
    tokenAcquisitionError = currentUi.handleAcquireError(exception)
  }

  fun setLoginListener(listener: ActionListener) {
    passwordUi.setLoginAction(listener)
    tokenUi.setLoginAction(listener)
  }

  fun setCancelListener(listener: ActionListener) {
    passwordUi.setCancelAction(listener)
    tokenUi.setCancelAction(listener)
  }

  fun setLoginButtonVisible(visible: Boolean) {
    passwordUi.setLoginButtonVisible(visible)
    tokenUi.setLoginButtonVisible(visible)
  }

  fun setCancelButtonVisible(visible: Boolean) {
    passwordUi.setCancelButtonVisible(visible)
    tokenUi.setCancelButtonVisible(visible)
  }
}