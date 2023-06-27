// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

internal class LinuxIdeMenuBar(coroutineScope: CoroutineScope, frame: JFrame) : IdeMenuBar(coroutineScope, frame) {
  companion object {
    fun doBindAppMenuOfParent(frame: JFrame, parentFrame: JFrame) {
      val globalMenu = (parentFrame.jMenuBar as? LinuxIdeMenuBar)?.globalMenu ?: return

      if (GlobalMenuLinux.isPresented()) {
        // all children of IdeFrame mustn't show swing-menubar
        frame.jMenuBar?.isVisible = false
      }

      frame.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent?) {
          globalMenu.unbindWindow(frame)
        }

        override fun windowOpened(e: WindowEvent?) {
          globalMenu.bindNewWindow(frame)
        }
      })
    }
  }

  private var globalMenu: GlobalMenuLinux? = null

  override val isDarkMenu: Boolean
    get() = super.isDarkMenu || globalMenu != null

  override fun updateGlobalMenuRoots() {
    globalMenu?.setRoots(components.mapNotNull { it as? ActionMenu })
  }

  override fun doInstallAppMenuIfNeeded(frame: JFrame) {
    if (IdeRootPane.isMenuButtonInToolbar || !GlobalMenuLinux.isAvailable()) {
      return
    }

    if (globalMenu == null) {
      val globalMenuLinux = GlobalMenuLinux.create(frame)
      globalMenu = globalMenuLinux
      coroutineScope.coroutineContext.job.invokeOnCompletion {
        Disposer.dispose(globalMenuLinux)
      }
      coroutineScope.launch {
        updateMenuActions(forceRebuild = true)
      }
    }
  }

  override fun onToggleFullScreen(isFullScreen: Boolean) {
    globalMenu?.toggle(!isFullScreen)
  }
}