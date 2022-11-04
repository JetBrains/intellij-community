// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.ui.IconReplacer;
import com.intellij.ui.UpdatableIcon;
import com.intellij.ui.icons.ReplaceableIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.RGBImageFilter;
import java.util.function.Supplier;

class FilteredIcon implements ReplaceableIcon {
  private long modificationCount = -1;

  private @NotNull final Icon baseIcon;
  private @NotNull final Supplier<? extends RGBImageFilter> filterSupplier;

  private @Nullable Icon iconToPaint;

  FilteredIcon(@NotNull Icon icon, @NotNull Supplier<? extends RGBImageFilter> filterSupplier) {
    baseIcon = icon;
    this.filterSupplier = filterSupplier;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Icon toPaint = iconToPaint;
    if (toPaint == null || modificationCount != -1) {
      long currentModificationCount = calculateModificationCount();
      if (currentModificationCount != -1 && currentModificationCount != modificationCount) {
        modificationCount = currentModificationCount;
        toPaint = null;
      }
    }
    if (toPaint == null) { // try to postpone rendering until it is really needed
      toPaint = IconLoader.renderFilteredIcon(baseIcon, filterSupplier, c);
      iconToPaint = toPaint;
    }
    if (c != null) {
      new PaintNotifier(c, x, y).replaceIcon(baseIcon);
    }
    toPaint.paintIcon(c != null ? c : IconLoader.fakeComponent, g, x, y);
  }

  @Override
  public @NotNull Icon replaceBy(@NotNull IconReplacer replacer) {
    return new FilteredIcon(replacer.replaceIcon(baseIcon), filterSupplier);
  }

  private long calculateModificationCount() {
    TimestampSearcher searcher = new TimestampSearcher();
    searcher.replaceIcon(baseIcon);
    return searcher.modificationCount;
  }

  @Override
  public int getIconWidth() {
    return baseIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return baseIcon.getIconHeight();
  }

  // This replacer play visitor role
  private static class TimestampSearcher implements IconReplacer {
    long modificationCount;

    @Override
    public Icon replaceIcon(Icon icon) {
      if (icon instanceof ModificationTracker withModificationCount) {
        if (modificationCount == -1) {
          modificationCount = withModificationCount.getModificationCount();
        }
        else {
          modificationCount += withModificationCount.getModificationCount();
        }
      }
      if (icon instanceof ReplaceableIcon replaceableIcon) {
        replaceableIcon.replaceBy(this);
      }
      return IconReplacer.super.replaceIcon(icon);
    }
  }

  // This replacer play visitor role
  private static class PaintNotifier implements IconReplacer {
    final @NotNull Component c;
    final int x;
    final int y;

    private PaintNotifier(@NotNull Component c, int x, int y) {
      this.c = c;
      this.x = x;
      this.y = y;
    }

    @Override
    public Icon replaceIcon(Icon icon) {
      if (icon instanceof UpdatableIcon updatableIcon) {
        updatableIcon.notifyPaint(c, x, y);
      }
      if (icon instanceof ReplaceableIcon replaceableIcon) {
        replaceableIcon.replaceBy(this);
      }
      return IconReplacer.super.replaceIcon(icon);
    }
  }
}
