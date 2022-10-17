// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ui.cloneDialog

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Extension point that provide an ability to add integration with specific cloud repository hosting/service (e.g "GitHub", "BitBucket", etc)
 */
@ApiStatus.OverrideOnly
interface VcsCloneDialogExtension {
  companion object {
    val EP_NAME =
      ExtensionPointName.create<VcsCloneDialogExtension>("com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension")
  }

  /**
   * Visible name of extension (e.g. "Repository URL", "GitHub", etc)
   */
  @Nls fun getName(): String

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
  @Nls fun getTooltip(): String? = null

  @Deprecated(message = "Implement createMainComponent(Project, ModalityState)")
  fun createMainComponent(project: Project): VcsCloneDialogExtensionComponent {
    throw AssertionError("Shouldn't be called, implement createMainComponent(Project, ModalityState)")
  }

  /**
   * Builds [VcsCloneDialogExtensionComponent] that would be displayed on center of get-from-vcs dialog when extension is selected.
   * Will be called lazily and once on first choosing of extension.
   */
  fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent {
    return createMainComponent(project)
  }
}