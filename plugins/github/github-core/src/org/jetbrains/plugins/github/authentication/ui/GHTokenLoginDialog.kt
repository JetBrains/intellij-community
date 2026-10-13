// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY
import com.intellij.openapi.ui.ExitActionType
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import git4idea.i18n.GitBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAuthType
import org.jetbrains.plugins.github.authentication.GHLoginCollector
import org.jetbrains.plugins.github.authentication.GHLoginData
import org.jetbrains.plugins.github.authentication.GHServerOffering
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Component
import javax.swing.JComponent

internal fun JComponent.setPaddingCompensated(): JComponent =
  apply { putClientProperty(IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY, false) }

@Suppress("SplitModeApiUsage")
internal class GHTokenLoginDialog(
  private val model: GHLoginModel,
  project: Project?,
  parentCs: CoroutineScope,
  parent: Component?,
  private val loginData: GHLoginData,
) : DialogWrapper(project, parent, false, IdeModalityType.IDE) {

  private val cs = parentCs.childScope(javaClass.name, Dispatchers.EDT + ModalityState.stateForComponent(window).asContextElement())
  private val loginPanel = GithubTokenLoginPanel(
    GithubApiRequestExecutor.Factory.getInstance(),
    isAccountUnique = { login, server -> model.isAccountUnique(server, login) },
  )

  init {
    title = GithubBundle.message("login.to.github")
    setLoginButtonText(GitBundle.message("login.dialog.button.login"))
    init()
  }

  fun setLoginButtonText(@NlsContexts.Button text: String) = setOKButtonText(text)

  fun setLogin(login: String?, editable: Boolean) = loginPanel.setLogin(login, editable)
  fun setServer(path: String, editable: Boolean) = loginPanel.setServer(path, editable)

  fun setError(exception: Throwable) {
    loginPanel.setError(exception)
    startTrackingValidation()
  }

  override fun createCenterPanel(): JComponent = loginPanel.setPaddingCompensated()

  override fun getPreferredFocusedComponent(): JComponent = loginPanel.getPreferredFocusableComponent()

  override fun doValidateAll(): List<ValidationInfo> = loginPanel.doValidateAll()

  override fun doOKAction() {
    cs.launch(Dispatchers.EDT + ModalityState.stateForComponent(rootPane).asContextElement()) {
      try {
        val (login, token) = loginPanel.acquireLoginAndToken()
        model.saveLogin(loginPanel.getServer(), login, token)
        logEvent()
        close(OK_EXIT_CODE, true, ExitActionType.OK)
      }
      catch (e: Exception) {
        if (e is CancellationException) {
          close(CANCEL_EXIT_CODE, false, ExitActionType.CANCEL)
          throw e
        }
        setError(e)
      }
    }
  }

  private fun logEvent() {
    GHLoginCollector.login(loginData.copy(
      authType = GHAuthType.TOKEN,
      serverOffering = GHServerOffering.of(loginPanel.getServer()))
    )
  }
}

@ApiStatus.Internal
interface GHLoginModel {
  fun isAccountUnique(server: GithubServerPath, login: String): Boolean
  suspend fun saveLogin(server: GithubServerPath, login: String, token: String)
}