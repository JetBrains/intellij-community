// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClassLoaderUtil {
  private ClassLoaderUtil() {
  }

  public static <E extends Throwable> void runWithClassLoader(@Nullable ClassLoader classLoader,
                                                              @NotNull ThrowableRunnable<E> runnable)
    throws E {
    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      runnable.run();
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  public static <T, E extends Throwable> T computeWithClassLoader(@Nullable ClassLoader classLoader,
                                                                  @NotNull ThrowableComputable<T, E> computable) throws E {
    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      return computable.compute();
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  /** @deprecated Use {@link ClassLoaderUtil#computeWithClassLoader(ClassLoader, ThrowableComputable)} instead. */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static <T> T runWithClassLoader(ClassLoader classLoader, Computable<T> computable) {
    return computeWithClassLoader(classLoader, () -> computable.compute());
  }
}
