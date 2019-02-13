// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NotNull;

import java.awt.Graphics;
import javax.swing.Icon;

final class DefaultControl implements Control {
  private final CompoundIcon collapsedIcon = new CompoundIcon("treeCollapsed");
  private final CompoundIcon expandedIcon = new CompoundIcon("treeExpanded");

  @NotNull
  @Override
  public Icon getIcon(boolean expanded, boolean selected) {
    return expanded ? expandedIcon.getIcon(selected) : collapsedIcon.getIcon(selected);
  }

  @Override
  public int getWidth() {
    return Math.max(expandedIcon.getWidth(), collapsedIcon.getWidth());
  }

  @Override
  public int getHeight() {
    return Math.max(expandedIcon.getHeight(), collapsedIcon.getHeight());
  }

  @Override
  public void paint(@NotNull Graphics g, int x, int y, int width, int height, boolean expanded, boolean selected) {
    Icon icon = getIcon(expanded, selected);
    icon.paintIcon(null, g,
                   x + (width - icon.getIconWidth()) / 2,
                   y + (height - icon.getIconHeight()) / 2);
  }

  private static final class CompoundIcon {
    private final Icon defaultIcon;
    private final Icon selectedIcon;

    private CompoundIcon(String name) {
      defaultIcon = LafIconLookup.getIcon(name);
      selectedIcon = LafIconLookup.getSelectedIcon(name);
    }

    @NotNull
    private Icon getIcon(boolean selected) {
      return selected ? selectedIcon : defaultIcon;
    }

    private int getWidth() {
      return Math.max(selectedIcon.getIconWidth(), defaultIcon.getIconWidth());
    }

    private int getHeight() {
      return Math.max(selectedIcon.getIconHeight(), defaultIcon.getIconHeight());
    }
  }
}
