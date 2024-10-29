// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.FrameInfoHelper
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.openapi.wm.impl.X11UiUtil
import com.intellij.openapi.wm.impl.headertoolbar.HeaderClickTransparentListener
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations.CustomTitleBar
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane

@ApiStatus.Internal
object CustomWindowHeaderUtil {
  /**
   * Returns `true` if a menu button should be placed in a toolbar instead of a menu bar.
   */
  internal fun isMenuButtonInToolbar(uiSettings: UISettings): Boolean {
    return ExperimentalUI.isNewUI() &&
           (SystemInfoRt.isUnix && !SystemInfoRt.isMac && !uiSettings.separateMainMenu && !hideNativeLinuxTitle(uiSettings) ||
            SystemInfo.isMac && !Menu.isJbScreenMenuEnabled())
  }

  internal fun hideNativeLinuxTitle(uiSettings: UISettings): Boolean {
    return hideNativeLinuxTitleAvailable && hideNativeLinuxTitleSupported && uiSettings.mergeMainMenuWithWindowTitle
  }

  internal val hideNativeLinuxTitleSupported: Boolean
    get() = SystemInfoRt.isUnix && !SystemInfoRt.isMac &&
            ExperimentalUI.isNewUI() &&
            JBR.isWindowMoveSupported() &&
            (StartupUiUtil.isXToolkit() && !X11UiUtil.isWSL() && !X11UiUtil.isTileWM() && !X11UiUtil.isUndefinedDesktop() ||
             StartupUiUtil.isWaylandToolkit())

  internal val hideNativeLinuxTitleAvailable: Boolean
    get() = SystemInfoRt.isUnix && !SystemInfoRt.isMac &&
            ExperimentalUI.isNewUI() &&
            Registry.`is`("ide.linux.hide.native.title", false)

  internal val isFloatingMenuBarSupported: Boolean
    get() = !SystemInfoRt.isMac && FrameInfoHelper.Companion.isFullScreenSupportedInCurrentOs()

  internal fun isDecoratedMenu(uiSettings: UISettings): Boolean {
    return (SystemInfoRt.isWindows || SystemInfoRt.isMac && ExperimentalUI.isNewUI()) &&
           (isToolbarInHeader(uiSettings, false) || IdeFrameDecorator.isCustomDecorationActive())
  }

  internal inline fun isCompactHeader(
    uiSettings: UISettings,
    mainToolbarActionSupplier: () -> List<Pair<ActionGroup, HorizontalLayout.Group>>,
  ): Boolean {
    if (isCompactHeader(uiSettings)) return true
    val mainToolbarHasNoActions = mainToolbarActionSupplier().all { it.first.getChildren(null).isEmpty() }
    return if (SystemInfoRt.isMac) {
      mainToolbarHasNoActions
    }
    else {
      mainToolbarHasNoActions && !uiSettings.separateMainMenu
    }
  }

  internal fun isCompactHeader(uiSettings: UISettings): Boolean {
    return DistractionFreeModeController.shouldMinimizeCustomHeader() || !uiSettings.showNewMainToolbar
  }

  internal fun isToolbarInHeader(uiSettings: UISettings, isFullscreen: Boolean): Boolean {
    if (IdeFrameDecorator.isCustomDecorationAvailable) {
      if (SystemInfoRt.isMac) {
        return true
      }
      if (SystemInfoRt.isWindows && !uiSettings.separateMainMenu && uiSettings.mergeMainMenuWithWindowTitle && !isFullscreen) {
        return true
      }
    }
    if (hideNativeLinuxTitle(UISettings.shadowInstance) && !uiSettings.separateMainMenu && !isFullscreen) {
      return true
    }
    return false
  }

  fun getPreferredWindowHeaderHeight(isCompactHeader: Boolean): Int = JBUI.scale(
    when {
      isCompactHeader -> HEADER_HEIGHT_DFM
      UISettings.getInstance().compactMode -> HEADER_HEIGHT_COMPACT
      else -> HEADER_HEIGHT_NORMAL
    }
  )

  fun configureCustomTitleBar(isCompactHeader: Boolean, customTitleBar: CustomTitleBar, frame: JFrame) {
    customTitleBar.height = getPreferredWindowHeaderHeight(isCompactHeader).toFloat()
    JBR.getWindowDecorations()!!.setCustomTitleBar(frame, customTitleBar)
  }

  internal fun customizeRawFrame(frame: IdeFrameImpl) {
    // some rootPane is required
    val rootPane = JRootPane()
    if (isDecoratedMenu(UISettings.getInstance()) && !isFloatingMenuBarSupported) {
      CustomHeader.enableCustomHeader(frame)
    }
    frame.doSetRootPane(rootPane)
    if (SystemInfoRt.isMac) {
      MacWinTabsHandler.fastInit(frame)
    }
  }

  fun makeComponentToBeMouseTransparentInTitleBar(frameHelper: ProjectFrameHelper, component: JComponent) {
    if (hideNativeLinuxTitle(UISettings.shadowInstance)) {
      WindowMoveListener(frameHelper.component).apply {
        setLeftMouseButtonOnly(true)
        installTo(component)
      }
      return
    }

    val customTitleBar = frameHelper.getCustomTitleBar() ?: return
    val listener = HeaderClickTransparentListener(customTitleBar)
    component.addMouseListener(listener)
    component.addMouseMotionListener(listener)
  }
}