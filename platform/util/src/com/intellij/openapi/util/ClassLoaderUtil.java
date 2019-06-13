/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.util.ThrowableRunnable;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassLoaderUtil {
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
    if (SystemInfo.IS_AT_LEAST_JAVA9) {
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
