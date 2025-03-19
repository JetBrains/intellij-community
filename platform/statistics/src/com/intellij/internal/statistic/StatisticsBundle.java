// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class StatisticsBundle extends DynamicBundle {

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.getLazyMessage(key, params);
  }

  public static final String PATH_TO_BUNDLE = "messages.StatisticsBundle";
  private static final StatisticsBundle BUNDLE = new StatisticsBundle();

  public StatisticsBundle() {
    super(PATH_TO_BUNDLE);
  }
}
