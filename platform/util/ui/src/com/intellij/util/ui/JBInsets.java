// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBInsets extends Insets {
  private final Insets unscaled;

  @ApiStatus.Internal
  public JBInsets(int all) {
    this(all, all, all, all);
  }

  @ApiStatus.Internal
  public static @NotNull JBInsets emptyInsets() {
    return new JBInsets(0, 0, 0, 0);
  }

  /**
   * Creates and initializes a new {@code Insets} object with the
   * specified top, left, bottom, and right insets.
   * You should pass unscaled values only!
   *
   * @param top    the inset from the top.
   * @param left   the inset from the left.
   * @param bottom the inset from the bottom.
   * @param right  the inset from the right.
   */
  public JBInsets(int top, int left, int bottom, int right) {
    super(JBUIScale.scale(top), JBUIScale.scale(left), JBUIScale.scale(bottom), JBUIScale.scale(right));
    //noinspection UseDPIAwareInsets
    unscaled = new Insets(top, left, bottom, right);
  }

  public int width() {
    return left + right;
  }

  public int height() {
    return top + bottom;
  }

  /**
   * topBottom and leftRight should be unscaled
   */
  public static @NotNull JBInsets create(int topBottom, int leftRight) {
    return new JBInsets(topBottom, leftRight, topBottom, leftRight);
  }

  public static @NotNull JBInsets create(@NotNull Insets insets) {
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

  /**
   * Returns unscaled insets
   */
  public Insets getUnscaled() {
    //noinspection UseDPIAwareInsets
    return new Insets(unscaled.top, unscaled.left, unscaled.bottom, unscaled.right);
  }

  public JBInsetsUIResource asUIResource() {
    return new JBInsetsUIResource(this);
  }

  public static final class JBInsetsUIResource extends JBInsets implements UIResource {
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
