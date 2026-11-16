// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.isGHAccount
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.util.GithubUtil
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHCloneDialogExtension : GHCloneDialogExtensionBase() {
  override fun getName() = GithubUtil.SERVICE_DISPLAY_NAME

  override fun getAccounts(): Collection<GithubAccount> = GHAccountsUtil.accounts.filter { it.isGHAccount }

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent =
    project.service<GHCloneDialogExtensionComponentFactory>().createInScope(modalityState) {
      GHCloneDialogExtensionComponent(project, this)
    }
}

private class GHCloneDialogExtensionComponent(project: Project, parentCs: CoroutineScope) : GHCloneDialogExtensionComponentBase(
  project,
  parentCs,
  accountManager = service()
) {

  override fun isAccountHandled(account: GithubAccount): Boolean = account.isGHAccount

  override fun createLoginPanel(cs: CoroutineScope, account: GithubAccount?, cancelHandler: () -> Unit): JComponent =
    GHCloneDialogLoginPanel(cs, project, account).apply {
      val chooseLoginUiHandler = { setChooseLoginUi() }
      loginPanel.setCancelHandler(if (getAccounts().isEmpty()) chooseLoginUiHandler else cancelHandler)
    }.also {
      Disposer.register(this, it)
    }

  override fun createAccountMenuLoginActions(account: GithubAccount?): Collection<AccountMenuItem.Action> =
    listOf(createLoginAction(account), createLoginWithTokenAction(account))

  private fun createLoginAction(account: GithubAccount?): AccountMenuItem.Action {
    val isExistingAccount = account != null
    return AccountMenuItem.Action(
      message("login.via.github.action"),
      { GHAccountsUtil.loginViaOAuth(account, project, content, GHLoginSource.CLONE) },
      showSeparatorAbove = !isExistingAccount
    )
  }

  private fun createLoginWithTokenAction(account: GithubAccount?): AccountMenuItem.Action =
    AccountMenuItem.Action(
      CollaborationToolsBundle.message("clone.dialog.login.with.token.action"),
      {
        switchToLogin(account)
        getLoginPanel()?.setTokenUi()
      }
    )

  private fun getLoginPanel(): GHCloneDialogLoginPanel? = content as? GHCloneDialogLoginPanel
}

private class GHCloneDialogLoginPanel(
  cs: CoroutineScope,
  private val project: Project,
  private val account: GithubAccount?,
) : JBPanel<GHCloneDialogLoginPanel>(ListLayout.vertical(0)), Disposable {
  private val titlePanel =
    simplePanel().apply {
      val title = JBLabel(message("login.to.github"), ComponentStyle.LARGE).apply { font = JBFont.label().biggerOn(5.0f) }
      addToLeft(title)
    }
  private val contentPanel = Wrapper()

  private val chooseLoginUiPanel: JPanel =
    panel {
      border = JBEmptyBorder(getRegularPanelInsets())

      row {
        cell(HorizontalListPanel().apply {
          val loginViaGHButton = JButton(message("login.via.github.action")).apply {
            addActionListener {
              GHAccountsUtil.loginViaOAuth(account, project, this@GHCloneDialogLoginPanel, GHLoginSource.CLONE)
            }
          }
          val useTokenLink = ActionLink(CollaborationToolsBundle.message("login.label.use.token")) { setTokenUi() }

          add(loginViaGHButton)
          add(JBLabel(CollaborationToolsBundle.message("login.option.separator.label")).apply { border = empty(0, 6, 0, 4) })
          add(useTokenLink)
        })
      }

      AccountsPanelFactory.addWarningForPersistentCredentials(
        cs,
        service<GHAccountManager>().canPersistCredentials,
        ::panel
      ).customize(UnscaledGaps(top = 5))
    }

  val loginPanel = CloneDialogLoginPanel(cs, account, server = GithubServerPath.DEFAULT_HOST).also {
    Disposer.register(this, it)
  }

  init {
    add(titlePanel.apply { border = JBEmptyBorder(getRegularPanelInsets().apply { bottom = 0 }) })
    add(contentPanel)

    setChooseLoginUi()
  }

  fun setChooseLoginUi() = setContent(chooseLoginUiPanel)

  fun setTokenUi() {
    setContent(loginPanel)
    loginPanel.focusPanel() // after `loginPanel` is set as content to ensure correct focus behavior
  }

  private fun setContent(content: JComponent) {
    contentPanel.setContent(content)

    revalidate()
    repaint()
  }

  override fun dispose() = Unit
}