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
import com.intellij.openapi.util.ScalableIcon;
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
public class EmptyIcon extends JBUI.JBAbstractIcon implements Icon, ScalableIcon {
  private static final Map<Pair<Integer, Float>, EmptyIcon> cache =
    new HashMap<Pair<Integer, Float>, EmptyIcon>(); // (size, jbuiScale) -> (icon)

  public static final Icon ICON_16 = JBUI.scale(create(16));
  public static final Icon ICON_18 = JBUI.scale(create(18));
  public static final Icon ICON_8 = JBUI.scale(create(8));
  public static final Icon ICON_0 = JBUI.scale(create(0));

  protected final int width;
  protected final int height;
  protected float scale = 1f;
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
    return create(width, height, 1f, false);
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
  public EmptyIcon withJBUIScale(float jbuiScale) {
    if (myUseCache && getJBUIScale() != jbuiScale) {
      Pair<Integer, Float> key = key(width, height, getJBUIScale());
      if (key != null) cache.remove(key); // rather useless to keep it in cache
      return create(width, height, jbuiScale, false);
    }
    return (EmptyIcon)super.withJBUIScale(jbuiScale);
  }

  private static EmptyIcon create(int width, int height, float jbuiScale, boolean asUIResource) {
    Pair<Integer, Float> key = key(width, height, jbuiScale);
    EmptyIcon icon = (key != null) ? cache.get(key) : null;
    if (icon == null) {
      icon = asUIResource ? new EmptyIconUIResource(width, height, true) : new EmptyIcon(width, height, true);
      icon.setJBUIScale(jbuiScale);
      if (key != null) cache.put(key, icon);
    }
    return icon;
  }

  private static Pair<Integer, Float> key(int width, int height, float jbuiScale) {
    return (width == height && width < 129) ? Pair.create(width, jbuiScale) : null;
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

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EmptyIcon)) return false;

    final EmptyIcon icon = (EmptyIcon)o;

    if (height != icon.height) return false;
    if (width != icon.width) return false;
    if (scale != icon.scale) return false;
    if (getJBUIScale() != icon.getJBUIScale()) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = width;
    result = 31 * result + height;
    result = 31 * result + (scale != +0.0f ? Float.floatToIntBits(scale) : 0);
    result = 31 * result + (getJBUIScale() != +0.0f ? Float.floatToIntBits(getJBUIScale()) : 0);
    return result;
  }

  public EmptyIconUIResource asUIResource() {
    return new EmptyIconUIResource(this);
  }

  @Override
  public int scaleVal(int n) {
    return super.scaleVal(scale == 1f ? n : (int) (n * scale));
  }

  @Override
  public Icon scale(float scaleFactor) {
    if (scale == scaleFactor) {
      return this;
    }

    if (myScaledCache != null && myScaledCache.scale == scaleFactor) {
      return myScaledCache;
    }

    myScaledCache = createScaledInstance(scaleFactor);
    myScaledCache.scale = scaleFactor;
    return myScaledCache;
  }

  protected EmptyIcon createScaledInstance(float scale) {
    return (scale != 1f) ? this : create(width, height, getJBUIScale(), this instanceof UIResource);
  }

  public static class EmptyIconUIResource extends EmptyIcon implements UIResource {
    public EmptyIconUIResource(EmptyIcon icon) {
      super(icon.width, icon.height);
      setJBUIScale(icon.getJBUIScale());
    }

    private EmptyIconUIResource(int width, int height, boolean useCache) {
      super(width, height, useCache);
    }
  }
}
