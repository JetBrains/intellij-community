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
 *
 * @see ColorIcon
 */
public class EmptyIcon implements Icon, ScalableIcon {
  private static final Map<Integer, Icon> cache = new HashMap<Integer, Icon>();

  public static final Icon ICON_16 = create(16);
  public static final Icon ICON_18 = create(18);
  public static final Icon ICON_8 = create(8);
  public static final Icon ICON_0 = create(0);

  private final int width;
  private final int height;
  private float scale = 1f;

  public static Icon create(int size) {
    Icon icon = cache.get(size);
    if (icon == null && size < 129) {
      cache.put(size, icon = new EmptyIcon(size, size));
    }
    return icon == null ? new EmptyIcon(size, size) : icon;
  }

  public static Icon create(int width, int height) {
    return width == height ? create(width) : new EmptyIcon(width, height);
  }

  public static Icon create(@NotNull Icon base) {
    return create(base.getIconWidth(), base.getIconHeight());
  }

  /**
   * @deprecated use {@linkplain #create(int)} for caching.
   */
  public EmptyIcon(int size) {
    this(size, size);
  }

  public EmptyIcon(int width, int height) {
    this.width = width;
    this.height = height;
  }

  /**
   * @deprecated use {@linkplain #create(javax.swing.Icon)} for caching.
   */
  public EmptyIcon(@NotNull Icon base) {
    this(base.getIconWidth(), base.getIconHeight());
  }

  @Override
  public int getIconWidth() {
    return scale == 1f ? width : (int) (width * scale);
  }

  @Override
  public int getIconHeight() {
    return scale == 1f ? height : (int) (height * scale);
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

    return true;
  }

  @Override
  public int hashCode() {
    int result = width;
    result = 31 * result + height;
    result = 31 * result + (scale != +0.0f ? Float.floatToIntBits(scale) : 0);
    return result;
  }

  public EmptyIconUIResource asUIResource() {
    return new EmptyIconUIResource(this);
  }

  @Override
  public Icon scale(float scaleFactor) {
    if (scaleFactor != scale) {
      EmptyIcon icon;
      if (scale != 1f) {
        icon = this;
      } else {
        icon = this instanceof UIResource ? new EmptyIconUIResource(width, height) : new EmptyIcon(width, height);
      }
      icon.scale = scaleFactor;
      return icon;
    }
    return this;
  }

  public static class EmptyIconUIResource extends EmptyIcon implements UIResource {
    public EmptyIconUIResource(EmptyIcon icon) {
      super(icon.width, icon.height);
    }

    private EmptyIconUIResource(int width, int height) {
      super(width, height);
    }
  }
}
