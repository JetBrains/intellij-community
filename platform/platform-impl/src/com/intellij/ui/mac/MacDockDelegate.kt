// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.intellij.ide.DataManager
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.SystemDock
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.wm.impl.headertoolbar.ProjectToolbarWidgetPresentable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Menu
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.Taskbar
import java.util.Collections
import java.util.IdentityHashMap

internal suspend fun createMacDelegate(): SystemDock? {
  // todo get rid of UI dispatcher here
  return withContext(Dispatchers.UiWithModelAccess) {
    val dockMenu = PopupMenu("DockMenu")

    runCatching {
      val recentProjectsMenuItem = initRecentProjectsMenuItem(dockMenu)
      initAdditionalItems(dockMenu)
      if (Taskbar.isTaskbarSupported() /* not supported in CWM/Projector environment */) {
        Taskbar.getTaskbar().menu = dockMenu
      }
      recentProjectsMenuItem?.let { MacDockDelegate(it) }
    }.getOrHandleException { LOG }
  }
}

private suspend fun initRecentProjectsMenuItem(dockMenu: PopupMenu): Menu? {
  val recentProjectsInDockSupported = serviceAsync<RecentProjectListActionProvider>().recentProjectsInDocSupported()
  if (!recentProjectsInDockSupported) return null
  val recentProjectsMenu = Menu("Recent Projects")
  dockMenu.add(recentProjectsMenu)
  return recentProjectsMenu
}

private val DOCK_MENU_ACTIONS_EP = ExtensionPointName<MacDockMenuActions>("com.intellij.mac.dockMenuActions")

private val LOG = logger<MacDockDelegate>()

private fun initAdditionalItems(dockMenu: PopupMenu) {
  val items = Collections.synchronizedMap(IdentityHashMap<MacDockMenuActions, MenuItem>())

  fun addItem(extension: MacDockMenuActions) {
    extension.createMenuItem()?.let {
      items[extension] = it
      dockMenu.add(it)
    }
  }

  DOCK_MENU_ACTIONS_EP.forEachExtensionSafe(::addItem)

  DOCK_MENU_ACTIONS_EP.addExtensionPointListener(object : ExtensionPointListener<MacDockMenuActions> {
    override fun extensionAdded(extension: MacDockMenuActions, pluginDescriptor: PluginDescriptor) {
      runInEdt {
        try {
          addItem(extension)
        }
        catch (e: Exception) {
          LOG.error("Failed to create dock menu item from ${extension.javaClass.name}", e)
        }
      }
    }

    override fun extensionRemoved(extension: MacDockMenuActions, pluginDescriptor: PluginDescriptor) {
      runInEdt {
        items.remove(extension)?.let { dockMenu.remove(it) }
      }
    }
  }, null)
}

private class MacDockDelegate(private val recentProjectsMenu: Menu) : SystemDock {
  override suspend fun updateRecentProjectsMenu() {
    val projectListActionProvider = serviceAsync<RecentProjectListActionProvider>()
    // todo get rid of UI dispatcher here
    withContext(Dispatchers.UiWithModelAccess) {
      recentProjectsMenu.removeAll()
      for (action in projectListActionProvider.getActionsWithoutGroups()) {
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