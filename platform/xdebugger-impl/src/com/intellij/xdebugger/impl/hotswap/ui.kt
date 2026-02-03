// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Extension point to configure UI for different IDEs.
 */
@ApiStatus.Internal
interface HotSwapUiExtension {
  /**
   * @return true if this EP is applicable for the current IDE
   */
  fun isApplicable(): Boolean = true
  @ApiStatus.Obsolete
  fun showFloatingToolbar(): Boolean = true
  fun showFloatingToolbar(project: Project): Boolean = showFloatingToolbar()
  val hotSwapIcon: Icon
  fun createTooltip(): HelpTooltip? = null
  val shouldAddHideButton: Boolean get() = true
  val shouldAddText: Boolean get() = true
  fun moreAction(): AnAction? = null
  fun popupMenuActions(): DefaultActionGroup? = null

  companion object {
    private val EP_NAME = com.intellij.openapi.extensions.ExtensionPointName<HotSwapUiExtension>("com.intellij.xdebugger.hotSwapUiExtension")

    fun <T> computeSafeIfAvailable(action: (HotSwapUiExtension) -> T): T? = EP_NAME.computeSafeIfAny {
      if (it.isApplicable()) action(it) else null
    }
  }
}
