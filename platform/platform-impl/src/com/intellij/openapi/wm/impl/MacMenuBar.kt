// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.runActivity
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.ui.mac.foundation.NSDefaults
import com.intellij.ui.mac.screenmenu.MenuBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import javax.swing.JComponent
import javax.swing.JFrame

internal class MacMenuBar internal constructor(@JvmField internal val coroutineScope: CoroutineScope,
                                               private val component: JComponent,
                                               private val frame: JFrame) : ActionAwareIdeMenuBar {
  private val menuBarHelper: IdeMenuBarHelper

  @JvmField
  internal var activated = false
  private val screenMenuPeer: MenuBar?

  init {
    val flavor = object : IdeMenuFlavor {
      override var state: IdeMenuBarState = IdeMenuBarState.EXPANDED

      override fun updateAppMenu() {
        doUpdateAppMenu()
      }
    }

    val facade = object : IdeMenuBarHelper.MenuBarImpl {
      override val frame: JFrame
        get() = this@MacMenuBar.frame
      override val coroutineScope: CoroutineScope
        get() = this@MacMenuBar.coroutineScope
      override val isDarkMenu: Boolean
        get() = NSDefaults.isDarkMenuBar()
      override val component: JComponent
        get() = this@MacMenuBar.component

      override fun updateGlobalMenuRoots() {
      }

      override suspend fun getMainMenuActionGroup(): ActionGroup? {
        val rootPane = this@MacMenuBar.frame.rootPane
        val group = if (rootPane is IdeRootPane) rootPane.mainMenuActionGroup else null
        return group ?: ApplicationManager.getApplication().serviceAsync<CustomActionsSchema>().getCorrectedAction(
          IdeActions.GROUP_MAIN_MENU) as ActionGroup?
      }
    }

    screenMenuPeer = runActivity("ide menu bar init") { createScreeMenuPeer(frame) }
    if (screenMenuPeer == null) {
      menuBarHelper = IdeMenuBarHelper(flavor = flavor, menuBar = facade)
    }
    else {
      menuBarHelper = PeerBasedIdeMenuBarHelper(screenMenuPeer = screenMenuPeer, flavor = flavor, menuBar = facade)
    }

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      screenMenuPeer?.let {
        @Suppress("SSBasedInspection")
        it.dispose()
      }
    }
  }

  override suspend fun updateMenuActions(forceRebuild: Boolean) {
    menuBarHelper.updateMenuActions(forceRebuild)
  }
}
