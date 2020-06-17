// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;

public final class IJSwingUtilities {
  /**
   * @return true if javax.swing.SwingUtilities.findFocusOwner(component) != null
   */
  public static boolean hasFocus(Component component) {
    Component focusOwner = findFocusOwner(component);
    return focusOwner != null;
  }

  private static Component findFocusOwner(Component c) {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    // verify focusOwner is a descendant of c
    for (Component temp = focusOwner; temp != null; temp = (temp instanceof Window) ? null : temp.getParent()) {
      if (temp == c) {
        return focusOwner;
      }
    }

    return null;
  }

  /**
   * @return true if window ancestor of component was most recent focused window and most recent focused component
   * in that window was descended from component
   */
  public static boolean hasFocus2(Component component) {
    WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
    Window activeWindow = null;
    if (windowManager != null) {
      activeWindow = windowManager.getMostRecentFocusedWindow();
    }
    if (activeWindow == null) {
      return false;
    }
    Component focusedComponent = windowManager.getFocusedComponent(activeWindow);
    if (focusedComponent == null) {
      return false;
    }

    return SwingUtilities.isDescendingFrom(focusedComponent, component);
  }

  public static HyperlinkEvent createHyperlinkEvent(@Nullable String href, @NotNull Object source) {
    URL url = null;
    try {
      url = new URL(href);
    }
    catch (MalformedURLException ignored) {
    }
    return new HyperlinkEvent(source, HyperlinkEvent.EventType.ACTIVATED, url, href);
  }

  /**
   * A copy of javax.swing.SwingUtilities#updateComponentTreeUI that invokes children updateUI() first
   *
   * @param c component
   * @see SwingUtilities#updateComponentTreeUI
   */
  public static void updateComponentTreeUI(@Nullable Component c) {
    if (c == null) return;

    if (c instanceof RootPaneContainer) {
      JRootPane rootPane = ((RootPaneContainer)c).getRootPane();
      if (rootPane != null) {
        UIUtil.decorateWindowHeader(rootPane);
      }
    }

    for (Component component : UIUtil.uiTraverser(c).postOrderDfsTraversal()) {
      if (component instanceof JComponent) ((JComponent)component).updateUI();
    }
    c.invalidate();
    c.validate();
    c.repaint();
  }

  public static void moveMousePointerOn(Component component) {
    if (component != null && component.isShowing()) {
      if (Registry.is("ide.settings.move.mouse.on.default.button")) {
        Point point = component.getLocationOnScreen();
        int dx = component.getWidth() / 2;
        int dy = component.getHeight() / 2;
        try {
          new Robot().mouseMove(point.x + dx, point.y + dy);
          component.requestFocusInWindow();
        }
        catch (AWTException ignored) {
          // robot is not available
        }
      }
    }
  }

  @ApiStatus.Internal
  public static void appendComponentClassNames(@NotNull StringBuilder sb, @Nullable Component root) {
    UIUtil.uiTraverser(root).forEach(c -> appendComponentClassName(sb, root, c));
  }

  private static void appendComponentClassName(@NotNull StringBuilder sb, @Nullable Component root, @NotNull Component component) {
    sb.append("\n    ");
    for (Component p = component; root != p && p != null; p = p.getParent()) sb.append("  ");
    sb.append(component.getClass().getName());
  }
}