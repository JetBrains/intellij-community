// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GradleObjectUtil {

  @Contract(value = "!null, _ -> param1; null, _ -> param2", pure = true)
  public static @NotNull <T> T notNull(@Nullable T value, @NotNull T defaultValue) {
    return value == null ? defaultValue : value;
  }

  @Contract(value = "!null, _, _ -> param1; null, !null, _ -> param2; null, null, _ -> param3;", pure = true)
  public static @NotNull <T> T notNull(@Nullable T value1, @Nullable T value2, @NotNull T defaultValue) {
    return notNull(value1, notNull(value2, defaultValue));
  }
}
