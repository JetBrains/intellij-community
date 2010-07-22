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

public class ClassLoaderUtil {
  private ClassLoaderUtil() {
  }

  public static void runWithClassLoader(final ClassLoader classLoader, final Runnable runnable) {
    final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      runnable.run();
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  public static <T> T runWithClassLoader(final ClassLoader classLoader, final Computable<T> computable) {
    final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      return computable.compute();
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  public static <E extends Throwable> void runWithClassLoader(final ClassLoader classLoader, final ThrowableRunnable<E> runnable)
    throws E {
    final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      runnable.run();
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  public static <T, E extends Throwable> T runWithClassLoader(final ClassLoader classLoader, final ThrowableComputable<T, E> computable)
    throws E {
    final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(classLoader);
      return computable.compute();
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }
}
