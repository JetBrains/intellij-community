// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

public final class DefaultControl implements Control {
  private final Icon expandedDefault = UIUtil.getTreeExpandedIcon();
  private final Icon collapsedDefault = UIUtil.getTreeCollapsedIcon();
  private final Icon expandedSelected = UIUtil.getTreeSelectedExpandedIcon();
  private final Icon collapsedSelected = UIUtil.getTreeSelectedCollapsedIcon();

  @NotNull
  @Override
  public Icon getIcon(boolean expanded, boolean selected) {
    return !selected
           ? expanded ? expandedDefault : collapsedDefault
           : expanded ? expandedSelected : collapsedSelected;
  }

  @Override
  public int getWidth() {
    return Math.max(
      Math.max(expandedDefault.getIconWidth(), collapsedDefault.getIconWidth()),
      Math.max(expandedSelected.getIconWidth(), collapsedSelected.getIconWidth()));
  }

  @Override
  public int getHeight() {
    return Math.max(
      Math.max(expandedDefault.getIconHeight(), collapsedDefault.getIconHeight()),
      Math.max(expandedSelected.getIconHeight(), collapsedSelected.getIconHeight()));
  }

  @Override
  public void paint(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height, boolean expanded, boolean selected) {
    Icon icon = getIcon(expanded, selected);
    icon.paintIcon(null, g,
                   x + (width - icon.getIconWidth()) / 2,
                   y + (height - icon.getIconHeight()) / 2);
  }
}
