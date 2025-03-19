// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.clouds;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

@Internal
public final class CloudsBundle {

  private static final @NonNls String BUNDLE_FQN = "messages.CloudsBundle";
  private static final DynamicBundle BUNDLE = new DynamicBundle(CloudsBundle.class, BUNDLE_FQN);

  private CloudsBundle() {
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
