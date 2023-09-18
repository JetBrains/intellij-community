// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.PopupMenuPreloader
import com.intellij.openapi.application.EDT
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.MenuSelectionManager

internal class JMenuBasedIdeMenuBarHelper(flavor: IdeMenuFlavor, menuBar: IdeJMenuBar.JMenuBarImpl) : IdeMenuBarHelper(flavor, menuBar) {
  override fun isUpdateForbidden() = MenuSelectionManager.defaultManager().selectedPath.isNotEmpty()

  override suspend fun postInitActions(actions: List<ActionGroup>) {
    withContext(Dispatchers.EDT) {
      for (action in actions) {
        PopupMenuPreloader.install(menuBar.component, ActionPlaces.MAIN_MENU, null) { action }
      }
    }
  }

  override suspend fun updateMenuActions(mainActionGroup: ActionGroup?, forceRebuild: Boolean, isFirstUpdate: Boolean): List<ActionGroup> {
    val menuBarComponent = menuBar.component
    val newVisibleActions = if (mainActionGroup == null) {
      emptyList()
    }
    else {
      // null means "cancelled" (todo - reconsider when Promise will be changed to coroutine)
      expandMainActionGroup(mainActionGroup = mainActionGroup,
                            menuBar = menuBarComponent,
                            frame = menuBar.frame,
                            presentationFactory = presentationFactory,
                            isFirstUpdate = isFirstUpdate) ?: return emptyList()
    }

    if (!forceRebuild && newVisibleActions == visibleActions && !presentationFactory.isNeedRebuild) {
      val enableMnemonics = !UISettings.getInstance().disableMnemonics
      withContext(Dispatchers.EDT) {
        for (child in menuBarComponent.components) {
          if (child is ActionMenu) {
            child.updateFromPresentation(enableMnemonics)
          }
        }
      }
      return newVisibleActions
    }

    // should rebuild UI
    val changeBarVisibility = newVisibleActions.isEmpty() || visibleActions.isEmpty()
    withContext(Dispatchers.EDT) {
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
    return newVisibleActions
  }
}