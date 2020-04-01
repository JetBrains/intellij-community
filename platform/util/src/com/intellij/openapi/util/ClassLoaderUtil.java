// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.ThrowableRunnable;
import com.intellij.util.lang.UrlClassLoader;
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
  public static <T> T runWithClassLoader(ClassLoader classLoader, Computable<T> computable) {
    return computeWithClassLoader(classLoader, () -> computable.compute());
  }

  @Nullable
  public static ClassLoader getPlatformLoaderParentIfOnJdk9() {
    if (SystemInfoRt.IS_AT_LEAST_JAVA9) {
      // on Java 8, 'tools.jar' is on a classpath; on Java 9, its classes are available via the platform loader
      try {
        //noinspection JavaReflectionMemberAccess
        return (ClassLoader)ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  public static void addPlatformLoaderParentIfOnJdk9(@NotNull UrlClassLoader.Builder builder) {
    builder.parent(getPlatformLoaderParentIfOnJdk9());
  }
}
