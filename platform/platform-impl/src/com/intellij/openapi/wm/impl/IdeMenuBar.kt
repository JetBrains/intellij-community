// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.DynamicBundle
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.ide.menu.*
import com.intellij.ui.Gray
import com.intellij.ui.ScreenUtil
import com.intellij.ui.mac.foundation.NSDefaults
import com.intellij.ui.mac.screenmenu.Menu
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
                                           @JvmField internal val frame: JFrame,
                                           private val explicitMainMenuActionGroup: ActionGroup? = null) : JMenuBar(), ActionAwareIdeMenuBar {
  private val menuBarHelper: IdeMenuBarHelper
  private val updateGlobalMenuRootsListeners = mutableListOf<Runnable>()
  val rootMenuItems: List<ActionMenu>
    get() = components.mapNotNull { it as? ActionMenu }

  init {
    val flavor = if (isFloatingMenuBarSupported) {
      FloatingMenuBarFlavor(this)
    }
    else {
      object : IdeMenuFlavor {
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

      override suspend fun getMainMenuActionGroup(): ActionGroup? = explicitMainMenuActionGroup ?: this@IdeMenuBar.getMainMenuActionGroup()
    }

    menuBarHelper = JMenuBasedIdeMenuBarHelper(flavor = flavor, menuBar = facade)
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
    menuBarHelper.flavor.jMenuSelectionChanged(isIncluded)
    super.menuSelectionChanged(isIncluded)
  }

  internal val isActivated: Boolean
    get() {
      val index = selectionModel.selectedIndex
      return index != -1 && getMenu(index).isPopupMenuVisible
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

  internal open val isDarkMenu: Boolean
    get() = SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar()

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

  open suspend fun getMainMenuActionGroup(): ActionGroup? = explicitMainMenuActionGroup ?: getAndWrapMainMenuActionGroup()

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

  open fun onToggleFullScreen(isFullScreen: Boolean) {}
}

private val LOG: Logger
  get() = logger<IdeMenuBar>()

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