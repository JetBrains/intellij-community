// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * Provides UI and dialog action handling for specific VCS
 */
interface VcsCloneComponent : Disposable {
  /**
   * Component that would be placed on center of dialog panel
   */
  fun getView(): JComponent

  fun doClone(listener: CheckoutProvider.Listener)

  fun isOkEnabled(): Boolean

  fun doValidateAll(): List<ValidationInfo>

  @Nls
  fun getOkButtonText(): String = VcsBundle.message("clone.dialog.clone.button")

  fun getPreferredFocusedComponent(): JComponent?

  @RequiresEdt
  fun onComponentSelected(dialogStateListener: VcsCloneDialogComponentStateListener) {
  }
}
