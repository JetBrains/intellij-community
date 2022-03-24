// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleContextSupport;
import com.intellij.ui.scale.UserScaleContext;
import com.intellij.util.SVGLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;

@SuppressWarnings("UnnecessaryFullyQualifiedName")
@ApiStatus.Internal
public abstract class LazyImageIcon extends ScaleContextSupport
  implements CopyableIcon, ScalableIcon, DarkIconProvider, MenuBarIconProvider {
  protected final Object myLock = new Object();

  @Nullable
  protected volatile Object myRealIcon;

  protected LazyImageIcon() {
    // For instance, ShadowPainter updates the context from outside.
    getScaleContext().addUpdateListener(new UserScaleContext.UpdateListener() {
      @Override
      public void contextUpdated() {
        myRealIcon = null;
      }
    });
  }

  @Nullable
  protected static ImageIcon unwrapIcon(Object realIcon) {
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
    ScaleContext ctx = ScaleContext.create(g2d);
    if (SVGLoader.isColorRedefinitionContext()) {
      getRealIconForSelection(ctx).paintIcon(c, g, x, y);
    } else {
      getRealIcon(ctx).paintIcon(c, g, x, y);
    }
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
  public final @NotNull ImageIcon getRealIcon() {
    return getRealIcon(null);
  }

  protected abstract @NotNull ImageIcon getRealIcon(@Nullable ScaleContext context);

  protected ImageIcon getRealIconForSelection(@Nullable ScaleContext context) {
    return getRealIcon(context);
  }
}
