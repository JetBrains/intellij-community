// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ImageDescriptor {
  public static final int HAS_2x = 1;
  public static final int HAS_DARK = 2;
  public static final int HAS_DARK_2x = 4;

  public final boolean isDark;
  public final @NotNull String path;
  public final float scale; // initial scale factor
  public final boolean isSvg;

  public ImageDescriptor(@NotNull String path, float scale, boolean isSvg, boolean isDark) {
    assert !path.isEmpty();

    this.path = path;
    this.scale = scale;
    this.isSvg = isSvg;
    this.isDark = isDark;
  }

  public @NotNull String getPath() {
    return path;
  }

  @Override
  public String toString() {
    return path + ", scale: " + scale + ", isSvg: " + isSvg;
  }
}
