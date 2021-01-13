// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.WindowManager
import java.awt.Component
import java.awt.KeyboardFocusManager
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
  val owner = SwingUtilities.getWindowAncestor(toolWindow.component) ?: return
  val activeFrame = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
  if (activeFrame != null && activeFrame !== owner) {
    owner.toFront()
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
  if (component == null && container.isFocusable) {
    return container
  }
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