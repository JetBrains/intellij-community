/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.table;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public abstract class ComponentsListFocusTraversalPolicy extends LayoutFocusTraversalPolicy {
  private static final int FORWARD = 1;
  private static final int BACKWARD = -1;

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    final List<Component> components = getOrderedComponents();
    int i = components.indexOf(aComponent);
    Component after = (i != -1 ? searchShowing(components, i + 1, FORWARD) : null);
    if (after == null) {
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
    if (before == null) {
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

  @NotNull
  protected abstract List<Component> getOrderedComponents();

  private static Component searchShowing(@NotNull List<Component> components, int start, int direction) {
    for (int i = start; i >= 0 && i < components.size(); i += direction) {
      Component c = components.get(i);
      if (c != null && c.isShowing()) return c;
    }
    return null;
  }
}