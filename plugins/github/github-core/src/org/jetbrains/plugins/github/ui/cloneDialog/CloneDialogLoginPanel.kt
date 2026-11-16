// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts.ENTER
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import git4idea.config.GitVcsApplicationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAuthType
import org.jetbrains.plugins.github.authentication.GHLoginCollector
import org.jetbrains.plugins.github.authentication.GHLoginData
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.GHServerOffering
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GHTokenLoginPanelFactory
import org.jetbrains.plugins.github.authentication.ui.GHTokenLoginViewModel
import javax.swing.JButton
import javax.swing.SwingConstants

internal class CloneDialogLoginPanel(
  parentCs: CoroutineScope,
  private val account: GithubAccount?,
  server: String = account?.server?.toUrl() ?: GithubServerPath.DEFAULT_HOST,
  needsWarning: Boolean = false,
) :
  JBPanel<CloneDialogLoginPanel>(ListLayout.vertical(0)),
  Disposable {
  private val cs = parentCs.childScope(javaClass.name)
  private val accountManager get() = service<GHAccountManager>()

  private val tokenLoginVm = GHTokenLoginViewModel(
    cs,
    GithubApiRequestExecutor.Factory.getInstance(),
    server = server,
    fixedLogin = account?.name,
    isAccountUnique = { name, serverPath -> if (account == null) accountManager.isAccountUnique(serverPath, name) else true },
    saveLogin = { serverPath, login, token ->
      val acc = account ?: GHAccountManager.createAccount(login, serverPath)
      accountManager.updateAccount(acc, token)
      logEvent(serverPath)
    }
  )

  private val loginButton = JButton(CollaborationToolsBundle.message("clone.dialog.button.login.mnemonic")).apply {
    bindDisabledIn(cs, tokenLoginVm.isLoggingIn)
  }
  private val backLink = LinkLabel<Any?>(IdeBundle.message("button.back"), null).apply {
    verticalAlignment = SwingConstants.CENTER
  }
  private val tokenLoginPanel = GHTokenLoginPanelFactory.createIn(cs, tokenLoginVm, footer = { buttonPanel(needsWarning) }).apply {
    registerValidators(this@CloneDialogLoginPanel)
  }

  var isCancelVisible: Boolean
    get() = backLink.isVisible
    set(value) {
      backLink.isVisible = value
    }

  init {
    add(tokenLoginPanel)

    loginButton.addActionListener { login() }
    LoginAction().registerCustomShortcutSet(ENTER, tokenLoginPanel)
  }

  fun setCancelHandler(listener: () -> Unit) =
    backLink.setListener(
      { _, _ ->
        cancelLogin()
        listener()
      },
      null
    )

  fun focusPanel() {
    tokenLoginPanel.preferredFocusedComponent?.requestFocus()
  }

  override fun dispose() = cs.cancel()

  private fun Panel.buttonPanel(needsWarning: Boolean) =
    row("") {
      cell(loginButton)
      cell(backLink)

      if (needsWarning) {
        AccountsPanelFactory
          .addWarningForPersistentCredentials(cs, accountManager.canPersistCredentials, ::panel)
          .align(AlignX.RIGHT)
      }
      AccountsPanelFactory
        .addWarningForEnabledCredentialHelper(GitVcsApplicationSettings.getInstance().isUseCredentialHelper, ::panel)
        .align(AlignX.RIGHT)
    }

  fun cancelLogin() = tokenLoginVm.cancelLogin()

  private fun login() =
    CollaborationToolsUIUtil.validateAndApplyAction(tokenLoginPanel) {
      tokenLoginVm.requestLogin()
    }

  private fun logEvent(server: GithubServerPath) {
    GHLoginCollector.login(GHLoginData(
      loginSource = GHLoginSource.CLONE,
      authType = GHAuthType.TOKEN,
      serverOffering = GHServerOffering.of(server),
    ))
  }

  private inner class LoginAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.getData(CONTEXT_COMPONENT) != backLink
    }

    override fun actionPerformed(e: AnActionEvent) = login()
  }
}