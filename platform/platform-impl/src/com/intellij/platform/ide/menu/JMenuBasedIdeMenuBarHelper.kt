// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.headertoolbar.MainMenuWithButton
import com.intellij.openapi.wm.impl.headertoolbar.MergedMainMenu
import com.intellij.ui.AppUIUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.job
import java.awt.AWTEvent
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleState
import javax.swing.MenuElement
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities

internal class JMenuBasedIdeMenuBarHelper(flavor: IdeMenuFlavor, menuBar: IdeJMenuBar.JMenuBarImpl) : IdeMenuBarHelper(flavor, menuBar) {
  init {
    if (WinAltKeyProcessor.isEnabled()) {
      WinAltKeyProcessor.register(this)
      menuBar.coroutineScope.coroutineContext.job.invokeOnCompletion {
        ApplicationManager.getApplication().invokeLater {
          WinAltKeyProcessor.unregister(this)
        }
      }
    }
  }

  override fun isUpdateForbidden() = MenuSelectionManager.defaultManager().selectedPath.isNotEmpty()

  override suspend fun doUpdateVisibleActions(newVisibleActions: List<ActionGroup>, forceRebuild: Boolean) {
    ThreadingAssertions.assertEventDispatchThread()
    val menuBarComponent = menuBar.component
    if (!forceRebuild && (newVisibleActions == visibleActions) && !presentationFactory.isNeedRebuild) {
      val enableMnemonics = !UISettings.getInstance().disableMnemonics
      for (child in menuBarComponent.components) {
        if (child is ActionMenu) {
          child.updateFromPresentation(enableMnemonics)
        }
      }
      return
    }

    // should rebuild UI
    val changeBarVisibility = newVisibleActions.isEmpty() || visibleActions.isEmpty()
    visibleActions = newVisibleActions
    val mergedMainMenu = (menuBarComponent as? MergedMainMenu)
    val visibleMenuCount = mergedMainMenu?.rootMenuItems?.count() ?: 0
    menuBarComponent.removeAll()

    if (!newVisibleActions.isEmpty()) {
      val enableMnemonics = !UISettings.getInstance().disableMnemonics
      val isCustomDecorationActive = IdeFrameDecorator.isCustomDecorationActive()
      for (action in newVisibleActions) {
        val actionMenu = ActionMenu(context = null,
                                    place = ActionPlaces.MAIN_MENU,
                                    group = action,
                                    presentationFactory = presentationFactory,
                                    isMnemonicEnabled = enableMnemonics,
                                    useDarkIcons = (menuBar as IdeJMenuBar.JMenuBarImpl).isDarkMenu,
                                    isHeaderMenuItem = true)
        if (isCustomDecorationActive || menuBarComponent is MergedMainMenu) {
          actionMenu.isOpaque = false
          if (isCustomDecorationActive) {
            actionMenu.isFocusable = false
          }
        }
        if (mergedMainMenu != null && visibleMenuCount > 0 && visibleMenuCount < visibleActions.size && newVisibleActions.indexOf(action) >= visibleMenuCount) {
          mergedMainMenu.addInvisibleItem(actionMenu)
          continue
        }
        menuBarComponent.add(actionMenu)
      }
      (mergedMainMenu?.parent as? MainMenuWithButton)?.recalculateWidth()
    }
    presentationFactory.resetNeedRebuild()
    flavor.updateAppMenu()
    menuBar.updateGlobalMenuRoots()
    menuBarComponent.validate()
    if (changeBarVisibility) {
      menuBarComponent.invalidate()
      menuBar.frame.validate()
    }
  }

