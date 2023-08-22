// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBInsets extends Insets {
  private final @Nullable String key;
  private final @NotNull Insets unscaledDefault;

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
    this(null, top, left, bottom, right);
  }

  @SuppressWarnings("UseDPIAwareInsets")
  private JBInsets(@NotNull JBInsets other) {
    super(other.top, other.left, other.bottom, other.right);
    this.key = other.key;
    this.unscaledDefault = new Insets(
      other.unscaledDefault.top,
      other.unscaledDefault.left,
      other.unscaledDefault.bottom,
      other.unscaledDefault.right
    );
  }

  @SuppressWarnings("UseDPIAwareInsets")
  private JBInsets(@Nullable String key, int top, int left, int bottom, int right) {
    super(JBUIScale.scale(top), JBUIScale.scale(left), JBUIScale.scale(bottom), JBUIScale.scale(right));
    this.key = key;
    unscaledDefault = new Insets(top, left, bottom, right);
  }

  /**
   * Updates the current values of these insets.
   * <p>
   *   If these insets have a UI Defaults key, then a fresh value (assumed to be unscaled)
   *   is first retrieved, otherwise the default values are used. Then these values are scaled
   *   according to the current {@link com.intellij.ui.scale.ScaleType#USR_SCALE} value.
   * </p>
   */
  public void update() {
    var unscaled = unscaledNoCopy();
    top = JBUIScale.scale(unscaled.top);
    left = JBUIScale.scale(unscaled.left);
    bottom = JBUIScale.scale(unscaled.bottom);
    right = JBUIScale.scale(unscaled.right);
  }

  private @NotNull Insets unscaledNoCopy() {
    var result = key != null ? UIManager.getInsets(key) : unscaledDefault;
    if (result == null) {
      result = unscaledDefault;
    }
    return result;
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
    if (insets instanceof JBInsets jbInsets) {
      return new JBInsets(jbInsets);
    }
     return new JBInsets(insets.top, insets.left, insets.bottom, insets.right);
  }

  public static @NotNull JBInsets create(@NotNull String key, @NotNull Insets defaultValue) {
    var unscaledDefault = unwrap(defaultValue);
    return new JBInsets(key, unscaledDefault.top, unscaledDefault.left, unscaledDefault.bottom, unscaledDefault.right);
  }

  /**
   * Returns unscaled insets
   */
  public Insets getUnscaled() {
    var unscaled = unscaledNoCopy();
    //noinspection UseDPIAwareInsets
    return new Insets(unscaled.top, unscaled.left, unscaled.bottom, unscaled.right);
  }

  public JBInsetsUIResource asUIResource() {
    return new JBInsetsUIResource(this);
  }

  public static final class JBInsetsUIResource extends JBInsets implements UIResource {
    public JBInsetsUIResource(JBInsets insets) {
      super(insets);
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

  public static @NotNull JBInsets addInsets(@NotNull Insets @NotNull ... insets) {
    JBInsets result = emptyInsets();
    for (Insets inset : insets) {
      result.top += inset.top;
      result.left += inset.left;
      result.bottom += inset.bottom;
      result.right += inset.right;
    }
    return result;
  }

  /**
   * Get safely unscaled Insets if the parameter is an instance of JBInsets.
   *
   * @param insets the insets to unwrap
   * @return the unwrapped Insets
   */
  @ApiStatus.Internal
  public static Insets unwrap(@NotNull Insets insets) {
    if (insets instanceof JBInsets jbInsets) {
      return jbInsets.getUnscaled();
    }
    return insets;
  }

  /**
   * Get the unscaled inset values.
   * <p>
   *   If the {@code insets} parameter value is not an instance of {@code JBInsets}, then it's assumed to be already unscaled.
   * </p>
   *
   * @param insets the Insets to unscale
   * @return the unscaled Insets
   */
  @ApiStatus.Internal
  public static Insets unscale(@NotNull Insets insets) {
    if (insets instanceof JBInsets jbInsets) {
      return jbInsets.getUnscaled();
    }
    else {
      return insets;
    }
  }
}
