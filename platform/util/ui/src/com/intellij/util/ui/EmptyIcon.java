// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.ui.scale.DerivedScaleType.PIX_SCALE;

/**
 * @author max
 * @author Konstantin Bulenkov
 * @author tav
 *
 * @see ColorIcon
 */
 public class EmptyIcon extends JBCachingScalableIcon<EmptyIcon> {
//public class EmptyIcon extends JBUI.CachingScalableJBIcon<JBUI.CachingScalableJBIcon> { // backward compatible version
  private static final Map<Pair<Integer, Boolean>, EmptyIcon> cache = new HashMap<>();

  public static final Icon ICON_18 = JBUI.scale(create(18));
  public static final Icon ICON_16 = JBUI.scale(create(16));
  public static final Icon ICON_13 = JBUI.scale(create(13));
  public static final Icon ICON_8 = JBUI.scale(create(8));
  public static final Icon ICON_0 = JBUI.scale(create(0));

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
  @NotNull
  public static EmptyIcon create(int size) {
    return create(size, size);
  }

  /**
   * Creates an icon of the provided size.
   *
   * Use {@link JBUIScale#scaleIcon(JBScalableIcon)} (JBScalableIcon)} to meet HiDPI.
   */
  @NotNull
  public static EmptyIcon create(int width, int height) {
    return create(width, height, true);
  }

  /**
   * Creates an icon of the size of the provided icon base.
   */
  @NotNull
  public static EmptyIcon create(@NotNull Icon base) {
    return create(base.getIconWidth(), base.getIconHeight());
  }

  /**
   * @deprecated use {@linkplain #create(int)} for caching.
   */
  @Deprecated
  public EmptyIcon(int size) {
    this(size, size);
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

  //@NotNull
  //@Override
  //public /*EmptyIcon*/ JBUI.CachingScalableJBIcon scale(float scale) { // backward compatible version
  //  return super.scale(scale);
  //}

  @NotNull
  @Override
  public EmptyIcon copy() {
    return new EmptyIcon(this);
  }

  @NotNull
  @Override
  public EmptyIcon withIconPreScaled(boolean preScaled) {
    if (myUseCache && isIconPreScaled() != preScaled) {
      return create(width, height, preScaled);
    }
    return (EmptyIcon)super.withIconPreScaled(preScaled);
  }

  @NotNull
  private static EmptyIcon create(int width, int height, boolean preScaled) {
    Pair<Integer, Boolean> key = key(width, height, preScaled);
    EmptyIcon icon = key != null ? cache.get(key) : null;
    if (icon == null) {
      icon = new EmptyIcon(width, height, true);
      icon.setIconPreScaled(preScaled);
      if (key != null) cache.put(key, icon);
    }
    return icon;
  }

  @Nullable
  private static Pair<Integer, Boolean> key(int width, int height, boolean preScaled) {
    return width == height && width < 129 ? Pair.create(width, preScaled) : null;
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

    if (scaleVal(height, PIX_SCALE) != icon.scaleVal(icon.height, PIX_SCALE)) return false;
    if (scaleVal(width, PIX_SCALE) != icon.scaleVal(icon.width, PIX_SCALE)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    double result = scaleVal(width, PIX_SCALE);
    result = 31 * result + scaleVal(height, PIX_SCALE);
    return (int)result;
  }

  @NotNull
  public EmptyIconUIResource asUIResource() {
    return new EmptyIconUIResource(this);
  }

  public static class EmptyIconUIResource extends EmptyIcon implements UIResource {
    EmptyIconUIResource(@NotNull EmptyIcon icon) {
      super(icon);
    }

    @NotNull
    @Override
    public EmptyIconUIResource copy() {
      return new EmptyIconUIResource(this);
    }
  }
}
