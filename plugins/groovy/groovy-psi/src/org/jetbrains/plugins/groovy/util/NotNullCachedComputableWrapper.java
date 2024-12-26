// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.atomic.AtomicReference;

public class NotNullCachedComputableWrapper<T> implements NotNullComputable<T> {
  private volatile NotNullComputable<T> myComputable;
  private final AtomicReference<T> myValueRef = new AtomicReference<>();

  public NotNullCachedComputableWrapper(@NotNull NotNullComputable<T> computable) {
    myComputable = computable;
  }

  @Override
  public @NotNull T compute() {
    while (true) {
      T value = myValueRef.get();
      if (value != null) return value;                  // value already computed and cached

      final NotNullComputable<T> computable = myComputable;
      if (computable == null) continue;                 // computable is null only after some thread succeeds CAS

      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      value = computable.compute();
      if (stamp.mayCacheNow()) {
        if (myValueRef.compareAndSet(null, value)) {    // try to cache value
          myComputable = null;                          // if ok, allow gc to clean computable
          return value;
        }
        // if CAS failed then other thread already set this value => loop & get value from reference
      }
      else {
        return value;                                   // recursion detected
      }
    }
  }

  @TestOnly
  public boolean isComputed() {
    return myValueRef.get() != null;
  }
}
