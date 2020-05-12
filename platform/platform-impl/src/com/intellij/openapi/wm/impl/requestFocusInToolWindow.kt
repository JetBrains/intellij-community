// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.WindowManager
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import javax.swing.SwingUtilities

private val LOG = logger<ToolWindowManagerImpl>()

internal class FocusTask(private val toolWindow: ToolWindowImpl) : Runnable {
  var startTime = System.currentTimeMillis()

  override fun run() {
    if (System.currentTimeMillis() - startTime > 10000) {
      LOG.debug { "tool window ${toolWindow.id} - cannot wait for showing component" }
      return
    }

    val component = getShowingComponentToRequestFocus(toolWindow)
    if (component == null) {
      toolWindow.focusAlarm.cancelAllRequests()
      resetStartTime()
      toolWindow.focusAlarm.request(delay = 100)
    }
    else {
      val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().permanentFocusOwner
      val manager = toolWindow.toolWindowManager
      if (owner !== component) {
        manager.focusManager.requestFocusInProject(component, manager.project)
        bringOwnerToFront(toolWindow)
      }
      manager.focusManager.doWhenFocusSettlesDown {
        updateToolWindow(toolWindow, component)
      }
    }
  }

  fun resetStartTime() {
    startTime = System.currentTimeMillis()
  }
}

private fun bringOwnerToFront(toolWindow: ToolWindowImpl) {
  val owner = SwingUtilities.getWindowAncestor(toolWindow.component)
  //Toolwindow component shouldn't take focus back if new dialog or frame appears
  //Example: Ctrl+D on file history brings a diff dialog to front and then hides it by main frame by calling
  // toFront on toolwindow window
  val activeFrame = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
  if (activeFrame != null && activeFrame !== owner) {
    return
  }
  //if (owner == null) {
  //  System.out.println("owner = " + owner);
  //  return;
  //}
  // if owner is active window or it has active child window which isn't floating decorator then
  // don't bring owner window to font. If we will make toFront every time then it's possible
  // the following situation:
  // 1. user perform refactoring
  // 2. "Do not show preview" dialog is popping up.
  // 3. At that time "preview" tool window is being activated and modal "don't show..." dialog
  // isn't active.
  if (owner != null && owner.focusOwner == null) {
    val activeWindow = getActiveWindow(owner.ownedWindows)
    if (activeWindow == null || activeWindow is FloatingDecorator) {
      LOG.debug("owner.toFront()")
      //Thread.dumpStack();
      //System.out.println("------------------------------------------------------");
      owner.toFront()
    }
  }
}

internal fun getShowingComponentToRequestFocus(toolWindow: ToolWindowImpl): Component? {
  toolWindow.contentManager.selectedContent?.preferredFocusableComponent?.let {
    return it
  }

  val container = toolWindow.getComponentIfInitialized()
  if (container == null || !container.isShowing) {
    LOG.debug { "tool window ${toolWindow.id} parent container is hidden: $container" }
    return null
  }

  val policy = container.focusTraversalPolicy
  if (policy == null) {
    LOG.warn("${toolWindow.id} tool window does not provide focus traversal policy")
    return null
  }

  val component: Component? = toolWindow.toolWindowManager.focusManager.getFocusTargetFor(container)
  if (component == null || !component.isShowing) {
    LOG.debug { " tool window ${toolWindow.id} default component is hidden: $container" }
    return null
  }
  return component
}

private fun updateToolWindow(toolWindow: ToolWindowImpl, component: Component) {
  if (component.isFocusOwner) {
    toolWindow.toolWindowManager.updateToolWindow(toolWindow, component)
  }
  updateFocusedComponentForWatcher(component)
}

private fun updateFocusedComponentForWatcher(c: Component) {
  val watcher = (WindowManager.getInstance() as WindowManagerImpl).windowWatcher
  val focusWatcher = watcher.getFocusWatcherFor(c)
  if (focusWatcher != null && c.isFocusOwner) {
    focusWatcher.setFocusedComponentImpl(c)
  }
}

/**
 * @return first active window from hierarchy with specified roots. Returns `null`
 * if there is no active window in the hierarchy.
 */
private fun getActiveWindow(windows: Array<Window>): Window? {
  for (window in windows) {
    if (window.isShowing && window.isActive) {
      return window
    }

    getActiveWindow(window.ownedWindows)?.let {
      return it
    }
  }
  return null
}