// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.jps;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.jps.api.JpsDynamicBundle;

import java.util.function.Supplier;

public final class CompilationChartsJpsBundle {
  private static final @NonNls String BUNDLE = "messages.CompilationChartsJpsBundle";
  private static final JpsDynamicBundle INSTANCE = new JpsDynamicBundle(CompilationChartsJpsBundle.class, BUNDLE);

  private CompilationChartsJpsBundle() {
  }

  public static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public
  static @NotNull Supplier<@Nls @NotNull String> messagePointer(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key,
                                                                Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
