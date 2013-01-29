/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.ui;


import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class Layers extends JLayeredPane {

  private final ArrayList<Component> myComponents = new ArrayList<Component>();

  public Layers() {
    setLayout(new Layout());
  }
  @Override
  public Dimension getMinimumSize() {
    if (!isMinimumSizeSet())
      return new Dimension(0, 0);
    return super.getMinimumSize();
  }

  private class Layout implements LayoutManager2 {
    public void addLayoutComponent(Component comp, Object constraints) {
      myComponents.add(comp);
    }

    public float getLayoutAlignmentX(Container target) {
      return 0;
    }

    public float getLayoutAlignmentY(Container target) {
      return 0;
    }

    public void invalidateLayout(Container target) {
    }

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

    public void addLayoutComponent(String name, Component comp) {
      myComponents.add(comp);
    }

    public void layoutContainer(Container parent) {
      for (Component each : myComponents) {
        each.setBounds(0, 0, parent.getWidth() - 1, parent.getHeight() - 1);
      }
    }

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

    public void removeLayoutComponent(Component comp) {
      myComponents.remove(comp);
    }
  }

}
