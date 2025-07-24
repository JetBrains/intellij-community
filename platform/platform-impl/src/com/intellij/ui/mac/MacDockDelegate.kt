// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.intellij.ide.DataManager
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.SystemDock
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.wm.impl.headertoolbar.ProjectToolbarWidgetPresentable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.*

internal suspend fun createMacDelegate(): SystemDock {
  // todo get rid of UI dispatcher here
  val recentProjectsMenu = withContext(Dispatchers.UiWithModelAccess) {
    val dockMenu = PopupMenu("DockMenu")
    val recentProjectsMenu = Menu("Recent Projects")
    runCatching {
      dockMenu.add(recentProjectsMenu)
      ExtensionPointName<MacDockMenuActions>("com.intellij.mac.dockMenuActions").forEachExtensionSafe { actions ->
        actions.createMenuItem()?.let {
          dockMenu.add(it)
        }
      }
      if (Taskbar.isTaskbarSupported() /* not supported in CWM/Projector environment */) {
        Taskbar.getTaskbar().menu = dockMenu
      }
    }.getOrLogException { logger<MacDockDelegate>() }
    recentProjectsMenu
  }
  return MacDockDelegate(recentProjectsMenu)
}

private class MacDockDelegate(private val recentProjectsMenu: Menu) : SystemDock {
  override suspend fun updateRecentProjectsMenu() {
    val projectListActionProvider = serviceAsync<RecentProjectListActionProvider>()
    // todo get rid of UI dispatcher here
    withContext(Dispatchers.UiWithModelAccess) {
      recentProjectsMenu.removeAll()
      for (action in projectListActionProvider.getActions()) {
        if (action !is ProjectToolbarWidgetPresentable) {
          continue
        }

        val menuItem = MenuItem(action.nameToDisplayAsText)
        menuItem.addActionListener {
          // The newly opened project won't become an active window if another application is currently active.
          // This is not what user expects, so we activate our application explicitly.
          Desktop.getDesktop().requestForeground(false)
          val event = AnActionEvent.createFromAnAction(
            action, null, ActionPlaces.DOCK_MENU, DataManager.getInstance().getDataContext(null))
          ActionUtil.performAction(action, event)
        }
        recentProjectsMenu.add(menuItem)
      }
    }
  }
}