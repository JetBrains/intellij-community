// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

@ApiStatus.Internal
public final class BootstrapBundle {
  private static final String BUNDLE = "messages.BootstrapBundle";

  private static final @Nullable DynamicBundle INSTANCE;

  static {
    DynamicBundle instance = null;
    try {
      instance = new DynamicBundle(BootstrapBundle.class, BUNDLE);
    }
    catch (Throwable ignored) { }
    INSTANCE = instance;
  }

  private BootstrapBundle() { }

  // used for reporting startup errors, hence must not produce any exceptions
  public static @Nls @NotNull String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE != null) {
      try {
        return INSTANCE.getMessage(key, params);
      }
      catch (Throwable ignored) { }
    }

    var sb = new StringBuilder();
    sb.append('!').append(key).append('!');
    for (Object param : params) {
      sb.append(param).append('!');
    }
    return sb.toString();  // NON-NLS (fallback)
  }
}
