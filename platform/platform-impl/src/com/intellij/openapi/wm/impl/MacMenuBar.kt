// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.runActivity
import com.intellij.ui.mac.foundation.NSDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import javax.swing.JComponent
import javax.swing.JFrame

internal fun createMacMenuBar(coroutineScope: CoroutineScope, component: JComponent, frame: JFrame): ActionAwareIdeMenuBar {
  val flavor = object : IdeMenuFlavor {
    override var state: IdeMenuBarState = IdeMenuBarState.EXPANDED

    override fun updateAppMenu() {
      doUpdateAppMenu()
    }
  }

  val facade = object : IdeMenuBarHelper.MenuBarImpl {
    override val frame: JFrame
      get() = frame
    override val coroutineScope: CoroutineScope
      get() = coroutineScope
    override val isDarkMenu: Boolean
      get() = NSDefaults.isDarkMenuBar()
    override val component: JComponent
      get() = component

    override fun updateGlobalMenuRoots() {
    }

    override suspend fun getMainMenuActionGroup() = getMainMenuActionGroup(frame)
  }

  val screenMenuPeer = runActivity("ide menu bar init") { createScreeMenuPeer(frame) }
  val menuBarHelper = if (screenMenuPeer == null) {
    JMenuBasedIdeMenuBarHelper(flavor = flavor, menuBar = facade)
  }
  else {
    PeerBasedIdeMenuBarHelper(screenMenuPeer = screenMenuPeer, flavor = flavor, menuBar = facade)
  }

  coroutineScope.coroutineContext.job.invokeOnCompletion {
    screenMenuPeer?.let {
      @Suppress("SSBasedInspection")
      it.dispose()
    }
  }
  return menuBarHelper
}