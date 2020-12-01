// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import com.intellij.ui.scale.ScaleContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.ImageFilter;
import java.net.URL;
import java.util.List;

@ApiStatus.Internal
public interface ImageDataLoader {
  @Nullable Image loadImage(@NotNull List<? extends ImageFilter> filters, @NotNull ScaleContext scaleContext, boolean isDark);

  @Nullable URL getURL();

  @Nullable ImageDataLoader patch(@NotNull String originalPath, @NotNull IconTransform transform);

  boolean isMyClassLoader(@NotNull ClassLoader classLoader);
}
