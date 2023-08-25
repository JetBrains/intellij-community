// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components;

import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

final class ComponentsNavBarPanel extends Breadcrumbs {
  private boolean isAccessibleEnabled = false;

  ComponentsNavBarPanel(@NotNull Component selectedComponent, @NotNull Consumer<? super Component> selectionHandler) {
    rebuild(selectedComponent);
    onSelect((crumb, event) -> {
      if (crumb != null) {
        ComponentItem item = (ComponentItem)crumb;
        selectionHandler.accept(item.myComponent);
      }
    });
    SwingUtilities.invokeLater(() -> scrollToLastItem());
  }

  public void setSelectedComponent(@NotNull Component component) {
    rebuild(component);
    revalidate();
    repaint();
    SwingUtilities.invokeLater(() -> scrollToLastItem());
  }

  private void rebuild(@NotNull Component component) {
    Iterable<Component> components = UIUtil.uiParents(component, false);
    List<ComponentItem> items = ContainerUtil.reverse(ContainerUtil.map(components, c -> new ComponentItem(c)));
    setCrumbs(items);
  }

  private void scrollToLastItem() {
    JBIterable<Crumb> crumbs = JBIterable.from(getCrumbs());
    Crumb last = crumbs.last();
    if (last != null) {
      scrollRectToVisible(getCrumbBounds(last));
    }
  }

  public void setAccessibleEnabled(boolean accessibleEnabled) {
    isAccessibleEnabled = accessibleEnabled;
  }

  @Override
  protected Color getBackground(Crumb crumb) {
    return isHovered(crumb) ? JBUI.CurrentTheme.StatusBar.Breadcrumbs.HOVER_BACKGROUND
                            : getBackground();
  }

  @Override
  public Font getFont() {
    Font font = super.getFont();
    return font != null ? font.deriveFont(13f) : null;
  }

  private final class ComponentItem implements Crumb {
    private final @NotNull Component myComponent;

    ComponentItem(@NotNull Component component) {
      myComponent = component;
    }

    @Override
    public Icon getIcon() {
      return HierarchyTree.Icons.findIconFor(myComponent);
    }

    @Override
    public @Nls String getText() {
      Object obj = isAccessibleEnabled ? myComponent.getAccessibleContext() : myComponent;
      return UiInspectorUtil.getClassPresentation(obj);
    }
  }
}
