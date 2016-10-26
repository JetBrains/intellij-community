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
public class EmptyIcon extends JBUI.ScalableJBIcon {
  private static final Map<Pair<Integer, Boolean>, EmptyIcon> cache =
    new HashMap<Pair<Integer, Boolean>, EmptyIcon>(); // (size, preScaled) -> (icon)

  public static final Icon ICON_16 = JBUI.scale(create(16));
  public static final Icon ICON_18 = JBUI.scale(create(18));
  public static final Icon ICON_8 = JBUI.scale(create(8));
  public static final Icon ICON_0 = JBUI.scale(create(0));

  protected final int width;
  protected final int height;
  private EmptyIcon myScaledCache;
  protected boolean myUseCache;

  /**
   * Creates an icon of the provided size.
   *
   * Use {@link JBUI#scale(EmptyIcon)} to meet HiDPI.
   */
  public static EmptyIcon create(int size) {
    return create(size, size);
  }

  /**
   * Creates an icon of the provided size.
   *
   * Use {@link JBUI#scale(EmptyIcon)} to meet HiDPI.
   */
  public static EmptyIcon create(int width, int height) {
    return create(width, height, true, false);
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
  public EmptyIcon(int size) {
    this(size, size);
  }

  /**
   * @deprecated use {@linkplain #create(int, int)} for caching.
   */
  public EmptyIcon(int width, int height) {
    this(width, height, false);
  }

  private EmptyIcon(int width, int height, boolean useCache) {
    this.width = width;
    this.height = height;
    this.myUseCache = useCache;
  }

  /**
   * @deprecated use {@linkplain #create(javax.swing.Icon)} for caching.
   */
  public EmptyIcon(@NotNull Icon base) {
    this(base.getIconWidth(), base.getIconHeight());
  }

  @Override
  public EmptyIcon withPreScaled(boolean preScaled) {
    if (myUseCache && isPreScaled() != preScaled) {
      Pair<Integer, Boolean> key = key(width, height, preScaled);
      if (key != null) cache.remove(key); // rather useless to keep it in cache
      return create(width, height, preScaled, false);
    }
    return (EmptyIcon)super.withPreScaled(preScaled);
  }

  private static EmptyIcon create(int width, int height, boolean preScaled, boolean asUIResource) {
    Pair<Integer, Boolean> key = key(width, height, preScaled);
    EmptyIcon icon = (key != null) ? cache.get(key) : null;
    if (icon == null) {
      icon = asUIResource ? new EmptyIconUIResource(width, height, true) : new EmptyIcon(width, height, true);
      icon.setPreScaled(preScaled);
      if (key != null) cache.put(key, icon);
    }
    return icon;
  }

  private static Pair<Integer, Boolean> key(int width, int height, boolean preScaled) {
    return (width == height && width < 129) ? Pair.create(width, preScaled) : null;
  }

  @Override
  public int getIconWidth() {
    return scaleVal(width);
  }

  @Override
  public int getIconHeight() {
    return scaleVal(height);
  }

  @Override
  public void paintIcon(Component component, Graphics g, int i, int j) {
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EmptyIcon)) return false;

    final EmptyIcon icon = (EmptyIcon)o;

    if (height != icon.height) return false;
    if (width != icon.width) return false;
    if (getScale() != icon.getScale()) return false;
    if (isPreScaled() != icon.isPreScaled()) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = width;
    result = 31 * result + height;
    result = 31 * result + (getScale() != +0.0f ? Float.floatToIntBits(getScale()) : 0);
    result = 31 * result + Boolean.valueOf(isPreScaled()).hashCode();
    return result;
  }

  public EmptyIconUIResource asUIResource() {
    return new EmptyIconUIResource(this);
  }

  @Override
  public Icon scale(float scale) {
    if (getScale() == scale) {
      return this;
    }

    if (myScaledCache != null && myScaledCache.getScale() == scale) {
      return myScaledCache;
    }

    myScaledCache = createScaledInstance(scale);
    myScaledCache.setScale(scale);
    return myScaledCache;
  }

  protected EmptyIcon createScaledInstance(float scale) {
    return (scale != 1f) ? this : create(width, height, isPreScaled(), this instanceof UIResource);
  }

  public static class EmptyIconUIResource extends EmptyIcon implements UIResource {
    public EmptyIconUIResource(EmptyIcon icon) {
      super(icon.width, icon.height);
      setPreScaled(icon.isPreScaled());
    }

    private EmptyIconUIResource(int width, int height, boolean useCache) {
      super(width, height, useCache);
    }
  }
}
