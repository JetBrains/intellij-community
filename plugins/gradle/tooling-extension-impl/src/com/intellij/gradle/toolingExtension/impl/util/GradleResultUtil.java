// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class GradleResultUtil {

  /**
   * Retries to execute given supplier.
   * If fails second time, throw exception from first failure.
   */
  public static <R> R runOrRetryOnce(@NotNull Supplier<R> supplier) {
    try {
      return supplier.get();
    }
    catch (Exception first) {
      try {
        return supplier.get();
      }
      catch (Exception second) {
        throw first;
      }
    }
  }

  /**
   * Retries to execute given runnable.
   * If fails second time, throw exception from first failure.
   */
  public static void runOrRetryOnce(@NotNull Runnable runnable) {
    runOrRetryOnce(() -> {
      runnable.run();
      return null;
    });
  }
}
