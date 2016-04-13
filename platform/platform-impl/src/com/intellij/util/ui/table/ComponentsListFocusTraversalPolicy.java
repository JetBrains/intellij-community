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

import java.awt.*;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public abstract class ComponentsListFocusTraversalPolicy extends FocusTraversalPolicy {
  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    final List<Component> components = getOrderedComponents();
    int i = components.indexOf(aComponent);
    if (i != -1) return searchShowing(components, i + 1, true);
    return null;
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    final List<Component> components = getOrderedComponents();
    int i = components.indexOf(aComponent);
    if (i != -1) return searchShowing(components, i - 1, false);
    return null;
  }

  @Override
  public Component getFirstComponent(Container aContainer) {
    final List<Component> components = getOrderedComponents();
    return components.isEmpty() ? null : searchShowing(components, 0, true);
  }

  @Override
  public Component getLastComponent(Container aContainer) {
    final List<Component> components = getOrderedComponents();
    return components.isEmpty() ? null : searchShowing(components, -1, false);
  }

  @Override
  public Component getDefaultComponent(Container aContainer) {
    final List<Component> components = getOrderedComponents();
    return components.isEmpty() ? null : searchShowing(components, 0, true);
  }

  @NotNull
  protected abstract List<Component> getOrderedComponents();

  private static Component searchShowing(@NotNull List<Component> components, int start, boolean fwd) {
    for (int k = 0; k < components.size(); ++k) {
      Component c = getCycled(components, start + (fwd ? 1 : -1)*k);
      if (c != null && c.isShowing()) return c;
    }
    return null;
  }

  private static Component getCycled(@NotNull List<Component> components, int i) {
    if (components.isEmpty()) return null;
    int s = components.size();
    i = (s + i % s) % s;
    return components.get(i);
  }
}