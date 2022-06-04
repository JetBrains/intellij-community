// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class VcsCloneComponentStub(
  private val project: Project,
  private val checkoutProvider: CheckoutProvider,
  @Nls private val primaryActionText: String = VcsBundle.message("clone.dialog.clone.button")
) : VcsCloneComponent {

  override fun getView(): JComponent {
    val panel = JPanel(BorderLayout()).apply {
      border = JBEmptyBorder(JBUI.insetsLeft(UIUtil.PANEL_REGULAR_INSETS.left))
    }
    // todo: replace with better help text
    // todo: or add additional button closer to vcs combo
    panel.add(JLabel(VcsBundle.message("action.clone.dialog.stub.click.to.continue", primaryActionText)), BorderLayout.NORTH)
    return panel
  }

  override fun doClone(listener: CheckoutProvider.Listener) = checkoutProvider.doCheckout(project, listener)

  override fun isOkEnabled() = true

  override fun doValidateAll() = emptyList<ValidationInfo>()

  override fun getOkButtonText() = primaryActionText

  override fun getPreferredFocusedComponent(): JComponent? {
    // TODO: implement obtaining focus for GitHub
    return null
  }

  override fun dispose() {}
}