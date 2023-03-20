// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.images;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

final class DevKitImagesBundle {

  private static final @NonNls String BUNDLE_FQN = "messages.DevKitImagesBundle";
  private static final DynamicBundle BUNDLE = new DynamicBundle(DevKitImagesBundle.class, BUNDLE_FQN);

  private DevKitImagesBundle() {
  }

  public static @Nls @NotNull String message(
    @PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
    @Nullable Object @NotNull ... params
  ) {
    return BUNDLE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls @NotNull String> messagePointer(
    @PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
    @Nullable Object @NotNull ... params
  ) {
    return BUNDLE.getLazyMessage(key, params);
  }
}
