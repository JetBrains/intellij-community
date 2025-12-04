// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.MainMenuDisplayMode
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomisedActionGroup
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.headertoolbar.HeaderClickTransparentListener
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.util.system.OS
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
  internal fun isMenuButtonInToolbar(uiSettings: UISettings): Boolean =
    ExperimentalUI.isNewUI() && (
      OS.isGenericUnix() && uiSettings.mainMenuDisplayMode != MainMenuDisplayMode.SEPARATE_TOOLBAR && !hideNativeLinuxTitle(uiSettings) ||
      OS.CURRENT == OS.macOS && !Menu.isJbScreenMenuEnabled()
    )

  internal fun hideNativeLinuxTitle(uiSettings: UISettings): Boolean =
    hideNativeLinuxTitleAvailable && hideNativeLinuxTitleSupported && uiSettings.mergeMainMenuWithWindowTitle

  internal val hideNativeLinuxTitleSupported: Boolean
    get() = OS.isGenericUnix() && ExperimentalUI.isNewUI() && hideNativeLinuxTitleNotSupportedReason == null

  internal enum class HideNativeLinuxTitleNotSupportedReason {
    INCOMPATIBLE_JBR,
    WAYLAND_OR_XTOOLKIT_REQUIRED,
    WSL_NOT_SUPPORTED,
    TILING_WM_NOT_SUPPORTED,
    UNDEFINED_DESKTOP_NOT_SUPPORTED,
  }

  /**
   * Returns `null` if supported
   */
  internal val hideNativeLinuxTitleNotSupportedReason: HideNativeLinuxTitleNotSupportedReason?
    get() = when {
      !JBR.isWindowMoveSupported() -> HideNativeLinuxTitleNotSupportedReason.INCOMPATIBLE_JBR
      StartupUiUtil.isWaylandToolkit() -> null
      !StartupUiUtil.isXToolkit() -> HideNativeLinuxTitleNotSupportedReason.WAYLAND_OR_XTOOLKIT_REQUIRED
      X11UiUtil.isWSL() -> HideNativeLinuxTitleNotSupportedReason.WSL_NOT_SUPPORTED
      X11UiUtil.isTileWM() -> HideNativeLinuxTitleNotSupportedReason.TILING_WM_NOT_SUPPORTED
      X11UiUtil.isUndefinedDesktop() -> HideNativeLinuxTitleNotSupportedReason.UNDEFINED_DESKTOP_NOT_SUPPORTED
      else -> null
    }

  internal val hideNativeLinuxTitleAvailable: Boolean
    get() = OS.isGenericUnix() && ExperimentalUI.isNewUI() && Registry.`is`("ide.linux.hide.native.title", false)

  internal val isFloatingMenuBarSupported: Boolean
    get() = OS.CURRENT != OS.macOS && FrameInfoHelper.isFullScreenSupportedInCurrentOs()

  internal fun isDecoratedMenu(uiSettings: UISettings): Boolean =
    (OS.CURRENT == OS.Windows || OS.CURRENT == OS.macOS && ExperimentalUI.isNewUI()) &&
    (isToolbarInHeader(uiSettings, false) || IdeFrameDecorator.isCustomDecorationActive())

  internal inline fun isCompactHeader(mainToolbarActionSupplier: () -> List<Pair<ActionGroup, HorizontalLayout.Group>>): Boolean {
    if (isCompactHeader()) {
      return true
    }

    val mainToolbarHasNoActions = mainToolbarActionSupplier().all {
      when (val g = it.first) {
        is DefaultActionGroup -> isEmptyExceptNonRemovableActions(g.childActionsOrStubs)
        is CustomisedActionGroup -> isEmptyExceptNonRemovableActions(g.defaultChildrenOrStubs)
        else -> false
      }
    }
    return if (OS.CURRENT == OS.macOS) {
      mainToolbarHasNoActions
    }
    else {
      mainToolbarHasNoActions && UISettings.getInstance().mainMenuDisplayMode != MainMenuDisplayMode.SEPARATE_TOOLBAR
    }
  }

  internal fun isCompactHeader(): Boolean =
    LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred &&
    (DistractionFreeModeController.shouldMinimizeCustomHeader() || !UISettings.getInstance().showNewMainToolbar)

  private fun isEmptyExceptNonRemovableActions(actions: Array<AnAction>): Boolean {
    val manager = ActionManager.getInstance()
    for (action in actions) {
      val id = manager.getId(action)
      //return false
      if (id !in NON_REMOVABLE_ACTIONS) return false
    }
    return true
  }

  private val NON_REMOVABLE_ACTIONS = setOf(
    "TrialStateWidget"
  )

  internal fun isToolbarInHeader(uiSettings: UISettings, isFullscreen: Boolean): Boolean {
    if (IdeFrameDecorator.isCustomDecorationAvailable) {
      if (OS.CURRENT == OS.macOS) {
        return true
      }
      if (OS.CURRENT == OS.Windows && uiSettings.mainMenuDisplayMode != MainMenuDisplayMode.SEPARATE_TOOLBAR && uiSettings.mergeMainMenuWithWindowTitle && !isFullscreen) {
        return true
      }
    }
    if (hideNativeLinuxTitle(UISettings.shadowInstance) && uiSettings.mainMenuDisplayMode != MainMenuDisplayMode.SEPARATE_TOOLBAR && !isFullscreen) {
      return true
    }
    return false
  }

  fun getPreferredWindowHeaderHeight(isCompactHeader: Boolean): Int = JBUI.scale(
    getPreferredWindowHeaderHeightUnscaled(isCompactHeader)
  )

  fun getPreferredWindowHeaderHeightUnscaled(isCompactHeader: Boolean): Int = when {
    isCompactHeader -> HEADER_HEIGHT_DFM
    UISettings.shadowInstance.compactMode -> HEADER_HEIGHT_COMPACT
    else -> HEADER_HEIGHT_NORMAL
  }

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
    if (OS.CURRENT == OS.macOS) {
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
