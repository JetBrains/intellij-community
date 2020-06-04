// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Predicate;

public final class ComponentUtil {
  public static <T> T getClientProperty(@NotNull JComponent component, @NotNull Key<T> key) {
    //noinspection unchecked
    return (T)component.getClientProperty(key);
  }

  public static <T> void putClientProperty(@NotNull JComponent component, @NotNull Key<T> key, T value) {
    component.putClientProperty(key, value);
  }

  public static boolean isDisableAutoRequestFocus() {
    return Registry.is("suppress.focus.stealing.disable.auto.request.focus", true)
           && !(SystemInfo.isXfce || SystemInfo.isI3);
  }

  public static boolean isMinimized(@Nullable Window window) {
    if (!(window instanceof Frame)) {
      return false;
    }

    Frame frame = (Frame)window;
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
   * Returns the first window ancestor of the component.
   * Note that this method returns the component itself if it is a window.
   *
   * @param component the component used to find corresponding window
   * @return the first window ancestor of the component; or {@code null}
   * if the component is not a window and is not contained inside a window
   */
  public static @Nullable Window getWindow(@Nullable Component component) {
    if (component == null) {
      return null;
    }
    return component instanceof Window ? (Window)component : SwingUtilities.getWindowAncestor(component);
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
}
