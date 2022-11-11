// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Common simple predicates to deduplicate lambdas/method references
 */
public class Predicates {
  @Contract(pure = true)
  public static <T> @NotNull Predicate<T> alwaysTrue() {
    return x -> true;
  }

  @Contract(pure = true)
  public static <T> @NotNull Predicate<T> alwaysFalse() {
    return x -> false;
  }

  @Contract(pure = true)
  public static <T> @NotNull Predicate<T> nonNull() {
    return Objects::nonNull;
  }
}
