// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.plaf.UIResource;
import java.awt.*;

import static com.intellij.util.ui.JBUI.scale;

/**
 * @author Konstantin Bulenkov
 */
public class JBInsets extends Insets {
  /**
   * Creates and initializes a new {@code Insets} object with the
   * specified top, left, bottom, and right insets.
   *
   * @param top    the inset from the top.
   * @param left   the inset from the left.
   * @param bottom the inset from the bottom.
   * @param right  the inset from the right.
   */
  public JBInsets(int top, int left, int bottom, int right) {
    super(scale(top), scale(left), scale(bottom), scale(right));
  }

  public int width() {
    return left + right;
  }

  public int height() {
    return top + bottom;
  }

  public static JBInsets create(@NotNull Insets insets) {
    if (insets instanceof JBInsets) {
      JBInsets copy = new JBInsets(0, 0, 0, 0);
      copy.top = insets.top;
      copy.left = insets.left;
      copy.bottom = insets.bottom;
      copy.right = insets.right;
      return copy;
    }
     return new JBInsets(insets.top, insets.left, insets.bottom, insets.right);
  }

  public JBInsetsUIResource asUIResource() {
    return new JBInsetsUIResource(this);
  }

  public static class JBInsetsUIResource extends JBInsets implements UIResource {
    public JBInsetsUIResource(JBInsets insets) {
      super(0, 0, 0, 0);
      top = insets.top;
      left = insets.left;
      bottom = insets.bottom;
      right = insets.right;
    }
  }

  /**
   * @param dimension the size to increase
   * @param insets    the insets to add
   */
  public static void addTo(@NotNull Dimension dimension, Insets insets) {
    if (insets != null) {
      dimension.width += insets.left + insets.right;
      dimension.height += insets.top + insets.bottom;
    }
  }

  /**
   * @param dimension the size to decrease
   * @param insets    the insets to remove
   */
  public static void removeFrom(@NotNull Dimension dimension, Insets insets) {
    if (insets != null) {
      dimension.width -= insets.left + insets.right;
      dimension.height -= insets.top + insets.bottom;
    }
  }

  /**
   * @param rectangle the size to increase and the location to move
   * @param insets    the insets to add
   */
  public static void addTo(@NotNull Rectangle rectangle, Insets insets) {
    if (insets != null) {
      rectangle.x -= insets.left;
      rectangle.y -= insets.top;
      rectangle.width += insets.left + insets.right;
      rectangle.height += insets.top + insets.bottom;
    }
  }

  /**
   * @param rectangle the size to decrease and the location to move
   * @param insets    the insets to remove
   */
  public static void removeFrom(@NotNull Rectangle rectangle, Insets insets) {
    if (insets != null) {
      rectangle.x += insets.left;
      rectangle.y += insets.top;
      rectangle.width -= insets.left + insets.right;
      rectangle.height -= insets.top + insets.bottom;
    }
  }
}
