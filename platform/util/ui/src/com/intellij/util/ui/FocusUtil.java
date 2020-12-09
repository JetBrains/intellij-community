// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;

public final class FocusUtil {
  private static final Logger LOG = Logger.getInstance(FocusUtil.class);

  public static Component findFocusableComponentIn(Container searchIn, Component toSkip) {
    List<Component> components = UIUtil.uiTraverser(searchIn).toList();
    for (Component component : components) {
      if (component.equals(toSkip)) {
        continue;
      }
      if (component.isFocusable()) {
        return component;
      }
    }
    return searchIn;
  }

  @Nullable
  public static Component getMostRecentComponent(Component component, Window ancestor) {
    if (ancestor != null) {
      try {
        final Component mostRecentFocusOwner;

        mostRecentFocusOwner = (Component)
          Objects.requireNonNull(ReflectionUtil.getDeclaredMethod(KeyboardFocusManager.class,
                                                                  "getMostRecentFocusOwner", Window.class)).invoke(null, ancestor);

        if (mostRecentFocusOwner != null &&
            SwingUtilities.isDescendingFrom(mostRecentFocusOwner, component) &&
            mostRecentFocusOwner.isShowing()) {
          return mostRecentFocusOwner;
        }
      }  catch (InvocationTargetException |IllegalAccessException e) {
        LOG.debug(e);
      }
    }
    return null;
  }

  public static Component getDefaultComponentInPanel(Component component) {
    if (component instanceof JPanel) {
      JPanel container = (JPanel)component;
      final FocusTraversalPolicy policy = container.getFocusTraversalPolicy();

      if (policy == null) {
        return container;
      }

      final Component defaultComponent = policy.getDefaultComponent(container);
      if (defaultComponent == null) {
        return container;
      }
      return policy.getDefaultComponent(container);
    }
    return null;
  }
}
