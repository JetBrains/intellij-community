// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.HotSwapVisibleStatus
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import com.intellij.xdebugger.XDebuggerBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.Internal
const val NOTIFICATION_TIME_SECONDS: Int = 3

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

  @get:Nls
  val hotSwapButtonAccessibleName: String? get() = XDebuggerBundle.message("xdebugger.hotswap.tooltip.apply")

  @get:Nls
  val toolbarAccessibleName: String? get() = XDebuggerBundle.message("xdebugger.hotswap.toolbar.accessible.name")

  fun announceHotSwapStatus(project: Project, status: HotSwapVisibleStatus) {
    if (status == HotSwapVisibleStatus.Success) {
      AccessibleAnnouncerUtil.announce(null, XDebuggerBundle.message("xdebugger.hotswap.status.success.announcement"), true)
    }
  }

  fun moreAction(): AnAction? = null
  fun popupMenuActions(): DefaultActionGroup? = null

  companion object {
    private val EP_NAME = com.intellij.openapi.extensions.ExtensionPointName<HotSwapUiExtension>("com.intellij.xdebugger.hotSwapUiExtension")

    fun <T> computeSafeIfAvailable(action: (HotSwapUiExtension) -> T): T? = EP_NAME.computeSafeIfAny {
      if (it.isApplicable()) action(it) else null
    }
  }
}
