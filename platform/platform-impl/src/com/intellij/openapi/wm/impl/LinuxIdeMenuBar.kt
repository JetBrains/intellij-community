// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.util.Disposer
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

internal class LinuxIdeMenuBar : IdeMenuBar() {
  companion object {
    fun doBindAppMenuOfParent(frame: JFrame, parentFrame: JFrame) {
      if (GlobalMenuLinux.isPresented()) {
        // all children of IdeFrame mustn't show swing-menubar
        frame.jMenuBar?.isVisible = false
      }

      val globalMenu = (parentFrame.jMenuBar as? LinuxIdeMenuBar)?.globalMenu ?: return
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

  override fun isDarkMenu() = super.isDarkMenu() || globalMenu != null

  override fun updateGlobalMenuRoots() {
    globalMenu?.setRoots(components.mapNotNull { it as? ActionMenu })
  }

  override fun doInstallAppMenuIfNeeded(frame: JFrame) {
    if (!GlobalMenuLinux.isAvailable()) {
      return
    }

    if (globalMenu == null) {
      val globalMenuLinux = GlobalMenuLinux.create(frame) ?: return
      globalMenu = globalMenuLinux
      Disposer.register(myDisposable, globalMenuLinux)
      updateMenuActionsLazily(true)
    }
  }

  override fun onToggleFullScreen(isFullScreen: Boolean) {
    globalMenu?.toggle(!isFullScreen)
  }
}