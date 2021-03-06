// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.completionOnEdt
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.Component
import javax.swing.JComponent

internal fun JComponent.setPaddingCompensated(): JComponent =
  apply { putClientProperty(IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY, false) }

internal abstract class BaseLoginDialog(
  project: Project?,
  parent: Component?,
  executorFactory: GithubApiRequestExecutor.Factory,
  isAccountUnique: UniqueLoginPredicate
) : DialogWrapper(project, parent, false, IdeModalityType.PROJECT) {

  protected val loginPanel = GithubLoginPanel(executorFactory, isAccountUnique)

  private var _login = ""
  private var _token = ""

  val login: String get() = _login
  val token: String get() = _token
  val server: GithubServerPath get() = loginPanel.getServer()

  fun setLogin(login: String?, editable: Boolean) = loginPanel.setLogin(login, editable)
  fun setToken(token: String?) = loginPanel.setToken(token)
  fun setServer(path: String, editable: Boolean) = loginPanel.setServer(path, editable)

  fun setError(exception: Throwable) {
    loginPanel.setError(exception)
    startTrackingValidation()
  }

  override fun getPreferredFocusedComponent(): JComponent? = loginPanel.getPreferredFocusableComponent()

  override fun doValidateAll(): List<ValidationInfo> = loginPanel.doValidateAll()

  override fun doOKAction() {
    val modalityState = ModalityState.stateForComponent(loginPanel)
    val emptyProgressIndicator = EmptyProgressIndicator(modalityState)
    Disposer.register(disposable, Disposable { emptyProgressIndicator.cancel() })

    startGettingToken()
    loginPanel.acquireLoginAndToken(emptyProgressIndicator)
      .completionOnEdt(modalityState) { finishGettingToken() }
      .successOnEdt(modalityState) { (login, token) ->
        _login = login
        _token = token

        close(OK_EXIT_CODE, true)
      }
      .errorOnEdt(modalityState) {
        if (!GithubAsyncUtil.isCancellation(it)) startTrackingValidation()
      }
  }

  protected open fun startGettingToken() = Unit
  protected open fun finishGettingToken() = Unit
}