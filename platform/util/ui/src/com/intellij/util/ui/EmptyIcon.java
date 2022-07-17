// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author max
 * @author Konstantin Bulenkov
 * @author tav
 *
 * @see ColorIcon
 */
 public class EmptyIcon extends JBCachingScalableIcon<EmptyIcon> {
  private static final Map<Pair<Integer, Boolean>, EmptyIcon> cache = new HashMap<>();

  public static final Icon ICON_18 = JBUIScale.scaleIcon(create(18));
  public static final Icon ICON_16 = JBUIScale.scaleIcon(create(16));
  public static final Icon ICON_13 = JBUIScale.scaleIcon(create(13));
  public static final Icon ICON_8 = JBUIScale.scaleIcon(create(8));
  public static final Icon ICON_0 = JBUIScale.scaleIcon(create(0));

  protected final int width;
  protected final int height;
  private final boolean myUseCache;

  static {
    JBUIScale.addUserScaleChangeListener(event -> cache.clear());
  }

  /**
   * Creates an icon of the provided size.
   *
   * Use {@link JBUIScale#scaleIcon(JBScalableIcon)} (JBScalableIcon)} to meet HiDPI.
   */
  public static @NotNull EmptyIcon create(int size) {
    return create(size, size);
  }

  /**
   * Creates an icon of the provided size.
   *
   * Use {@link JBUIScale#scaleIcon(JBScalableIcon)} (JBScalableIcon)} to meet HiDPI.
   */
  public static @NotNull EmptyIcon create(int width, int height) {
    return create(width, height, true);
  }

  /**
   * Creates an icon of the size of the provided icon base.
   */
  public static @NotNull EmptyIcon create(@NotNull Icon base) {
    return create(base.getIconWidth(), base.getIconHeight());
  }

  /**
   * @deprecated use {@linkplain #create(int)} for caching.
   */
  @Deprecated(forRemoval = true)
  public EmptyIcon(int size) {
    this(size, size, false);
  }

  /**
   * @deprecated use {@linkplain #create(int, int)} for caching.
   */
  @Deprecated
  public EmptyIcon(int width, int height) {
    this(width, height, false);
  }

  private EmptyIcon(int width, int height, boolean useCache) {
    this.width = width;
    this.height = height;
    myUseCache = useCache;
  }

  protected EmptyIcon(@NotNull EmptyIcon icon) {
    super(icon);
    width = icon.width;
    height = icon.height;
    myUseCache = icon.myUseCache;
  }

  @Override
  public @NotNull EmptyIcon copy() {
    return new EmptyIcon(this);
  }

  @Override
  public @NotNull EmptyIcon withIconPreScaled(boolean preScaled) {
    if (myUseCache && isIconPreScaled() != preScaled) {
      return create(width, height, preScaled);
    }
    return (EmptyIcon)super.withIconPreScaled(preScaled);
  }

  private static @NotNull EmptyIcon create(int width, int height, boolean preScaled) {
    if (width != height || width >= 129) {
      EmptyIcon icon = new EmptyIcon(width, height, true);
      icon.setIconPreScaled(preScaled);
      return icon;
    }

    return cache.computeIfAbsent(new Pair<>(width, preScaled), __ -> {
      EmptyIcon icon = new EmptyIcon(width, height, true);
      icon.setIconPreScaled(preScaled);
      return icon;
    });
  }

  @Override
  public int getIconWidth() {
    return (int)Math.ceil(scaleVal(width));
  }

  @Override
  public int getIconHeight() {
    return (int)Math.ceil(scaleVal(height));
  }

  @Override
  public void paintIcon(Component component, Graphics g, int i, int j) {
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EmptyIcon)) return false;

    final EmptyIcon icon = (EmptyIcon)o;

    if (scaleVal(height, DerivedScaleType.PIX_SCALE) != icon.scaleVal(icon.height, DerivedScaleType.PIX_SCALE)) return false;
    if (scaleVal(width, DerivedScaleType.PIX_SCALE) != icon.scaleVal(icon.width, DerivedScaleType.PIX_SCALE)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    double result = scaleVal(width, DerivedScaleType.PIX_SCALE);
    result = 31 * result + scaleVal(height, DerivedScaleType.PIX_SCALE);
    return (int)result;
  }

  public @NotNull EmptyIconUIResource asUIResource() {
    return new EmptyIconUIResource(this);
  }

  public static class EmptyIconUIResource extends EmptyIcon implements UIResource {
    EmptyIconUIResource(@NotNull EmptyIcon icon) {
      super(icon);
    }

    @Override
    public @NotNull EmptyIconUIResource copy() {
      return new EmptyIconUIResource(this);
    }
  }
}
