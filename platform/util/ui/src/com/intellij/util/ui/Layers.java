// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.ui;


import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public final class Layers extends JLayeredPane {

  private final ArrayList<Component> myComponents = new ArrayList<>();

  public Layers() {
    setLayout(new Layout());
  }
  @Override
  public Dimension getMinimumSize() {
    if (!isMinimumSizeSet())
      return JBUI.emptySize();
    return super.getMinimumSize();
  }

  private final class Layout implements LayoutManager2 {
    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
      myComponents.add(comp);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
      return 0;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
      return 0;
    }

    @Override
    public void invalidateLayout(Container target) {
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
      int maxWidth = 0;
      int maxHeight = 0;
      for (Component each : myComponents) {
        Dimension min = each.getMaximumSize();
        maxWidth = Math.min(maxWidth, min.width);
        maxHeight = Math.min(maxHeight, min.height);
      }
      return new Dimension(maxWidth, maxHeight);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
      myComponents.add(comp);
    }

    @Override
    public void layoutContainer(Container parent) {
      for (Component each : myComponents) {
        each.setBounds(0, 0, parent.getWidth() - 1, parent.getHeight() - 1);
      }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      int minWidth = 0;
      int minHeight = 0;
      for (Component each : myComponents) {
        Dimension min = each.getMinimumSize();
        minWidth = Math.min(minWidth, min.width);
        minHeight = Math.min(minHeight, min.height);
      }
      return new Dimension(minWidth, minHeight);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int prefWidth = 0;
      int prefHeight = 0;
      for (Component each : myComponents) {
        Dimension min = each.getPreferredSize();
        prefWidth = Math.max(prefWidth, min.width);
        prefHeight = Math.max(prefHeight, min.height);
      }
      return new Dimension(prefWidth, prefHeight);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
      myComponents.remove(comp);
    }
  }

}
