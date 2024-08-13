// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.ide.HelpTooltip
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
  fun showFloatingToolbar(): Boolean = true
  val hotSwapIcon: Icon
  fun createTooltip(): HelpTooltip? = null
  val shouldAddHideButton: Boolean get() = true
  val successStatusLocation: SuccessStatusLocation get() = SuccessStatusLocation.IDE_POPUP

  enum class SuccessStatusLocation {
    IDE_POPUP, NOTIFICATION
  }

  companion object {
    private val EP_NAME = com.intellij.openapi.extensions.ExtensionPointName<HotSwapUiExtension>("com.intellij.xdebugger.hotSwapUiExtension")

    internal fun <T> computeSafeIfAvailable(action: (HotSwapUiExtension) -> T): T? = EP_NAME.computeSafeIfAny {
      if (it.isApplicable()) action(it) else null
    }
  }
}
