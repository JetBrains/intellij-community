// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

public final class FocusUtil {
  private static final @NonNls String SWING_FOCUS_OWNER_PROPERTY = "focusOwner";

  public static Component findFocusableComponentIn(Component searchIn, Component toSkip) {
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

  public static @Nullable Component getMostRecentComponent(Component component, Window ancestor) {
    if (ancestor == null) {
      return null;
    }

    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      MethodHandle owner =
        lookup.findStatic(KeyboardFocusManager.class, "getMostRecentFocusOwner", MethodType.methodType(Component.class, Window.class));
      Component mostRecentFocusOwner = (Component)owner.invokeExact(ancestor);
      if (mostRecentFocusOwner != null &&
          SwingUtilities.isDescendingFrom(mostRecentFocusOwner, component) &&
          mostRecentFocusOwner.isShowing()) {
        return mostRecentFocusOwner;
      }
    }
    catch (Throwable e) {
      Logger.getInstance(FocusUtil.class).debug(e);
    }
    return null;
  }

  public static Component getDefaultComponentInPanel(Component component) {
    if (!(component instanceof JPanel container)) {
      return null;
    }

    FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
    if (policy == null) {
      return container;
    }

    final Component defaultComponent = policy.getDefaultComponent(container);
    if (defaultComponent == null) {
      return container;
    }
    return policy.getDefaultComponent(container);
  }

  /**
   * Add {@link PropertyChangeListener} listener to the current {@link KeyboardFocusManager} until the {@code parentDisposable} is disposed
   */
  public static void addFocusOwnerListener(@NotNull Disposable parentDisposable, @NotNull PropertyChangeListener listener) {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, listener);
    Disposer.register(parentDisposable, () -> {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, listener);
    });
  }
}
