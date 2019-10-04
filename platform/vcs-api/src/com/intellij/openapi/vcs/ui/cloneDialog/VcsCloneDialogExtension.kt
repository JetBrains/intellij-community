// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui.cloneDialog

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * Extension point that provide an ability to add integration with specific cloud repository hosting/service (e.g "GitHub", "BitBucket", etc)
 */
interface VcsCloneDialogExtension {
  companion object {
    val EP_NAME =
      ExtensionPointName.create<VcsCloneDialogExtension>("com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension")
  }

  /**
   * Visible name of extension (e.g. "Repository URL", "GitHub", etc)
   */
  fun getName(): String

  /**
   * Visible icon of extension
   */
  fun getIcon(): Icon

  /**
   * Additional status lines, which may contain some info and actions related to authorized accounts, internal errors, etc
   */
  fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> = emptyList()

  /**
   * Optional tooltip for extension item
   */
  fun getTooltip(): String? = null

  /**
   * Builds [VcsCloneDialogExtensionComponent] that would be displayed on center of get-from-vcs dialog when extension is selected.
   * Will be called lazily and once on first choosing of extension.
   */
  fun createMainComponent(project: Project): VcsCloneDialogExtensionComponent
}