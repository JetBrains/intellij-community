// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.diagnostic.runActivity
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.mac.screenmenu.MenuBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import javax.swing.JComponent
import javax.swing.JFrame

internal fun createMacMenuBar(
  coroutineScope: CoroutineScope,
  component: JComponent,
  frame: JFrame,
  mainMenuActionGroupProvider: suspend () -> ActionGroup?,
): ActionAwareIdeMenuBar {
  val flavor = object : IdeMenuFlavor {
    override fun updateAppMenu() {
      doUpdateAppMenu()
    }
  }

  val facade = object : IdeMenuBarHelper.MenuBarImpl {
    override val frame: JFrame
      get() = frame
    override val coroutineScope: CoroutineScope
      get() = coroutineScope
    override val component: JComponent
      get() = component

    override fun updateGlobalMenuRoots() {
    }

    override suspend fun getMainMenuActionGroup() = mainMenuActionGroupProvider()
  }

  val screenMenuPeer = runActivity("ide menu bar init") { MenuBar("MainMenu", frame) }
  val menuBarHelper = PeerBasedIdeMenuBarHelper(screenMenuPeer = screenMenuPeer, flavor = flavor, menuBar = facade)

  coroutineScope.coroutineContext.job.invokeOnCompletion {
    Disposer.dispose(screenMenuPeer)
  }
  return menuBarHelper
}