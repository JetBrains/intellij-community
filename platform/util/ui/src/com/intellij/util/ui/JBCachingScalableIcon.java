// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.icons.CopyableIcon;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ui.scale.ScaleType.OBJ_SCALE;

/**
 * A {@link JBScalableIcon} providing an immutable caching implementation of the {@link ScalableIcon#scale(float)} method.
 *
 * @author tav
 * @author Aleksey Pivovarov
 */
public abstract class JBCachingScalableIcon<T extends JBCachingScalableIcon> extends JBScalableIcon implements CopyableIcon {
  private T scaledIconCache;

  protected JBCachingScalableIcon() {}

  protected JBCachingScalableIcon(@NotNull JBCachingScalableIcon icon) {
    super(icon);
  }

  /**
   * @return a new scaled copy of this icon, or the cached instance of the provided scale
   */
  @Override
  public @NotNull T scale(float scale) {
    if (scale == getScale()) {
      //noinspection unchecked
      return (T)this;
    }

    if (scaledIconCache == null || scaledIconCache.getScale() != scale) {
      scaledIconCache = copy();
      scaledIconCache.setScale(OBJ_SCALE.of(scale));
    }
    return scaledIconCache;
  }

  protected void clearCachedScaledValue() {
    scaledIconCache = null;
  }

  @Override
  public abstract @NotNull T copy();
}
