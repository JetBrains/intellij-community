// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.WindowWatcher;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Requests focus for the specified tool window.
 *
 * @author Vladimir Kondratyev
 */
public final class RequestFocusInToolWindowCmd extends FinalizableCommand {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.commands.RequestFocusInToolWindowCmd");
  private final ToolWindowImpl myToolWindow;
  private final FocusWatcher myFocusWatcher;

  private final Project myProject;

  public RequestFocusInToolWindowCmd(IdeFocusManager focusManager, final ToolWindowImpl toolWindow, final FocusWatcher focusWatcher, @NotNull Runnable finishCallBack, Project project) {
    super(finishCallBack);
    myToolWindow = toolWindow;
    myFocusWatcher = focusWatcher;
    myProject = project;
  }

  @Override
  public final void run() {
    processRequestFocus();
  }

  private void processRequestFocus() {
    try {

      Component preferredFocusedComponent = myToolWindow.isUseLastFocusedOnActivation() ? myFocusWatcher.getFocusedComponent() : null;

      if (preferredFocusedComponent == null && myToolWindow.getContentManager().getSelectedContent() != null) {
        preferredFocusedComponent = myToolWindow.getContentManager().getSelectedContent().getPreferredFocusableComponent();
        if (preferredFocusedComponent != null) {
          preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent((JComponent)preferredFocusedComponent);
        }
      }

      if (preferredFocusedComponent == null) {
        preferredFocusedComponent = myFocusWatcher.getNearestFocusableComponent();
        if (preferredFocusedComponent instanceof JComponent) {
          preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent((JComponent)preferredFocusedComponent);
        }
      }

      if (preferredFocusedComponent != null) {
        // When we get remembered component this component can be already invisible
        if (!preferredFocusedComponent.isShowing()) {
          preferredFocusedComponent = null;
        }
      }

      if (preferredFocusedComponent == null) {
        final JComponent component = myToolWindow.getComponent();
        preferredFocusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(component);
      }

      // Try to focus component which is preferred one for the tool window
      if (preferredFocusedComponent != null) {
        requestFocus(preferredFocusedComponent).doWhenDone(() -> bringOwnerToFront());
      }
      else {
        // If there is no preferred component then try to focus tool window itself
        final JComponent componentToFocus = myToolWindow.getComponent();
        requestFocus(componentToFocus).doWhenDone(() -> bringOwnerToFront());
      }
    }
    finally {
      finish();
    }
  }

  private void bringOwnerToFront() {
    final Window owner = SwingUtilities.getWindowAncestor(myToolWindow.getComponent());
    //Toolwindow component shouldn't take focus back if new dialog or frame appears
    //Example: Ctrl+D on file history brings a diff dialog to front and then hides it by main frame by calling
    // toFront on toolwindow window
    Window activeFrame = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (activeFrame != null && activeFrame != owner) return;
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
      final Window activeWindow = getActiveWindow(owner.getOwnedWindows());
      if (activeWindow == null || activeWindow instanceof FloatingDecorator) {
        LOG.debug("owner.toFront()");
        //Thread.dumpStack();
        //System.out.println("------------------------------------------------------");
        owner.toFront();
      }
    }
  }


  @NotNull
  private ActionCallback requestFocus(@NotNull final Component c) {
    final ActionCallback result = new ActionCallback();
    final Alarm checkerAlarm = new Alarm();
    Runnable checker = new Runnable() {
      final long startTime = System.currentTimeMillis();
      @Override
      public void run() {
        if (System.currentTimeMillis() - startTime > 10000) {
          result.setRejected();
          return;
        }
        if (c.isShowing()) {
          final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
          if (owner == null || owner != c) {
            Component defaultComponent = myToolWindow.getComponent().getFocusTraversalPolicy().getDefaultComponent(myToolWindow.getComponent());
            if (defaultComponent != null) {
              myManager.getFocusManager().requestFocusInProject(
                defaultComponent, myProject);
              result.setDone();
            } else {
              result.setRejected();
            }
          }
          myManager.getFocusManager().doWhenFocusSettlesDown(() -> updateToolWindow(c));
        }
        else {
          checkerAlarm.addRequest(this, 100);
        }
      }
    };
    checkerAlarm.addRequest(checker, 0);
    return result;
  }

  private void updateToolWindow(Component c) {
    if (c.isFocusOwner()) {
      myFocusWatcher.setFocusedComponentImpl(c);
      if (myToolWindow.isAvailable() && !myToolWindow.isActive()) {
        myToolWindow.activate(null, true, false);
      }
    }

    updateFocusedComponentForWatcher(c);
  }

  private static void updateFocusedComponentForWatcher(final Component c) {
    final WindowWatcher watcher = ((WindowManagerImpl)WindowManager.getInstance()).getWindowWatcher();
    final FocusWatcher focusWatcher = watcher.getFocusWatcherFor(c);
    if (focusWatcher != null && c.isFocusOwner()) {
      focusWatcher.setFocusedComponentImpl(c);
    }
  }

  /**
   * @return first active window from hierarchy with specified roots. Returns {@code null}
   *         if there is no active window in the hierarchy.
   */
  private static Window getActiveWindow(final Window[] windows) {
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
