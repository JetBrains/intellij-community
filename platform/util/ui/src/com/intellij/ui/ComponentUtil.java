// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ComponentUtil {
  private static final @NonNls String FOCUS_PROXY_KEY = "isFocusProxy";

  /**
   * @deprecated use {@link ClientProperty#get(Component, Key)} instead
   */
  @Deprecated
  public static <T> T getClientProperty(@NotNull JComponent component, @NotNull Key<T> key) {
    return ClientProperty.get(component, key);
  }

  /**
   * @deprecated use {@link JComponent#putClientProperty(Object, Object)} or {@link ClientProperty#put(JComponent, Key, Object)} instead
   */
  @Deprecated
  public static <T> void putClientProperty(@NotNull JComponent component, @NotNull Key<T> key, T value) {
    component.putClientProperty(key, value);
  }

  public static boolean isMinimized(@Nullable Window window) {
    if (!(window instanceof Frame frame)) {
      return false;
    }

    return frame.getExtendedState() == Frame.ICONIFIED;
  }

  public static @NotNull Window getActiveWindow() {
    for (Window each : Window.getWindows()) {
      if (each.isVisible() && each.isActive()) {
        return each;
      }
    }
    return JOptionPane.getRootFrame();
  }

  public static @NotNull Component findUltimateParent(@NotNull Component c) {
    Component parent = c;
    while (true) {
      Container nextParent = parent.getParent();
      if (nextParent == null) {
        return parent;
      }
      parent = nextParent;
    }
  }

  /**
   * Returns the first window ancestor of the component,
   * or {@code null} the component is not a window and is not contained inside a window.
   * Note that this method returns the component itself if it is a window.
   */
  public static @Nullable Window getWindow(@Nullable Component component) {
    return component == null ? null :
           component instanceof Window ? (Window)component :
           SwingUtilities.getWindowAncestor(component);
  }

  public static @Nullable Component findParentByCondition(@Nullable Component c, @NotNull Predicate<? super Component> condition) {
    Component eachParent = c;
    while (eachParent != null) {
      if (condition.test(eachParent)) return eachParent;
      eachParent = eachParent.getParent();
    }
    return null;
  }

  /**
   * Searches above in the component hierarchy starting from the specified component.
   * Note that the initial component is also checked.
   *
   * @param type      expected class
   * @param component initial component
   * @return a component of the specified type, or {@code null} if the search is failed
   * @see SwingUtilities#getAncestorOfClass
   */
  @Contract(pure = true)
  public static @Nullable <T> T getParentOfType(@NotNull Class<? extends T> type, Component component) {
    while (component != null) {
      if (type.isInstance(component)) {
        //noinspection unchecked
        return (T)component;
      }
      component = component.getParent();
    }
    return null;
  }

  /**
   * @param component a view component of the requested scroll pane
   * @return a scroll pane for the given component or {@code null} if none
   */
  public static @Nullable JScrollPane getScrollPane(@Nullable Component component) {
    return component instanceof JScrollBar
           ? getScrollPane((JScrollBar)component)
           : getScrollPane(component instanceof JViewport
                           ? (JViewport)component
                           : getViewport(component));
  }

  /**
   * @param bar a scroll bar of the requested scroll pane
   * @return a scroll pane for the given scroll bar or {@code null} if none
   */
  public static @Nullable JScrollPane getScrollPane(@Nullable JScrollBar bar) {
    Container parent = bar == null ? null : bar.getParent();
    return parent instanceof JScrollPane ? (JScrollPane)parent : null;
  }

  /**
   * @param viewport a viewport of the requested scroll pane
   * @return a scroll pane for the given viewport or {@code null} if none
   */
  public static @Nullable JScrollPane getScrollPane(@Nullable JViewport viewport) {
    Container parent = viewport == null ? null : viewport.getParent();
    return parent instanceof JScrollPane ? (JScrollPane)parent : null;
  }

  /**
   * @param component a view component of the requested viewport
   * @return a viewport for the given component or {@code null} if none
   */
  public static @Nullable JViewport getViewport(@Nullable Component component) {
    Container parent = component == null ? null : SwingUtilities.getUnwrappedParent(component);
    return parent instanceof JViewport ? (JViewport)parent : null;
  }

  public static @NotNull <T extends JComponent> java.util.List<T> findComponentsOfType(JComponent parent, @NotNull Class<? extends T> cls) {
    java.util.List<T> result = new ArrayList<>();
    findComponentsOfType(parent, cls, result);
    return result;
  }

  private static <T extends JComponent> void findComponentsOfType(JComponent parent,
                                                                  @NotNull Class<T> cls,
                                                                  @NotNull List<? super T> result) {
    if (parent == null) return;
    if (cls.isAssignableFrom(parent.getClass())) {
      @SuppressWarnings("unchecked") final T t = (T)parent;
      result.add(t);
    }
    for (Component c : parent.getComponents()) {
      if (c instanceof JComponent) {
        findComponentsOfType((JComponent)c, cls, result);
      }
    }
  }

  public static boolean isFocusProxy(@Nullable Component c) {
    return c instanceof JComponent && Boolean.TRUE.equals(((JComponent)c).getClientProperty(FOCUS_PROXY_KEY));
  }

  public static boolean isMeaninglessFocusOwner(@Nullable Component c) {
    if (c == null || !c.isShowing()) {
      return true;
    }
    return c instanceof JFrame || c instanceof JDialog || c instanceof JWindow || c instanceof JRootPane || isFocusProxy(c);
  }
}
