/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.frame;

import javax.swing.*;
import java.awt.*;

public class WrappedFlowLayout extends FlowLayout {

  public WrappedFlowLayout(int hGap, int vGap) {
    super(FlowLayout.LEADING, hGap, vGap);
  }

  @Override
  public Dimension preferredLayoutSize(Container target) {
    Dimension baseSize = super.preferredLayoutSize(target);
    if (getAlignOnBaseline()) {
      return baseSize;
    }

    return getWrappedSize(target);
  }

  public Dimension getWrappedSize(Container target) {
    Container parent = SwingUtilities.getUnwrappedParent(target);
    int maxWidth = parent.getWidth() - (parent.getInsets().left + parent.getInsets().right);

    return getDimension(target, maxWidth);
  }

  public Dimension getDimension(Container target, int maxWidth) {
    Insets insets = target.getInsets();
    int height = insets.top + insets.bottom;
    int width = insets.left + insets.right;

    int rowHeight = 0;
    int rowWidth = insets.left + insets.right;

    boolean isVisible = false;
    boolean start = true;

    synchronized (target.getTreeLock()) {
      for (int i = 0; i < target.getComponentCount(); i++) {
        Component component = target.getComponent(i);
        if (component.isVisible()) {
          isVisible = true;
          Dimension size = component.getPreferredSize();

          if (rowWidth + getHgap() + size.width > maxWidth && !start) {
            height += getVgap() + rowHeight;
            width = Math.max(width, rowWidth);

            rowWidth = insets.left + insets.right;
            rowHeight = 0;
          }

          rowWidth += getHgap() + size.width;
          rowHeight = Math.max(rowHeight, size.height);

          start = false;
        }
      }
      height += getVgap() + rowHeight;
      width = Math.max(width, rowWidth);

      if (!isVisible) {
        return super.preferredLayoutSize(target);
      }
      else {
        return new Dimension(width, height);
      }
    }
  }

  @Override
  public Dimension minimumLayoutSize(Container target) {
    if (getAlignOnBaseline()) return super.minimumLayoutSize(target);

    return getWrappedSize(target);
  }
}
