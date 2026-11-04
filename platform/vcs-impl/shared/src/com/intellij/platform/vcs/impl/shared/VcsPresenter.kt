// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vcs.VcsKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Gives the UI presentation of a VCS
 */
@ApiStatus.Internal
interface VcsPresenter {
  /**
   * Returns the presentation of the VCS with the [vcsKey], or null when this presenter does not know the VCS.
   */
  fun getPresentation(vcsKey: VcsKey): VcsPresentation?

  companion object {
    private val EP_NAME: ExtensionPointName<VcsPresenter> = ExtensionPointName("com.intellij.vcs.presenter")

    /**
     * Returns null when no presenter knows the VCS.
     */
    @JvmStatic
    fun findPresentation(vcsKey: VcsKey): VcsPresentation? {
      return EP_NAME.computeSafeIfAny { it.getPresentation(vcsKey) }
    }
  }
}

/**
 * @property shortNameWithMnemonic the short name of the VCS with a mnemonic, for menus and action names (e.g. "_Git").
 * @property isWithCustomMenu true when the VCS hides the 'VCS' action group of the main menu.
 */
@ApiStatus.Internal
class VcsPresentation(
  val shortNameWithMnemonic: @Nls String,
  val isWithCustomMenu: Boolean,
)
