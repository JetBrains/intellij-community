// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class VcsCloneComponentStub(private val checkoutProvider: CheckoutProvider,
                            private val primaryActionText: String = "Clone") : VcsCloneComponent {
  override fun getView(): JComponent {
    val panel = JPanel(FlowLayout(FlowLayout.LEADING))
    // todo: replace with better help text
    // todo: or add additional button closer to vcs combo
    panel.add(JLabel("Click \"$primaryActionText\" to continue"))
    return panel
  }

  override fun doClone(project: Project, listener: CheckoutProvider.Listener) = checkoutProvider.doCheckout(project, listener)

  override fun isOkEnabled() = true

  override fun doValidateAll() = emptyList<ValidationInfo>()

  override fun getOkButtonText() = primaryActionText
}