// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.intellij.ide.DataManager
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.impl.SystemDock
import java.awt.*

internal class MacDockDelegate private constructor(private val recentProjectsMenu: Menu) : SystemDock.Delegate {
  companion object {
    val instance: SystemDock.Delegate by lazy {
      val dockMenu = PopupMenu("DockMenu")
      val recentProjectsMenu = Menu("Recent Projects")
      try {
        dockMenu.add(recentProjectsMenu)
        if (Taskbar.isTaskbarSupported() /* not supported in CWM/Projector environment */) {
          Taskbar.getTaskbar().menu = dockMenu
        }
      }
      catch (e: Exception) {
        logger<MacDockDelegate>().error(e)
      }

      MacDockDelegate(recentProjectsMenu)
    }
  }

  override fun updateRecentProjectsMenu() {
    recentProjectsMenu.removeAll()
    for (action in RecentProjectListActionProvider.getInstance().getActions(addClearListItem = false)) {
      val menuItem = MenuItem((action as ReopenProjectAction).templatePresentation.text)
      menuItem.addActionListener {
        // Newly opened project won't become an active window, if another application is currently active.
        // This is not what user expects, so we activate our application explicitly.
        Desktop.getDesktop().requestForeground(false)
        ActionUtil.performActionDumbAwareWithCallbacks(
          action,
          AnActionEvent.createFromAnAction(action, null, ActionPlaces.DOCK_MENU, DataManager.getInstance().getDataContext(null))
        )
      }
      recentProjectsMenu.add(menuItem)
    }
  }
}