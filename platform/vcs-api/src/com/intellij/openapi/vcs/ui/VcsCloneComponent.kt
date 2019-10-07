// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import javax.swing.JComponent

/**
 * Provides UI and dialog action handling for specific VCS
 */
interface VcsCloneComponent : Disposable {
  /**
   * Component that would be placed on center of dialog panel
   */
  fun getView(): JComponent

  fun doClone(project: Project, listener: CheckoutProvider.Listener)

  fun isOkEnabled(): Boolean

  fun doValidateAll(): List<ValidationInfo>

  fun getOkButtonText(): String = "Clone"

  fun getPreferredFocusedComponent(): JComponent?
}
