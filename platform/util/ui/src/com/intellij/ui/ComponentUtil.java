// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Predicate;

public final class ComponentUtil {
  @NotNull
  public static Component findUltimateParent(@NotNull Component c) {
    Component eachParent = c;
    while (true) {
      if (eachParent.getParent() == null) {
        return eachParent;
      }
      eachParent = eachParent.getParent();
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
  @Nullable
  public static Window getWindow(@Nullable Component component) {
    if (component == null) {
      return null;
    }
    return component instanceof Window ? (Window)component : SwingUtilities.getWindowAncestor(component);
  }

  @Nullable
  public static Component findParentByCondition(@Nullable Component c, @NotNull Predicate<? super Component> condition) {
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
  @Nullable
  @Contract(pure = true)
  public static <T> T getParentOfType(@NotNull Class<? extends T> type, Component component) {
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
