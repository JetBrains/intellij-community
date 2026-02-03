// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.ui.Gray
import com.intellij.ui.ScreenUtil
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.plaf.beg.IdeaMenuUI
import com.intellij.util.concurrency.annotations.RequiresEdt
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
open class IdeJMenuBar internal constructor(@JvmField internal val coroutineScope: CoroutineScope,
                                            @JvmField internal val frame: JFrame,
                                            private val customMenuGroup: ActionGroup? = null)
  : JMenuBar(), ActionAwareIdeMenuBar {

  private val menuBarHelper: IdeMenuBarHelper
  private val updateGlobalMenuRootsListeners = mutableListOf<Runnable>()
  val rootMenuItems: List<ActionMenu>
    get() = components.mapNotNull { it as? ActionMenu }

  init {
    val flavor = if (CustomWindowHeaderUtil.isFloatingMenuBarSupported) {
      FloatingMenuBarFlavor(this)
    }
    else {
      object : IdeMenuFlavor {
        override fun updateAppMenu() {
          doUpdateAppMenu()
        }
      }
    }

    menuBarHelper = JMenuBasedIdeMenuBarHelper(flavor = flavor, menuBar = JMenuBarImpl(this))
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      isOpaque = false
    }
  }

  internal class JMenuBarImpl(private val bar: IdeJMenuBar) : IdeMenuBarHelper.MenuBarImpl {
    override val frame: JFrame
      get() = bar.frame
    override val coroutineScope: CoroutineScope
      get() = bar.coroutineScope
    val isDarkMenu: Boolean
      get() = bar.isDarkMenu
    override val component: JComponent
      get() = bar

    override fun updateGlobalMenuRoots() {
      bar.updateGlobalMenuRoots()
    }

    override suspend fun getMainMenuActionGroup(): ActionGroup? = bar.customMenuGroup ?: bar.getMainMenuActionGroup()
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
    if (state == IdeMenuBarState.TEMPORARY_EXPANDED && StartupUiUtil.isDarkTheme) {
      return JBUI.Borders.customLine(Gray._75, 0, 0, 1, 0)
    }

    // save 1px for mouse handler
    if (state == IdeMenuBarState.COLLAPSED) {
      return JBUI.Borders.emptyBottom(1)
    }

    val uiSettings = UISettings.getInstance()
    return if (uiSettings.showMainToolbar || uiSettings.showNavigationBar) super.getBorder() else null
  }

  /**
   * We override [paint] and [paintChildren] in [IdeMenuBarState.COLLAPSED] state
   */
  override fun isPaintingOrigin(): Boolean {
    return true
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
    menuBarHelper.flavor.jMenuSelectionChanged(isIncluded)
    super.menuSelectionChanged(isIncluded)
  }

  internal val isActivated: Boolean
    get() {
      val index = selectionModel.selectedIndex
      return index != -1 && getMenu(index).isTryingToShowPopupMenu()
    }

  override fun getPreferredSize(): Dimension {
    return menuBarHelper.flavor.getPreferredSize(super.getPreferredSize())
  }

  override fun removeNotify() {
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      menuBarHelper.flavor.suspendAnimator()
    }
    super.removeNotify()
  }

  override fun updateMenuActions(forceRebuild: Boolean) {
    menuBarHelper.updateMenuActions(forceRebuild)
  }

  protected open val isDarkMenu: Boolean
    get() = false

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (isOpaque) {
      paintBackground(g)
    }
  }

  fun addUpdateGlobalMenuRootsListener(runnable: Runnable) {
    updateGlobalMenuRootsListeners.add(runnable)
  }

  fun removeUpdateGlobalMenuRootsListener(runnable: Runnable) {
    updateGlobalMenuRootsListeners.remove(runnable)
  }

  private fun paintBackground(g: Graphics) {
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      val window = SwingUtilities.getWindowAncestor(this)
      if (window is IdeFrame && !(window as IdeFrame).isInFullScreen) {
        return
      }
    }

    g.color = IdeaMenuUI.getMenuBackgroundColor()
    g.fillRect(0, 0, width, height)
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

  open suspend fun getMainMenuActionGroup(): ActionGroup? = customMenuGroup ?: createIdeMainMenuActionGroup()

  override fun getMenuCount(): Int {
    @Suppress("IfThenToElvis", "SENSELESS_COMPARISON")
    return if (menuBarHelper == null) 0 else menuBarHelper.flavor.correctMenuCount(super.getMenuCount())
  }

  internal open fun updateGlobalMenuRoots() {
    for (listener in updateGlobalMenuRootsListeners) {
      listener.run()
    }
  }

  internal open fun doInstallAppMenuIfNeeded(frame: JFrame) {}

  // it contradicts to our principle of avoiding EDT, but for the sake of simplicity and a reliable implementation, we do exclusion here,
  // it is an internal method, and we do control all implementations
  @RequiresEdt
  internal open fun onToggleFullScreen(isFullScreen: Boolean) {}
}

private val LOG: Logger
  get() = logger<IdeJMenuBar>()

// NOTE: for OSX only
internal fun doUpdateAppMenu() {
  if (!Menu.isJbScreenMenuEnabled()) {
    return
  }

  // 1. rename with localized
  Menu.renameAppMenuItems()

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

internal fun installAppMenuIfNeeded(frame: JFrame) {
  if (!SystemInfoRt.isLinux) {
    return
  }

  val menuBar = frame.jMenuBar
  // must be called when frame is visible (otherwise frame.getPeer() == null)
  if (menuBar is IdeJMenuBar) {
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

private fun JMenu.isTryingToShowPopupMenu(): Boolean =
  if (this is ActionMenu) {
    isTryingToShowPopupMenu
  }
  else {
    isPopupMenuVisible
  }
