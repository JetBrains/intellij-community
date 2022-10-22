// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

public final class DevPsiViewerBundle {

  private static final @NonNls String BUNDLE_FQN = "messages.DevPsiViewerBundle";
  private static final DynamicBundle BUNDLE = new DynamicBundle(DevPsiViewerBundle.class, BUNDLE_FQN);

  private DevPsiViewerBundle() {
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
