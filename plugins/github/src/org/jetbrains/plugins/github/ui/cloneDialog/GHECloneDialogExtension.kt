// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GithubUtil
import javax.swing.JComponent
import javax.swing.JPanel

class GHECloneDialogExtension : BaseCloneDialogExtension() {
  override fun getName(): String = GithubUtil.ENTERPRISE_SERVICE_DISPLAY_NAME

  override fun getAccounts(): Collection<GithubAccount> =
    GithubAuthenticationManager.getInstance().getAccounts().filterNot { it.server.isGithubDotCom }

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent =
    object : VcsCloneDialogExtensionComponent() {
      override fun getView(): JComponent = JPanel()

      override fun doClone(checkoutListener: CheckoutProvider.Listener) = Unit

      override fun doValidateAll(): List<ValidationInfo> = emptyList()

      override fun onComponentSelected() = Unit
    }
}