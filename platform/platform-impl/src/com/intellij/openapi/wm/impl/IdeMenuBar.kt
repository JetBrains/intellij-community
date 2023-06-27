// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.DynamicBundle
import com.intellij.diagnostic.runActivity
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFloatingMenuBarSupported
import com.intellij.ui.Gray
import com.intellij.ui.ScreenUtil
import com.intellij.ui.mac.foundation.NSDefaults
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.mac.screenmenu.MenuBar
import com.intellij.ui.plaf.beg.IdeaMenuUI
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CoroutineScope
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import javax.swing.*
import javax.swing.border.Border

internal enum class IdeMenuBarState {
  EXPANDED,
  COLLAPSING,
  COLLAPSED,
  EXPANDING,
  TEMPORARY_EXPANDED;

  val isInProgress: Boolean
    get() = this == COLLAPSING || this == EXPANDING
}

@Suppress("LeakingThis")
open class IdeMenuBar internal constructor(@JvmField internal val coroutineScope: CoroutineScope,
                                           private val frame: JFrame) : JMenuBar(), ActionAwareIdeMenuBar {
  private val menuBarHelper: IdeMenuBarHelper

  @JvmField
  internal var activated = false
  private val screenMenuPeer: MenuBar?

  init {
    val flavor = if (isFloatingMenuBarSupported) {
      FloatingMenuBarFlavor(this)
    }
    else {
      object : IdeMenuFlavor {
        override var state = IdeMenuBarState.EXPANDED

        override fun updateAppMenu() {
          doUpdateAppMenu()
        }
      }
    }

    val facade = object : IdeMenuBarHelper.MenuBarImpl {
      override val frame: JFrame
        get() = this@IdeMenuBar.frame
      override val coroutineScope: CoroutineScope
        get() = this@IdeMenuBar.coroutineScope
      override val isDarkMenu: Boolean
        get() = this@IdeMenuBar.isDarkMenu
      override val component: JComponent
        get() = this@IdeMenuBar

      override fun updateGlobalMenuRoots() {
        this@IdeMenuBar.updateGlobalMenuRoots()
      }

      override suspend fun getMainMenuActionGroup(): ActionGroup? = getMainMenuActionGroupAsync()
    }

    screenMenuPeer = runActivity("ide menu bar init") { createScreeMenuPeer(frame) }
    if (screenMenuPeer == null) {
      menuBarHelper = IdeMenuBarHelper(flavor = flavor, menuBar = facade)
    }
    else {
      menuBarHelper = PeerBasedIdeMenuBarHelper(screenMenuPeer = screenMenuPeer, flavor = flavor, menuBar = facade)
    }

    if (IdeFrameDecorator.isCustomDecorationActive()) {
      isOpaque = false
    }
  }

  override fun add(menu: JMenu): JMenu {
    menu.isFocusable = false
    return super.add(menu)
  }

  override fun getBorder(): Border? {
    @Suppress("UNNECESSARY_SAFE_CALL")
    val state = menuBarHelper?.flavor?.state ?: IdeMenuBarState.EXPANDED
    // avoid moving lines
    if (state == IdeMenuBarState.EXPANDING || state == IdeMenuBarState.COLLAPSING) {
      return JBUI.Borders.empty()
    }

    if (IdeFrameDecorator.isCustomDecorationActive()) {
      return JBUI.Borders.empty()
    }

    // fix for a Darcula double border
    if (state == IdeMenuBarState.TEMPORARY_EXPANDED && StartupUiUtil.isUnderDarcula) {
      return JBUI.Borders.customLine(Gray._75, 0, 0, 1, 0)
    }

    // save 1px for mouse handler
    if (state == IdeMenuBarState.COLLAPSED) {
      return JBUI.Borders.emptyBottom(1)
    }

    val uiSettings = UISettings.getInstance()
    return if (uiSettings.showMainToolbar || uiSettings.showNavigationBar) super.getBorder() else null
  }

  override fun paint(g: Graphics) {
    // otherwise, there will be a 1px line on top
    if (menuBarHelper.flavor.state != IdeMenuBarState.COLLAPSED) {
      super.paint(g)
    }
  }

  override fun doLayout() {
    super.doLayout()

    menuBarHelper.flavor.layoutClockPanelAndButton()
  }

  override fun menuSelectionChanged(isIncluded: Boolean) {
    if (!isIncluded && menuBarHelper.flavor.state == IdeMenuBarState.TEMPORARY_EXPANDED) {
      activated = false
      menuBarHelper.flavor.state = IdeMenuBarState.COLLAPSING
      menuBarHelper.flavor.restartAnimator()
      return
    }

    if (isIncluded && menuBarHelper.flavor.state == IdeMenuBarState.COLLAPSED) {
      activated = true
      menuBarHelper.flavor.state = IdeMenuBarState.TEMPORARY_EXPANDED
      revalidate()
      repaint()
      SwingUtilities.invokeLater {
        val menu = getMenu(selectionModel.selectedIndex)
        if (menu.isPopupMenuVisible) {
          menu.isPopupMenuVisible = false
          menu.isPopupMenuVisible = true
        }
      }
    }
    super.menuSelectionChanged(isIncluded)
  }

  internal val isActivated: Boolean
    get() {
      val index = selectionModel.selectedIndex
      return index != -1 && getMenu(index).isPopupMenuVisible
    }

  override fun getPreferredSize(): Dimension {
    val dimension = super.getPreferredSize()
    val state = menuBarHelper.flavor.state
    if (state.isInProgress) {
      val progress = menuBarHelper.flavor.getProgress()
      dimension.height = COLLAPSED_HEIGHT +
                         ((if (state == IdeMenuBarState.COLLAPSING) 1 - progress else progress) * (dimension.height - COLLAPSED_HEIGHT)).toInt()
    }
    else if (state == IdeMenuBarState.COLLAPSED) {
      dimension.height = COLLAPSED_HEIGHT
    }
    return dimension
  }

  override fun removeNotify() {
    screenMenuPeer?.let {
      @Suppress("SSBasedInspection")
      it.dispose()
    }
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      menuBarHelper.flavor.suspendAnimator()
    }
    super.removeNotify()
  }

  override suspend fun updateMenuActions(forceRebuild: Boolean) {
    menuBarHelper.updateMenuActions(forceRebuild)
  }

  internal open val isDarkMenu: Boolean
    get() = SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar()

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (isOpaque) {
      paintBackground(g)
    }
  }

  private fun paintBackground(g: Graphics) {
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      val window = SwingUtilities.getWindowAncestor(this)
      if (window is IdeFrame && !(window as IdeFrame).isInFullScreen) {
        return
      }
    }
    if (StartupUiUtil.isUnderDarcula || StartupUiUtil.isUnderIntelliJLaF()) {
      g.color = IdeaMenuUI.getMenuBackgroundColor()
      g.fillRect(0, 0, width, height)
    }
  }

  override fun paintChildren(g: Graphics) {
    if (menuBarHelper.flavor.state.isInProgress) {
      val g2 = g as Graphics2D
      val oldTransform = g2.transform
      val newTransform = if (oldTransform == null) AffineTransform() else AffineTransform(oldTransform)
      newTransform.concatenate(AffineTransform.getTranslateInstance(0.0, (height - super.getPreferredSize().height).toDouble()))
      g2.transform = newTransform
      super.paintChildren(g2)
      g2.transform = oldTransform
    }
    else if (menuBarHelper.flavor.state != IdeMenuBarState.COLLAPSED) {
      super.paintChildren(g)
    }
  }

  open suspend fun getMainMenuActionGroupAsync(): ActionGroup? {
    val rootPane = frame.rootPane
    val group = if (rootPane is IdeRootPane) rootPane.mainMenuActionGroup else null
    return group ?:
    ApplicationManager.getApplication().serviceAsync<CustomActionsSchema>().getCorrectedAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup?
  }

  protected open fun getMainMenuActionGroup(): ActionGroup? {
    val rootPane = frame.rootPane
    val group = if (rootPane is IdeRootPane) rootPane.mainMenuActionGroup else null
    return group ?: CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup?
  }

  override fun getMenuCount(): Int {
    @Suppress("IfThenToElvis", "SENSELESS_COMPARISON")
    return if (menuBarHelper == null) 0 else menuBarHelper.flavor.correctMenuCount(super.getMenuCount())
  }

  internal open fun updateGlobalMenuRoots() {}

  internal open fun doInstallAppMenuIfNeeded(frame: JFrame) {}

  open fun onToggleFullScreen(isFullScreen: Boolean) {}
}

