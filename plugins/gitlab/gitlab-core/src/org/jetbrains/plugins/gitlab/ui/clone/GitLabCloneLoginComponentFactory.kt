// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.auth.ui.AccountsPanelFactory.Companion.addWarningForEnabledCredentialHelper
import com.intellij.collaboration.auth.ui.AccountsPanelFactory.Companion.addWarningForPersistentCredentials
import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.collaboration.auth.ui.login.TokenLoginInputPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.service
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.empty
import git4idea.config.GitVcsApplicationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginErrorStatusPresenter
import org.jetbrains.plugins.gitlab.authentication.GitLabSecurityUtil
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneLoginEntryViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneLoginViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneTokenLoginViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JButton
import javax.swing.JComponent

internal object GitLabCloneLoginComponentFactory {
  fun create(cs: CoroutineScope, loginVm: GitLabCloneLoginViewModel, cloneVm: GitLabCloneViewModel) = when (loginVm) {
    is GitLabCloneLoginEntryViewModel -> createLoginPanelComponent(cs, loginVm, cloneVm)
    is GitLabCloneTokenLoginViewModel -> createTokenLoginComponent(cs, loginVm, cloneVm)
  }

  private fun createLoginPanelComponent(
    cs: CoroutineScope,
    loginVm: GitLabCloneLoginEntryViewModel,
    cloneVm: GitLabCloneViewModel,
  ): JComponent = HorizontalListPanel().apply {
    val loginViaGLButton =
      JButton(GitLabBundle.message("account.add.popup.text")).apply {
        addActionListener {
          loginVm.loginWithOAuth()
        }
        bindDisabledIn(cs, loginVm.busyState)
      }
    val useTokenLink =
      ActionLink(CollaborationToolsBundle.message("login.label.use.token")) { cloneVm.switchToTokenLoginPanel(null) }

    add(loginViaGLButton)
    add(JBLabel(CollaborationToolsBundle.message("login.option.separator.label")).apply { border = empty(0, 6, 0, 4) })
    add(useTokenLink)
  }

  private fun createTokenLoginComponent(
    cs: CoroutineScope,
    loginVm: GitLabCloneTokenLoginViewModel,
    cloneVm: GitLabCloneViewModel,
  ): JComponent {
    val loginModel = loginVm.tokenLoginModel
    val loginButton = JButton(CollaborationToolsBundle.message("clone.dialog.button.login.mnemonic")).apply {
      bindDisabledIn(cs, loginModel.loginState.map { it is LoginModel.LoginState.Connecting })
    }
    val backLink = LinkLabel<Unit>(IdeBundle.message("button.back"), null) { _, _ -> cloneVm.switchBackFromTokenLogin() }
    val loginInputPanel = TokenLoginInputPanelFactory(loginModel).createIn(
      cs,
      serverFieldDisabled = false,
      tokenNote = CollaborationToolsBundle.message("clone.dialog.insufficient.scopes", GitLabSecurityUtil.MASTER_SCOPES),
      errorPresenter = GitLabLoginErrorStatusPresenter(cs, loginModel, canLogInWithGit = false),
      footer = {
        row("") {
          cell(loginButton)
          cell(backLink)

          addWarningForPersistentCredentials(
            cs,
            service<GitLabAccountManager>().canPersistCredentials,
            ::panel
          ).align(AlignX.RIGHT)

          addWarningForEnabledCredentialHelper(GitVcsApplicationSettings.getInstance().isUseCredentialHelper, ::panel)
            .align(AlignX.RIGHT)
        }
      }
    ).apply {
      border = JBUI.Borders.empty(8, 0, 0, 35)
      registerValidators(cs.nestedDisposable())
    }

    loginButton.addActionListener {
      cs.launch {
        CollaborationToolsUIUtil.validateAndApplyAction(loginInputPanel) {
          loginModel.login()
        }
      }
    }

    return loginInputPanel
  }
}