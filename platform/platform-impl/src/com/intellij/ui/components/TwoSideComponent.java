// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * This component wraps 'left' and 'right' components (for example two toolbars in wide header):<ul>
 * <li>left one is aligned to the left, right one is aligned to the right</li>
 * <li>left and right parts get widths according to their preferred sizes</li>
 * <li>if there is enough space wrapper provides a gap between components</li>
 * <li>in case of lack of width left part has a priority so it starts decreasing only when right part is already shrunk to its minimum size</li>
 * </ul>
 */
public final class TwoSideComponent extends NonOpaquePanel {
  private final boolean myIsLocked;

  public TwoSideComponent(@NotNull JComponent left, @NotNull JComponent right) {
    try {
      add(left);
      add(right);
      setLayout(new CommonToolbarLayout(left, right));
    } finally {
      myIsLocked = true;
    }
  }

  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    if (myIsLocked) throw new UnsupportedOperationException();
    super.addImpl(comp, constraints, index);
  }

  private static class CommonToolbarLayout extends AbstractLayoutManager {
    private final JComponent myLeft;
    private final JComponent myRight;

    CommonToolbarLayout(final JComponent left, final JComponent right) {
      myLeft = left;
      myRight = right;
    }

    @Override
    public Dimension preferredLayoutSize(final @NotNull Container parent) {

      Dimension size = new Dimension();
      Dimension leftSize = myLeft.getPreferredSize();
      Dimension rightSize = myRight.getPreferredSize();

      size.width = leftSize.width + rightSize.width;
      size.height = Math.max(leftSize.height, rightSize.height);
      JBInsets.addTo(size, parent.getInsets());
      return size;
    }

    @Override
    public void layoutContainer(final @NotNull Container parent) {
      Insets insets = parent.getInsets();
      Dimension size = parent.getSize();
      Dimension prefSize = parent.getPreferredSize();
      if (prefSize.width <= size.width) {
        myLeft.setBounds(insets.left, insets.top, myLeft.getPreferredSize().width, parent.getHeight() - insets.bottom);
        Dimension rightSize = myRight.getPreferredSize();
        myRight.setBounds(parent.getWidth() - rightSize.width - insets.left, insets.top, rightSize.width, parent.getHeight() - insets.bottom);
      }
      else {
        Dimension leftMinSize = myLeft.getMinimumSize();
        Dimension rightMinSize = myRight.getMinimumSize();

        // see IDEA-140557, always shrink left component last
        int delta = 0;
        //int delta = (prefSize.width - size.width) / 2;

        myLeft.setBounds(insets.left, insets.top, myLeft.getPreferredSize().width - delta, parent.getHeight() - insets.bottom);
        int rightX = (int)myLeft.getBounds().getMaxX();
        int rightWidth = size.width - rightX - insets.right;
        if (rightWidth < rightMinSize.width) {
          Dimension leftSize = myLeft.getSize();
          int diffToRightMin = rightMinSize.width - rightWidth;
          if (leftSize.width - diffToRightMin >= leftMinSize.width) {
            leftSize.width -= diffToRightMin;
            myLeft.setSize(leftSize);
          }
        }

        myRight.setBounds((int)myLeft.getBounds().getMaxX(), insets.top, parent.getWidth() - (int)myLeft.getBounds().getMaxX() - insets.right, parent.getHeight() - insets.bottom);
      }

      toMakeVerticallyInCenter(myLeft, parent);
      toMakeVerticallyInCenter(myRight, parent);
    }

    private static void toMakeVerticallyInCenter(JComponent comp, Container parent) {
      final Rectangle compBounds = comp.getBounds();
      Insets insets = parent.getInsets();
      int compHeight = comp.getPreferredSize().height;
      final int parentHeight = parent.getHeight()  - insets.top - insets.bottom;
      if (compHeight > parentHeight) {
        compHeight = parentHeight;
      }

      int y = (int)Math.floor(parentHeight / 2.0 - compHeight / 2.0) + insets.top;
      comp.setBounds(compBounds.x, y, compBounds.width, compHeight);
    }
  }
}