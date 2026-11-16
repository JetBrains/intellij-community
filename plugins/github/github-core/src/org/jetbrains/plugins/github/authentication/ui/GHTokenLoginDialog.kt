// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.auth.ui.login.LoginModel.LoginState
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY
import com.intellij.openapi.ui.ExitActionType
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import git4idea.i18n.GitBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Component
import javax.swing.JComponent

internal fun JComponent.setPaddingCompensated(): JComponent =
  apply { putClientProperty(IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY, false) }

@Suppress("SplitModeApiUsage")
internal class GHTokenLoginDialog(
  private val loginVm: GHTokenLoginViewModel,
  project: Project?,
  parentCs: CoroutineScope,
  parent: Component?,
  private val isServerEditable: Boolean = true,
) : DialogWrapper(project, parent, false, IdeModalityType.IDE) {
  private val cs = parentCs.childScope(javaClass.name, Dispatchers.EDT + ModalityState.stateForComponent(window).asContextElement())

  init {
    title = GithubBundle.message("login.to.github")
    setLoginButtonText(GitBundle.message("login.dialog.button.login"))
    init()

    cs.launch {
      loginVm.isLoggingIn.collect { isOKActionEnabled = !it }
    }
    cs.launch {
      loginVm.loginState.collect {
        if (it is LoginState.Connected) close(OK_EXIT_CODE, true, ExitActionType.OK)
      }
    }
  }

  fun setLoginButtonText(@NlsContexts.Button text: String) = setOKButtonText(text)

  override fun createCenterPanel(): JComponent = GHTokenLoginPanelFactory.createIn(cs, loginVm, isServerEditable).setPaddingCompensated()

  override fun doOKAction() = loginVm.requestLogin()
}

@ApiStatus.Internal
interface GHLoginModel {
  fun isAccountUnique(server: GithubServerPath, login: String): Boolean
  suspend fun saveLogin(server: GithubServerPath, login: String, token: String)
}