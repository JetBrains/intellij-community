// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.focus;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class DumpFocusStateAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Application application = ApplicationManager.getApplication();

    System.err.println("Is the application active?: " + application.isActive());

    System.err.println("Idle time: " + application.isActive());

    System.err.println("Toplevels [ " + Window.getWindows().length +" ] :");

    Arrays.stream(Window.getWindows()).forEach(w -> {
      System.err.println("    - " + w.getName());
    });

    System.err.println("Ownerless [ " + Window.getOwnerlessWindows().length + " ] :");

    Arrays.stream(Window.getOwnerlessWindows()).forEach(w -> {
      System.err.println("    - " + w.getName());
    });

    KeyboardFocusManager focusManager = DefaultKeyboardFocusManager.getCurrentKeyboardFocusManager();
    System.err.println("Active window: " + getPrintableStringForWindow(focusManager.getActiveWindow()));
    System.err.println("Focused window: " + getPrintableStringForWindow(focusManager.getFocusedWindow()));
    System.err.println("Permanent focus owner: " + getPrintableStringForComponent(focusManager.getPermanentFocusOwner()));
    System.err.println("Focus owner: " + getPrintableStringForComponent(focusManager.getFocusOwner()));

    System.err.println("Is focus owner inside focused window?" + isFocusOwnerInsideFocusedWindow (
      focusManager.getFocusedWindow(), focusManager.getFocusOwner()
    ));

    System.err.println("Is focused window owned by active window?" + isFocusedWindowOwnedByActiveWindow (
      focusManager.getFocusedWindow(), focusManager.getActiveWindow()
    ));
  }

  private String getPrintableStringForComponent(Component component) {
    if (component == null) return "Component is null";
    if (component.getName() != null) return component.getName();
    return component.getClass().getName();
  }

  private String getPrintableStringForWindow (Window w) {
    if(w == null) return "Window is null";
    String title = getTitle(w);
    if (title != null) return title;
    title = getTitle(w);
    if (title != null) return title;
    String name = w.getName();
    if (name != null && !name.isEmpty()) return name;
    return w.getClass().getName();
  }

  private String getTitle(Window w) {
    if (w instanceof Dialog || w instanceof Frame) {
      String title = w instanceof Frame ? ((Frame)w).getTitle() : ((Dialog)w).getTitle();
      if (title != null && !title.isEmpty()) {
        return title;
      }
    }
    return null;
  }

  private boolean isFocusedWindowOwnedByActiveWindow(Window focusedWindow, Window activeWindow) {
    while (focusedWindow.getOwner() != null) {
      if (activeWindow == focusedWindow.getOwner()) {
        return true;
      }
      focusedWindow = focusedWindow.getOwner();
    }
    return false;
  }

  private boolean isFocusOwnerInsideFocusedWindow(Window window, Component focusOwner) {
    if (focusOwner == null) return false;
    return window == SwingUtilities.getWindowAncestor(focusOwner);
  }
}
