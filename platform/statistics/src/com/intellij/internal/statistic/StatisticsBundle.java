// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic;

import com.intellij.DynamicBundle;
import com.intellij.ide.IdeCoreBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class StatisticsBundle {
  public static final String PATH_TO_BUNDLE = "messages.StatisticsBundle";
  private static final DynamicBundle bundle = new DynamicBundle(IdeCoreBundle.class, PATH_TO_BUNDLE);

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return bundle.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return bundle.getLazyMessage(key, params);
  }
}
