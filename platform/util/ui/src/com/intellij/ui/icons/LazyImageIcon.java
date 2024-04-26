// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleContextSupport;
import com.intellij.ui.scale.UserScaleContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;

@ApiStatus.Internal
public abstract class LazyImageIcon extends ScaleContextSupport
  implements CopyableIcon, ScalableIcon, DarkIconProvider, MenuBarIconProvider {
  protected final Object lock = new Object();

  protected volatile @Nullable Object realIcon;

  protected LazyImageIcon() {
    // for instance, ShadowPainter updates the context from an outside
    getScaleContext().addUpdateListener(new UserScaleContext.UpdateListener() {
      @Override
      public void contextUpdated() {
        realIcon = null;
      }
    });
  }

  protected static @Nullable ImageIcon unwrapIcon(Object realIcon) {
    Object icon = realIcon;
    if (icon instanceof Reference) {
      //noinspection unchecked
      icon = ((Reference<ImageIcon>)icon).get();
    }
    return icon instanceof ImageIcon ? (ImageIcon)icon : null;
  }

  @Override
  public final void paintIcon(Component c, Graphics g, int x, int y) {
    Graphics2D g2d = g instanceof Graphics2D ? (Graphics2D)g : null;
    getRealIcon(ScaleContext.create(g2d)).paintIcon(c, g, x, y);
  }

  @Override
  public int getIconWidth() {
    return getRealIcon().getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return getRealIcon().getIconHeight();
  }

  @Override
  public float getScale() {
    return 1f;
  }

  @ApiStatus.Internal
  public final @NotNull Icon getRealIcon() {
    return getRealIcon(null);
  }

  protected abstract @NotNull Icon getRealIcon(@Nullable ScaleContext context);
}
