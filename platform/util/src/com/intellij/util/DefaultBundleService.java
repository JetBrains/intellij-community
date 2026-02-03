// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * <p>Use this class to evaluate a computable with default bundle.</p>
 *
 * <p>It can be useful if a language plugin is enabled, but some computation should be invoked with a default locale
 * (e.g. getting actions' default text/description).</p>
 */
public final class DefaultBundleService {
  private static final DefaultBundleService INSTANCE = new DefaultBundleService();
  private static final ThreadLocal<Boolean> ourDefaultBundle = ThreadLocal.withInitial(() -> false);

  public static @NotNull DefaultBundleService getInstance() {
    return INSTANCE;
  }

  public <T> @Nullable T compute(@NotNull Supplier<? extends T> computable) {
    final boolean isDefault = isDefaultBundle();
    if (!isDefault) {
      ourDefaultBundle.set(true);
    }
    try {
      return computable.get();
    }
    finally {
      if (!isDefault) {
        ourDefaultBundle.set(false);
      }
    }
  }

  public static boolean isDefaultBundle() {
    return ourDefaultBundle.get();
  }
}
