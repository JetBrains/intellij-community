// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

public final class ContainerUtil {
  private ContainerUtil() {
  }

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> unmodifiableOrEmptyMap(@NotNull Map<? extends K, ? extends V> original) {
    int size = original.size();
    if (size == 0) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(original);
  }
}
