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

package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.ui.JBUI.ScaleType.PIX_SCALE;

/**
 * @author max
 * @author Konstantin Bulenkov
 * @author tav
 *
 * @see ColorIcon
 */
public class EmptyIcon extends JBUI.CachingScalableJBIcon<EmptyIcon> {
  private static final Map<Pair<Integer, Boolean>, EmptyIcon> cache = new HashMap<Pair<Integer, Boolean>, EmptyIcon>();

  public static final Icon ICON_16 = JBUI.scale(create(16));
  public static final Icon ICON_18 = JBUI.scale(create(18));
  public static final Icon ICON_8 = JBUI.scale(create(8));
  public static final Icon ICON_0 = JBUI.scale(create(0));

  protected final int width;
  protected final int height;
  private final boolean myUseCache;

  static {
    JBUI.addPropertyChangeListener(JBUI.USER_SCALE_FACTOR_PROPERTY, new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        cache.clear();
      }
    });
  }

  /**
   * Creates an icon of the provided size.
   *
   * Use {@link JBUI#scale(JBUI.JBIcon)} to meet HiDPI.
   */
  public static EmptyIcon create(int size) {
    return create(size, size);
  }

  /**
   * Creates an icon of the provided size.
   *
   * Use {@link JBUI#scale(JBUI.JBIcon)} to meet HiDPI.
   */
  public static EmptyIcon create(int width, int height) {
    return create(width, height, true);
  }

  /**
   * Creates an icon of the size of the provided icon base.
   */
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

  protected EmptyIcon(EmptyIcon icon) {
    super(icon);
    width = icon.width;
    height = icon.height;
    myUseCache = icon.myUseCache;
  }

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

  public EmptyIconUIResource asUIResource() {
    return new EmptyIconUIResource(this);
  }

  public static class EmptyIconUIResource extends EmptyIcon implements UIResource {
    protected EmptyIconUIResource(EmptyIcon icon) {
      super(icon);
    }

    @NotNull
    @Override
    public EmptyIconUIResource copy() {
      return new EmptyIconUIResource(this);
    }
  }
}
