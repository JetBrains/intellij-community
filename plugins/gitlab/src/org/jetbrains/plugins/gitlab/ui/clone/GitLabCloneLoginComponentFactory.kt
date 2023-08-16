// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.auth.ui.AccountsPanelFactory.Companion.addWarningForMemoryOnlyPasswordSafe
import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.collaboration.auth.ui.login.TokenLoginInputPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory.Alignment
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.GitLabSecurityUtil
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneLoginViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JButton
import javax.swing.JComponent

@OptIn(ExperimentalCoroutinesApi::class)
internal object GitLabCloneLoginComponentFactory {
  fun create(cs: CoroutineScope, loginVm: GitLabCloneLoginViewModel, cloneVm: GitLabCloneViewModel): JComponent {
    val loginModel = loginVm.tokenLoginModel
    val titlePanel = JBUI.Panels.simplePanel().apply {
      @Suppress("DialogTitleCapitalization")
      val title = JBLabel(GitLabBundle.message("clone.dialog.login.title"), UIUtil.ComponentStyle.LARGE).apply {
        font = JBFont.label().biggerOn(5.0f)
      }
      addToLeft(title)
    }
    val loginButton = JButton(CollaborationToolsBundle.message("clone.dialog.button.login.mnemonic")).apply {
      bindDisabledIn(cs, loginModel.loginState.map { it is LoginModel.LoginState.Connecting })
    }
    val backLink = LinkLabel<Unit>(IdeBundle.message("button.back"), null) { _, _ -> cloneVm.switchToRepositoryList() }.apply {
      bindVisibilityIn(cs, loginVm.accounts.map { it.isNotEmpty() })
    }
    val loginInputPanel = TokenLoginInputPanelFactory(loginModel).create(
      serverFieldDisabled = false,
      tokenNote = CollaborationToolsBundle.message("clone.dialog.insufficient.scopes", GitLabSecurityUtil.MASTER_SCOPES),
      footer = {
        row("") {
          cell(loginButton)
          cell(backLink)

          addWarningForMemoryOnlyPasswordSafe(
            cs,
            service<GitLabAccountManager>().canPersistCredentials,
            ::panel
          )
        }
      }
    ).apply {
      border = JBUI.Borders.empty(8, 0, 0, 35)
      registerValidators(cs.nestedDisposable())
    }

    loginButton.addActionListener {
      cs.launch {
        validateAndApplyAction(loginInputPanel, loginModel::login)
      }
    }

    val errorFlow: Flow<Throwable?> = loginModel.loginState.transformLatest { loginState ->
      when (loginState) {
        LoginModel.LoginState.Connecting -> emit(null)
        is LoginModel.LoginState.Failed -> emit(loginState.error)
        else -> {}
      }
    }
    val errorPresenter = GitLabLoginErrorStatusPresenter()
    val errorPanel = ErrorStatusPanelFactory.create(cs, errorFlow, errorPresenter, Alignment.LEFT)

    return VerticalListPanel().apply {
      border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
      add(titlePanel)
      add(loginInputPanel)
      add(errorPanel)
    }
  }

  private suspend fun validateAndApplyAction(panel: DialogPanel, action: suspend () -> Unit) {
    panel.apply()
    val errors = panel.validateAll()
    if (errors.isEmpty()) {
      action()
      panel.reset()
    }
    else {
      val componentWithError = errors.first().component ?: return
      CollaborationToolsUIUtil.focusPanel(componentWithError)
    }
  }
}