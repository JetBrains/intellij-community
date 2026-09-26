// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

final class DevKitGradleBundle {

  public static final @NonNls String BUNDLE = "messages.DevKitGradleBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(DevKitGradleBundle.class, BUNDLE);

  private DevKitGradleBundle() {
  }

  public static @Nls @NotNull String message(
    @PropertyKey(resourceBundle = BUNDLE) @NotNull String key,
    @Nullable Object @NotNull ... params
  ) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls @NotNull String> messagePointer(
    @PropertyKey(resourceBundle = BUNDLE) @NotNull String key,
    @Nullable Object @NotNull ... params
  ) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
