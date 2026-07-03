// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
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
  fun configureTooltip(tooltip: HelpTooltip, status: HotSwapVisibleStatus) {
    val text = if (status is HotSwapVisibleStatus.ChangesNotHotSwappable) {
      XDebuggerBundle.message("xdebugger.hotswap.tooltip.not.hot.swappable")
    }
    else {
      @Suppress("DialogTitleCapitalization")
      XDebuggerBundle.message("xdebugger.hotswap.tooltip.apply")
    }
    val description = if (status is HotSwapVisibleStatus.ChangesNotHotSwappable) {
      formatHotSwapReasonForTooltip(status.reason)
    }
    else {
      HtmlChunk.text(XDebuggerBundle.message("xdebugger.hotswap.tooltip.description"))
    }
    tooltip.setPlainTextTitle(text)
    tooltip.setDescription(description)
  }

  val shouldAddHideButton: Boolean get() = true
  val shouldAddText: Boolean get() = true

  fun hotSwapButtonAccessibleName(status: HotSwapVisibleStatus): @Nls String {
    return if (status is HotSwapVisibleStatus.ChangesNotHotSwappable) {
      XDebuggerBundle.message("xdebugger.hotswap.not.hot.swappable.accessible.name")
    }
    else {
      XDebuggerBundle.message("xdebugger.hotswap.tooltip.apply")
    }
  }

  @get:Nls
  val toolbarAccessibleName: String? get() = XDebuggerBundle.message("xdebugger.hotswap.toolbar.accessible.name")

  fun announceHotSwapStatus(project: Project, status: HotSwapVisibleStatus) {
    val message = when (status) {
      HotSwapVisibleStatus.Success -> XDebuggerBundle.message("xdebugger.hotswap.status.success.announcement")
      is HotSwapVisibleStatus.ChangesNotHotSwappable -> XDebuggerBundle.message("xdebugger.hotswap.status.not.hot.swappable.announcement")
      else -> return
    }
    AccessibleAnnouncerUtil.announce(null, message, true)
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

internal fun formatHotSwapReasonForTooltip(reason: @NlsSafe String): HtmlChunk =
  HtmlChunk.fragment(
    HtmlChunk.raw(reason),
    HtmlChunk.br(),
    HtmlChunk.br(),
    HtmlChunk.text(XDebuggerBundle.message("xdebugger.hotswap.tooltip.not.hot.swappable.description")),
  )
