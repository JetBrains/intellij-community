// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui.cloneDialog

import com.intellij.openapi.ui.ValidationInfo
import javax.swing.JComponent

/**
 * Main component for get-from-vcs extensions.
 * It's responsible for:
 * 1. Providing extension-specific UI for get-from-vcs dialog by [getView]
 * 2. handling commands that would be provided from top-level [com.intellij.openapi.ui.DialogWrapper]
 */
interface VcsCloneDialogExtensionComponent {

  /**
   * Return main [JComponent] that will be displayed in center of get-from-vcs dialog when extension is selected.
   *
   * It is called once when extension is selected first time
   */
  fun getView(): JComponent

  /**
   * Checks that is possible to perform ok action in current state of component
   *
   * Would be called form [com.intellij.openapi.ui.DialogWrapper.isOKActionEnabled] when extension is selected
   */
  fun isOkEnabled(): Boolean

  /**
   * Perform primary clone/checkout action.
   */
  fun doClone()

  /**
   * would be called from [com.intellij.openapi.ui.DialogWrapper.doValidateAll] when extension is selected
   *
   * @see com.intellij.openapi.ui.DialogWrapper.doValidateAll
   */
  fun doValidateAll(): List<ValidationInfo>

  /**
   * Text that represents dialog primary action
   */
  fun getOkButtonText(): String = "Clone"
}
