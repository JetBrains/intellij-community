// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.*;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class RequestFocusInToolWindowCommand implements Runnable {
  private static final Logger LOG = Logger.getInstance(RequestFocusInToolWindowCommand.class);
  private final ToolWindowImpl toolWindow;

  public RequestFocusInToolWindowCommand(@NotNull ToolWindowImpl toolWindow) {
    this.toolWindow = toolWindow;
  }

  @Override
  public void run() {
    Alarm checkerAlarm = new Alarm(toolWindow.getDisposable());
    checkerAlarm.addRequest(new Runnable() {
      final long startTime = System.currentTimeMillis();

      @Override
      public void run() {
        if (System.currentTimeMillis() - startTime > 10000) {
          LOG.debug(toolWindow.getId(), " tool window - cannot wait for showing component");
          return;
        }

        Component component = getShowingComponentToRequestFocus(toolWindow);
        if (component == null) {
          checkerAlarm.addRequest(this, 100);
        }
        else {
          Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
          ToolWindowManagerImpl manager = toolWindow.getToolWindowManager();
          if (owner != component) {
            manager.getFocusManager().requestFocusInProject(component, manager.getProject());
            bringOwnerToFront(toolWindow);
          }
          manager.getFocusManager().doWhenFocusSettlesDown(() -> updateToolWindow(component));
        }
      }
    }, 0);
  }

  private static void bringOwnerToFront(@NotNull ToolWindowImpl toolWindow) {
    final Window owner = SwingUtilities.getWindowAncestor(toolWindow.getComponent());
    //Toolwindow component shouldn't take focus back if new dialog or frame appears
    //Example: Ctrl+D on file history brings a diff dialog to front and then hides it by main frame by calling
    // toFront on toolwindow window
    Window activeFrame = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (activeFrame != null && activeFrame != owner) {
      return;
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
    if (owner != null && owner.getFocusOwner() == null) {
      Window activeWindow = getActiveWindow(owner.getOwnedWindows());
      if (activeWindow == null || activeWindow instanceof FloatingDecorator) {
        LOG.debug("owner.toFront()");
        //Thread.dumpStack();
        //System.out.println("------------------------------------------------------");
        owner.toFront();
      }
    }
  }

  @Nullable
  public static Component getShowingComponentToRequestFocus(@NotNull ToolWindowImpl toolWindow) {
    JComponent container = toolWindow.getComponentIfInitialized();
    if (container == null || !container.isShowing()) {
      LOG.debug(toolWindow.getId(), " tool window: parent container is hidden: ", container);
      return null;
    }

    FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
    if (policy == null) {
      LOG.warn(toolWindow.getId() + " tool window does not provide focus traversal policy");
      return null;
    }

    Component component = toolWindow.getToolWindowManager().getFocusManager().getFocusTargetFor(container);
    if (component == null || !component.isShowing()) {
      LOG.debug(toolWindow.getId(), " tool window: default component is hidden: ", container);
      return null;
    }
    return component;
  }

  private void updateToolWindow(@NotNull Component component) {
    if (component.isFocusOwner()) {
      toolWindow.setFocusedComponent(component);
      if (toolWindow.isAvailable() && !toolWindow.isActive()) {
        toolWindow.activate(null, true, false);
      }
    }

    updateFocusedComponentForWatcher(component);
  }

  private static void updateFocusedComponentForWatcher(@NotNull Component c) {
    WindowWatcher watcher = ((WindowManagerImpl)WindowManager.getInstance()).getWindowWatcher();
    FocusWatcher focusWatcher = watcher.getFocusWatcherFor(c);
    if (focusWatcher != null && c.isFocusOwner()) {
      focusWatcher.setFocusedComponentImpl(c);
    }
  }

  /**
   * @return first active window from hierarchy with specified roots. Returns {@code null}
   *         if there is no active window in the hierarchy.
   */
  private static Window getActiveWindow(Window @NotNull [] windows) {
    for (Window window : windows) {
      if (window.isShowing() && window.isActive()) {
        return window;
      }
      window = getActiveWindow(window.getOwnedWindows());
      if (window != null) {
        return window;
      }
    }
    return null;
  }
}
