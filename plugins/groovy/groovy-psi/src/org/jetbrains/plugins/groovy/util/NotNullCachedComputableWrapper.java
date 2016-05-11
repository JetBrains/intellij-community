/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.atomic.AtomicReference;

public class NotNullCachedComputableWrapper<T> implements NotNullComputable<T> {

  private static final RecursionGuard ourGuard = RecursionManager.createGuard(NotNullCachedComputableWrapper.class.getName());

  private volatile NotNullComputable<T> myComputable;
  private final AtomicReference<T> myValueRef = new AtomicReference<>();

  public NotNullCachedComputableWrapper(@NotNull NotNullComputable<T> computable) {
    myComputable = computable;
  }

  @NotNull
  @Override
  public T compute() {
    while (true) {
      T value = myValueRef.get();
      if (value != null) return value;                  // value already computed and cached

      final NotNullComputable<T> computable = myComputable;
      if (computable == null) continue;                 // computable is null only after some thread succeeds CAS

      RecursionGuard.StackStamp stamp = ourGuard.markStack();
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