  internal fun activateMenu() {
    val menuBarComponent = menuBar.component
    if (menuBarComponent is MergedMainMenu) {
      val mainMenuWithButton = menuBarComponent.parent as? MainMenuWithButton
      if (mainMenuWithButton?.mainMenuButton?.button?.isShowing == true) {
        mainMenuWithButton.mainMenuButton.expandableMenu?.switchState(selectOnlyHeaderMenu = true)
        return
      }
    }

    val ideMenuBar = menuBarComponent as? IdeJMenuBar ?: return
    val firstMenu = ideMenuBar.rootMenuItems.firstOrNull() ?: return
    MenuSelectionManager.defaultManager().selectedPath = arrayOf<MenuElement>(menuBarComponent, firstMenu)
    ApplicationManager.getApplication().invokeLater {
      firstMenu.accessibleContext.firePropertyChange(AccessibleContext.ACCESSIBLE_STATE_PROPERTY, null, AccessibleState.SELECTED)
    }
  }

  internal fun shouldProcessAltRelease(e: KeyEvent): Boolean {
    val menuBarComponent = menuBar.component
    val visibleComponent = if (menuBarComponent.isShowing) menuBarComponent
    else {
      val button = ((menuBarComponent as? MergedMainMenu)?.parent as? MainMenuWithButton)?.mainMenuButton?.button
      if (button?.isShowing == true) button else return false
    }

    val window = SwingUtilities.getWindowAncestor(visibleComponent) ?: return false
    val eventWindow = e.component as? Window ?: SwingUtilities.getWindowAncestor(e.component)
    if (eventWindow != window) return false
    if (MenuSelectionManager.defaultManager().selectedPath.isNotEmpty()) return false
    if (ScreenReader.isActive() && AppUIUtil.isInFullScreen(window)) return false

    return true
  }

}

internal object WinAltKeyProcessor : IdeEventQueue.NonLockedEventDispatcher {
  private val helpers = mutableListOf<JMenuBasedIdeMenuBarHelper>()
  private var registered = false

  private var altPressed = false
  private var altPressedOnly = false

  @JvmStatic
  fun isEnabled(): Boolean = SystemInfoRt.isWindows && Registry.`is`("ide.windows.main.menu.focus.on.alt", false)

  fun register(helper: JMenuBasedIdeMenuBarHelper) {
    helpers.add(helper)
    if (!registered) {
      IdeEventQueue.getInstance().addDispatcher(this, null)
      registered = true
    }
  }

  fun unregister(helper: JMenuBasedIdeMenuBarHelper) {
    helpers.remove(helper)
    if (helpers.isEmpty() && registered) {
      IdeEventQueue.getInstance().removeDispatcher(this)
      registered = false
    }
  }

  override fun dispatch(e: AWTEvent): Boolean {
    if (!isEnabled()) {
      return false
    }

    // Prevent focusing the menu when hold Alt + mouse click shortcuts are used
    if (e is MouseEvent && e.id == MouseEvent.MOUSE_PRESSED && altPressed) {
      altPressedOnly = false
      return false
    }

    if (e !is KeyEvent) {
      return false
    }

    if (e.keyCode != KeyEvent.VK_ALT) {
      if (altPressed && (e.id == KeyEvent.KEY_PRESSED || e.id == KeyEvent.KEY_RELEASED)) {
        altPressedOnly = false
      }
      return false
    }

    val settings = UISettings.instanceOrNull
    // To avoid conflicts with the double Alt shortcut to show hidden tool stripes
    if (settings == null || settings.hideToolStripes || settings.presentationMode) {
      return false
    }

    when (e.id) {
      KeyEvent.KEY_PRESSED -> {
        if (!altPressed) {
          altPressed = true
          altPressedOnly = e.modifiersEx and (InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK) == 0
        }
      }
      KeyEvent.KEY_RELEASED -> {
        if (altPressed && altPressedOnly
            && e.modifiersEx and (InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK) == 0) {
          for (helper in helpers) {
            if (helper.shouldProcessAltRelease(e)) {
              ApplicationManager.getApplication().invokeLater {
                helper.activateMenu()
              }
              MainMenuCollector.logFocusedByAlt()
              break
            }
          }
        }

        altPressed = false
        altPressedOnly = false
      }
    }

    return false
  }
}
