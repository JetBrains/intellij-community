// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.icons;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URL;

@ApiStatus.Internal
public interface ImageDataLoader {
  @Nullable Image loadImage(@NotNull LoadIconParameters parameters);

  @Nullable URL getURL();

  @Nullable ImageDataLoader patch(@NotNull String originalPath, @NotNull IconTransform transform);

  boolean isMyClassLoader(@NotNull ClassLoader classLoader);

  default int getFlags() {
    return 0;
  }
}
