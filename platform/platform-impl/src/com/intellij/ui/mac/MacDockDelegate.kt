// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.intellij.ide.DataManager
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.impl.SystemDock
import java.awt.Menu
import java.awt.MenuItem
import java.awt.PopupMenu
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal class MacDockDelegate private constructor() : SystemDock.Delegate {
  companion object {
    private val LOG = logger<MacDockDelegate>()

    @Suppress("SpellCheckingInspection")
    private val appClass by lazy { MacDockDelegate::class.java.classLoader.loadClass("com.apple.eawt.Application") }

    val instance: SystemDock.Delegate by lazy {
      val result = MacDockDelegate()
      try {
        dockMenu.add(recentProjectsMenu)
        val lookup = MethodHandles.lookup()
        val appClass = appClass
        val app = lookup.findStatic(appClass, "getApplication", MethodType.methodType(appClass)).invoke()
        lookup.findVirtual(appClass, "setDockMenu", MethodType.methodType(Void.TYPE, PopupMenu::class.java)).invoke(app, dockMenu)
      }
      catch (e: Exception) {
        LOG.error(e)
      }

      result
    }

    private val dockMenu = PopupMenu("DockMenu")
    private val recentProjectsMenu = Menu("Recent Projects")

    private fun activateApplication() {
      try {
        val appClass = appClass
        val lookup = MethodHandles.lookup()
        val app = lookup.findStatic(appClass, "getApplication", MethodType.methodType(appClass)).invoke()
        lookup.findVirtual(appClass, "requestForeground", MethodType.methodType(Void.TYPE, java.lang.Boolean.TYPE)).invoke(app, false)
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }
  }

  override fun updateRecentProjectsMenu() {
    recentProjectsMenu.removeAll()
    for (action in RecentProjectListActionProvider.getInstance().getActions(addClearListItem = false)) {
      val menuItem = MenuItem((action as ReopenProjectAction).projectNameToDisplay)
      menuItem.addActionListener {
        // Newly opened project won't become an active window, if another application is currently active.
        // This is not what user expects, so we activate our application explicitly.
        activateApplication()
        ActionUtil.performActionDumbAwareWithCallbacks(
          action,
          AnActionEvent.createFromAnAction(action, null, ActionPlaces.DOCK_MENU, DataManager.getInstance().getDataContext(null))
        )
      }
      recentProjectsMenu.add(menuItem)
    }
  }
}