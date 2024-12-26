// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dev;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

public final class JavaDevBundle {
  private static final @NonNls String BUNDLE_FQN = "messages.JavaDevBundle";
  private static final DynamicBundle BUNDLE = new DynamicBundle(JavaDevBundle.class, BUNDLE_FQN);

  private JavaDevBundle() {
  }

  public static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
                                             @Nullable Object @NotNull ... params) {
    return BUNDLE.getMessage(key, params);
  }
}
