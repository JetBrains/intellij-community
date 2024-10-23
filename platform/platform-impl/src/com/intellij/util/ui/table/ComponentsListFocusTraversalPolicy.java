// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.table;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public abstract class ComponentsListFocusTraversalPolicy extends LayoutFocusTraversalPolicy {
  private static final int FORWARD = 1;
  private static final int BACKWARD = -1;
  private final boolean myReturnNullIfOutsideOfOrderedComponents;

  public ComponentsListFocusTraversalPolicy() {
    this(false);
  }

  /**
   * @param returnNullIfOutsideOfOrderedComponents specifies whether {@code getComponentAfter} and {@code getComponentBefore} should
   *                                               return null if focus goes beyond ordered components. When these methods return null,
   *                                               it indicates that focus should go to the next/previous component outside of this focus
   *                                               traversal provider. The default behavior is to fall back to {@code LayoutTraversalPolicy}
   *                                               which may add unwanted components into the focus order.
   */
  public ComponentsListFocusTraversalPolicy(boolean returnNullIfOutsideOfOrderedComponents) {
    myReturnNullIfOutsideOfOrderedComponents = returnNullIfOutsideOfOrderedComponents;
  }

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    final List<Component> components = getOrderedComponents();
    int i = components.indexOf(aComponent);
    Component after = (i != -1 ? searchShowing(components, i + 1, FORWARD) : null);
    if (after == null && !myReturnNullIfOutsideOfOrderedComponents) {
      // Let LayoutFTP detect the nearest focus cycle root and handle cycle icity correctly.
      return super.getComponentAfter(aContainer, aComponent);
    }
    return after;
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    final List<Component> components = getOrderedComponents();
    int i = components.indexOf(aComponent);
    Component before = (i != -1 ? searchShowing(components, i - 1, BACKWARD) : null);
    if (before == null && !myReturnNullIfOutsideOfOrderedComponents) {
      // Let LayoutFTP detect the nearest focus cycle root and handle cyclicity correctly.
      return super.getComponentBefore(aContainer, aComponent);
    }
    return before;
  }

  @Override
  public Component getFirstComponent(Container aContainer) {
    final List<Component> components = getOrderedComponents();
    return components.isEmpty() ? null : searchShowing(components, 0, FORWARD);
  }

  @Override
  public Component getLastComponent(Container aContainer) {
    final List<Component> components = getOrderedComponents();
    return components.isEmpty() ? null : searchShowing(components, components.size() - 1, BACKWARD);
  }

  @Override
  public Component getDefaultComponent(Container aContainer) {
    final List<Component> components = getOrderedComponents();
    return components.isEmpty() ? null : searchShowing(components, 0, FORWARD);
  }

  @Unmodifiable
  protected abstract @NotNull List<Component> getOrderedComponents();

  private static Component searchShowing(@NotNull List<? extends Component> components, int start, int direction) {
    for (int i = start; i >= 0 && i < components.size(); i += direction) {
      Component c = components.get(i);
      if (c != null && c.isShowing()) return c;
    }
    return null;
  }
}