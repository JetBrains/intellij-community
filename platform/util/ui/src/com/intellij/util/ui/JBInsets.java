// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public class JBInsets extends Insets {
  private final @Nullable Supplier<@Nullable Insets> unscaledSupplier;
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
  @SuppressWarnings("UseDPIAwareInsets")
  public JBInsets(int top, int left, int bottom, int right) {
    this(
      null,
      new Insets(top, left, bottom, right),
      JBUI.scale(top),
      JBUI.scale(left),
      JBUI.scale(bottom),
      JBUI.scale(right)
    );
  }

  @SuppressWarnings("UseDPIAwareInsets")
  private JBInsets(@NotNull JBInsets other) {
    super(other.top, other.left, other.bottom, other.right);
    this.unscaledSupplier = other.unscaledSupplier;
    this.unscaledDefault = new Insets(
      other.unscaledDefault.top,
      other.unscaledDefault.left,
      other.unscaledDefault.bottom,
      other.unscaledDefault.right
    );
  }

  @SuppressWarnings("UseDPIAwareInsets")
  private JBInsets(
    @Nullable Supplier<@Nullable Insets> unscaledSupplier,
    @NotNull Insets unscaledDefault,
    int scaledTop,
    int scaledLeft,
    int scaledBottom,
    int scaledRight
  ) {
    super(scaledTop, scaledLeft, scaledBottom, scaledRight);
    this.unscaledSupplier = unscaledSupplier;
    this.unscaledDefault = unscaledDefault;
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
    var result = unscaledSupplier != null ? unscaledSupplier.get() : unscaledDefault;
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
    var defInsets = defaultValue instanceof JBInsets jbInsets ? jbInsets.unscaledDefault : defaultValue;
    return create(new UIDefaultsSupplier(key), defInsets);
  }

  private static @NotNull JBInsets create(@Nullable Supplier<@Nullable Insets> unscaledSupplier, @NotNull Insets unscaledDefault) {
    // zero values will be overwritten by update()
    var result = new JBInsets(unscaledSupplier, unscaledDefault, 0, 0, 0, 0);
    result.update();
    return result;
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

  static boolean isZero(Insets insets) {
    if (insets instanceof JBInsets jbInsets && jbInsets.unscaledSupplier != null) {
      return false; // Even if these are zero now, they can be non-zero later (e.g. if the theme is changed or compact mode toggled).
    }
    // Scaling doesn't matter here, as zero is zero regardless of scaling:
    return insets.top == 0 && insets.left == 0 && insets.bottom == 0 && insets.right == 0;
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
    var copies = new JBInsets[insets.length];
    for (int i = 0; i < insets.length; i++) {
      copies[i] = create(insets[i]);
    }
    Supplier<@Nullable Insets> unscaledSupplier = new SummingSupplier(copies);
    var unscaledDefault = Objects.requireNonNull(unscaledSupplier.get());
    return create(unscaledSupplier, unscaledDefault);
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

  private static class UIDefaultsSupplier implements Supplier<@Nullable Insets> {
    private final String key;

    UIDefaultsSupplier(String key) {
      this.key = key;
    }

    @Override
    public @Nullable Insets get() {
      return UIManager.getInsets(key);
    }

    @Override
    public String toString() {
      return "UIDefaultsSupplier{" +
             "key='" + key + '\'' +
             '}';
    }
  }

  private static class SummingSupplier implements Supplier<@Nullable Insets> {
    private final JBInsets[] values;

    SummingSupplier(JBInsets[] values) {
      this.values = values;
    }

    @SuppressWarnings("UseDPIAwareInsets")
    @Override
    public @Nullable Insets get() {
      Insets unscaled = new Insets(0, 0, 0, 0);
      for (JBInsets value : values) {
        Insets unscaledValue = value.unscaledNoCopy();
        unscaled.top += unscaledValue.top;
        unscaled.left += unscaledValue.left;
        unscaled.bottom += unscaledValue.bottom;
        unscaled.right += unscaledValue.right;
      }
      return unscaled;
    }

    @Override
    public String toString() {
      return "SummingSupplier{" +
             "values=" + Arrays.toString(values) +
             '}';
    }
  }
}