private val LOG: Logger
  get() = logger<IdeMenuBar>()

private const val COLLAPSED_HEIGHT = 2

// NOTE: for OSX only
internal fun doUpdateAppMenu() {
  if (!Menu.isJbScreenMenuEnabled()) {
    return
  }

  // 1. rename with localized
  Menu.renameAppMenuItems(DynamicBundle(IdeMenuBar::class.java, "messages.MacAppMenuBundle"))

  //
  // 2. add custom new items in AppMenu
  //

  //Example (add new item after "Preferences"):
  //int pos = appMenu.findIndexByTitle("Pref.*");
  //int pos2 = appMenu.findIndexByTitle("NewCustomItem");
  //if (pos2 < 0) {
  //  MenuItem mi = new MenuItem();
  //  mi.setLabel("NewCustomItem", null);
  //  mi.setActionDelegate(() -> System.err.println("NewCustomItem executes"));
  //  appMenu.add(mi, pos, true);
  //}
}

internal fun createScreeMenuPeer(frame: JFrame): MenuBar? {
  if (Menu.isJbScreenMenuEnabled()) {
    val screenMenuPeer = MenuBar("MainMenu")
    screenMenuPeer.setFrame(frame)
    return screenMenuPeer
  }
  else {
    return null
  }
}

internal fun createMenuBar(coroutineScope: CoroutineScope, frame: JFrame): IdeMenuBar {
  return if (SystemInfoRt.isLinux) LinuxIdeMenuBar(coroutineScope, frame) else IdeMenuBar(coroutineScope, frame)
}

internal fun installAppMenuIfNeeded(frame: JFrame) {
  if (!SystemInfoRt.isLinux) {
    return
  }

  val menuBar = frame.jMenuBar
  // must be called when frame is visible (otherwise frame.getPeer() == null)
  if (menuBar is IdeMenuBar) {
    try {
      menuBar.doInstallAppMenuIfNeeded(frame)
    }
    catch (e: Throwable) {
      LOG.warn("cannot install app menu", e)
    }
  }
  else if (menuBar != null) {
    LOG.info("The menu bar '$menuBar of frame '$frame' isn't instance of IdeMenuBar")
  }
}
