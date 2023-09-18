// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.EDT
import com.intellij.ui.mac.foundation.NSDefaults
import com.intellij.ui.mac.screenmenu.MenuBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal open class PeerBasedIdeMenuBarHelper(private val screenMenuPeer: MenuBar,
                                              flavor: IdeMenuFlavor,
                                              menuBar: MenuBarImpl) : IdeMenuBarHelper(flavor, menuBar) {
  override fun isUpdateForbidden() = screenMenuPeer.isAnyChildOpened

  override suspend fun updateMenuActions(mainActionGroup: ActionGroup?, forceRebuild: Boolean, isFirstUpdate: Boolean): List<ActionGroup> {
    val newVisibleActions = if (mainActionGroup == null) {
      emptyList()
    }
    else {
      expandMainActionGroup(mainActionGroup = mainActionGroup,
                            menuBar = menuBar.component,
                            frame = menuBar.frame,
                            presentationFactory = presentationFactory,
                            isFirstUpdate = isFirstUpdate) ?: return emptyList()
    }

    if (!forceRebuild && newVisibleActions == visibleActions && !presentationFactory.isNeedRebuild) {
      return newVisibleActions
    }

    withContext(Dispatchers.EDT) {
      visibleActions = newVisibleActions
      screenMenuPeer.beginFill()
      try {
        if (!newVisibleActions.isEmpty()) {
          val enableMnemonics = !UISettings.getInstance().disableMnemonics
          for (action in newVisibleActions) {
            screenMenuPeer.add(createMacNativeActionMenu(context = null,
                                                         place = ActionPlaces.MAIN_MENU,
                                                         group = action,
                                                         presentationFactory = presentationFactory,
                                                         isMnemonicEnabled = enableMnemonics,
                                                         frame = menuBar.frame,
                                                         useDarkIcons = NSDefaults.isDarkMenuBar())
            )
          }
        }
      }
      finally {
        screenMenuPeer.endFill()
        presentationFactory.resetNeedRebuild()
      }
      flavor.updateAppMenu()
    }
    return newVisibleActions
  }
}