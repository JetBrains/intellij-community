// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Use this class to evaluate a computable with default bundle.
 * <p>
 * It can be useful if a language plugin enabled, but some computation should be invoked with default locale, e.g., getting actions' default text/description.
 */
public class DefaultBundleService {
  private static final DefaultBundleService INSTANCE = new DefaultBundleService();
  private static final ThreadLocal<Boolean> ourDefaultBundle = ThreadLocal.withInitial(() -> false);

  @NotNull
  public static DefaultBundleService getInstance() {
    return INSTANCE;
  }

  @NotNull
  public <T> T compute(@NotNull Supplier<T> computable) {
    ourDefaultBundle.set(true);
    try {
      return computable.get();
    }
    finally {
      ourDefaultBundle.set(false);
    }
  }

  public static boolean isDefaultBundle() {
    return ourDefaultBundle.get();
  }
}