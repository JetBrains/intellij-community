// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUtil
import javax.swing.JComponent

private fun getGHAccounts(): Collection<GithubAccount> =
  GithubAuthenticationManager.getInstance().getAccounts().filter { it.server.isGithubDotCom }

class GHCloneDialogExtension : BaseCloneDialogExtension() {
  override fun getName() = GithubUtil.SERVICE_DISPLAY_NAME

  override fun getAccounts(): Collection<GithubAccount> = getGHAccounts()

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent =
    object : BaseCloneDialogExtensionComponent(
      project,
      GithubAuthenticationManager.getInstance(),
      GithubApiRequestExecutorManager.getInstance(),
      GithubAccountInformationProvider.getInstance(),
      CachingGithubUserAvatarLoader.getInstance(),
      GithubImageResizer.getInstance()
    ) {

      init {
        setup()
      }

      override fun getAccounts(): Collection<GithubAccount> = getGHAccounts()

      override fun createLoginPanel(account: GithubAccount?, cancelHandler: () -> Unit): JComponent =
        GHCloneDialogLoginPanel(account).apply {
          loginPanel.isCancelVisible = getAccounts().isNotEmpty()
          loginPanel.setCancelHandler(cancelHandler)
        }
    }
}

private class GHCloneDialogLoginPanel(account: GithubAccount?) : BorderLayoutPanel() {
  val loginPanel = CloneDialogLoginPanel(account)

  private val titlePanel =
    simplePanel().apply {
      val title = JBLabel(message("login.to.github"), ComponentStyle.LARGE).apply { font = JBFont.label().biggerOn(5.0f) }
      addToLeft(title)
      addToRight(loginPanel.createSwitchUiLink())
    }

  init {
    addToTop(titlePanel.apply { border = JBEmptyBorder(getRegularPanelInsets().apply { bottom = 0 }) })
    addToCenter(loginPanel)
  }
}