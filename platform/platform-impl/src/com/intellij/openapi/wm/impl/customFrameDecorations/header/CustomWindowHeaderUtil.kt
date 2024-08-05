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
import com.intellij.openapi.wm.impl.RootPaneUtil
import com.intellij.openapi.wm.impl.ToolbarHolder
import com.intellij.openapi.wm.impl.X11UiUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationPath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SelectedEditorFilePath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ToolbarFrameHeader
import com.intellij.openapi.wm.impl.headertoolbar.HeaderClickTransparentListener
import com.intellij.platform.ide.menu.ActionAwareIdeMenuBar
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.platform.ide.menu.createIdeMainMenuActionGroup
import com.intellij.platform.ide.menu.createMacMenuBar
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ToolbarService
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations.CustomTitleBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLayeredPane
import javax.swing.JMenuBar
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

  internal fun installCustomHeader(
    parentCs: CoroutineScope,
    frame: JFrame,
    rootPane: JRootPane,
    mainMenuActionGroup: ActionGroup?,
    isAlwaysCompact: Boolean,
    isFullScreen: () -> Boolean,
  ): FrameHeaderHelper {
    val uiSettings = UISettings.getInstance()
    val isDecoratedMenu = isDecoratedMenu(uiSettings)
    val isFloatingMenuBarSupported = isFloatingMenuBarSupported
    return if (!isDecoratedMenu && !isFloatingMenuBarSupported) {
      createMacAwareMenuBar(parentCs.childScope(), frame, rootPane, mainMenuActionGroup)
      val headerHelper = FrameHeaderHelper.Undecorated(isFloatingMenuBarSupported = false)
      if (SystemInfoRt.isXWindow && !isMenuButtonInToolbar(uiSettings)) {
        val menuBar = RootPaneUtil.createMenuBar(parentCs.childScope(), frame, mainMenuActionGroup).apply {
          isOpaque = true
        }
        installMenuBar(rootPane, menuBar)
      }
      headerHelper
    }
    else {
      val headerHelper = if (isDecoratedMenu) {
        val selectedEditorFilePath: SelectedEditorFilePath?
        val ideMenu: ActionAwareIdeMenuBar
        val customFrameTitlePane = if (ExperimentalUI.isNewUI()) {
          selectedEditorFilePath = null
          ideMenu = createMacAwareMenuBar(parentCs.childScope(), frame, rootPane, mainMenuActionGroup)
          if (SystemInfoRt.isMac) {
            MacToolbarFrameHeader(parentCs.childScope(), frame, rootPane, isAlwaysCompact)
          }
          else {
            ToolbarFrameHeader(parentCs.childScope(), frame, ideMenu as IdeJMenuBar, isAlwaysCompact, isFullScreen)
          }
        }
        else {
          CustomHeader.enableCustomHeader(frame)

          ideMenu = RootPaneUtil.createMenuBar(parentCs.childScope(), frame, mainMenuActionGroup)
          selectedEditorFilePath = CustomDecorationPath(frame)
          MenuFrameHeader(frame, headerTitle = selectedEditorFilePath, ideMenu, isAlwaysCompact)
        }
        val headerHelper = FrameHeaderHelper.Decorated(
          customFrameTitlePane,
          selectedEditorFilePath,
          ideMenu,
          isFloatingMenuBarSupported,
          isAlwaysCompact,
          isFullScreen = isFullScreen)
        rootPane.layeredPane.add(customFrameTitlePane.getComponent(), (JLayeredPane.DEFAULT_LAYER - 3) as Any)
        headerHelper
      }
      else if (hideNativeLinuxTitle(uiSettings)) {
        val ideMenu = RootPaneUtil.createMenuBar(parentCs.childScope(), frame, mainMenuActionGroup)
        val customFrameTitlePane = ToolbarFrameHeader(parentCs.childScope(), frame, ideMenu, isAlwaysCompact, isFullScreen)
        val headerHelper = FrameHeaderHelper.Decorated(
          customFrameTitlePane,
          selectedEditorFilePath = null,
          ideMenu,
          isFloatingMenuBarSupported = true,
          isAlwaysCompact,
          isFullScreen = isFullScreen
        )
        rootPane.layeredPane.add(customFrameTitlePane.getComponent(), (JLayeredPane.DEFAULT_LAYER - 3) as Any)
        headerHelper
      }
      else {
        FrameHeaderHelper.Undecorated(isFloatingMenuBarSupported = true)
      }

      if (isFloatingMenuBarSupported) {
        val menuBar = RootPaneUtil.createMenuBar(parentCs.childScope(), frame, mainMenuActionGroup).apply {
          isOpaque = true
        }
        installMenuBar(rootPane, menuBar)
      }
      headerHelper
    }
  }

  private fun createMacAwareMenuBar(
    coroutineScope: CoroutineScope,
    frame: JFrame,
    rootPane: JRootPane,
    mainMenuActionGroup: ActionGroup?,
  ): ActionAwareIdeMenuBar {
    if (!SystemInfoRt.isMac) {
      return RootPaneUtil.createMenuBar(coroutineScope, frame, mainMenuActionGroup)
    }
    else if (Menu.isJbScreenMenuEnabled()) {
      return createMacMenuBar(coroutineScope, rootPane, frame) { mainMenuActionGroup ?: createIdeMainMenuActionGroup() }
    }
    else {
      val menuBar = RootPaneUtil.createMenuBar(coroutineScope, frame, mainMenuActionGroup)
      rootPane.jMenuBar = menuBar
      return menuBar
    }
  }

  private fun installMenuBar(rootPane: JRootPane, menuBar: JMenuBar) {
    rootPane.jMenuBar = menuBar
    rootPane.layeredPane.add(menuBar, (JLayeredPane.DEFAULT_LAYER - 1) as Any)
  }
}

internal sealed interface FrameHeaderHelper {
  val toolbarHolder: ToolbarHolder?
  val ideMenu: ActionAwareIdeMenuBar?
  val isFloatingMenuBarSupported: Boolean

  fun init(frame: JFrame, pane: JRootPane, coroutineScope: CoroutineScope) {}

  class Undecorated(override val isFloatingMenuBarSupported: Boolean) : FrameHeaderHelper {
    override val toolbarHolder: ToolbarHolder?
      get() = null

    override val ideMenu: ActionAwareIdeMenuBar?
      get() = null

    override fun init(frame: JFrame, pane: JRootPane, coroutineScope: CoroutineScope) {
      ToolbarService.getInstance().setCustomTitleBar(frame, pane, onDispose = { runnable ->
        coroutineScope.coroutineContext.job.invokeOnCompletion {
          runnable.run()
        }
      })
    }
  }

  class Decorated(
    val customFrameTitlePane: MainFrameCustomHeader,
    val selectedEditorFilePath: SelectedEditorFilePath?,
    override val ideMenu: ActionAwareIdeMenuBar,
    override val isFloatingMenuBarSupported: Boolean,
    private val isLightEdit: Boolean,
    private val isFullScreen: () -> Boolean,
  ) : FrameHeaderHelper {
    override val toolbarHolder: ToolbarHolder?
      get() = (customFrameTitlePane as? ToolbarHolder)
        ?.takeIf { ExperimentalUI.isNewUI() && (CustomWindowHeaderUtil.isToolbarInHeader(UISettings.getInstance(), isFullScreen()) || isLightEdit) }
  }
}