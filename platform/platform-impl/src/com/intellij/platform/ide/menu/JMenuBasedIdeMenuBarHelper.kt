// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.util.concurrency.ThreadingAssertions
import javax.swing.MenuSelectionManager

internal class JMenuBasedIdeMenuBarHelper(flavor: IdeMenuFlavor, menuBar: IdeJMenuBar.JMenuBarImpl) : IdeMenuBarHelper(flavor, menuBar) {
  override fun isUpdateForbidden() = MenuSelectionManager.defaultManager().selectedPath.isNotEmpty()

  override suspend fun doUpdateVisibleActions(newVisibleActions: List<ActionGroup>, forceRebuild: Boolean) {
    ThreadingAssertions.assertEventDispatchThread()
    val menuBarComponent = menuBar.component
    if (!forceRebuild && newVisibleActions == visibleActions && !presentationFactory.isNeedRebuild) {
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
        if (isCustomDecorationActive) {
          actionMenu.isOpaque = false
          actionMenu.isFocusable = false
        }
        menuBarComponent.add(actionMenu)
      }
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
}