// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ImageDescriptor {
  public final boolean isDark;
  public final @NotNull String path;
  public final double scale; // initial scale factor
  public final boolean isSvg;
  public final boolean original; // path is not altered

  // The original user space size of the image. In case of SVG it's the size specified in the SVG doc.
  // Otherwise it's the size of the original image divided by the image's scale (defined by the extension @2x).
  public final @NotNull ImageLoader.Dimension2DDouble originalUserSize;

  public ImageDescriptor(@NotNull String path, double scale, boolean isSvg, boolean isDark) {
    this(path, scale, isSvg, isDark, false);
  }

  public ImageDescriptor(@NotNull String path, double scale, boolean isSvg, boolean isDark, boolean original) {
    assert !path.isEmpty();

    this.path = path;
    this.scale = scale;
    this.isSvg = isSvg;
    this.original = original;
    this.originalUserSize = new ImageLoader.Dimension2DDouble(0, 0);
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
