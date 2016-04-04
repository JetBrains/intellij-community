/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

public class NotNullCachedComputableWrapper<T> implements NotNullComputable<T> {

  private static final RecursionGuard ourGuard = RecursionManager.createGuard(NotNullCachedComputableWrapper.class.getName());

  private NotNullComputable<T> myComputable;
  private volatile T myValue;

  public NotNullCachedComputableWrapper(@NotNull NotNullComputable<T> computable) {
    myComputable = computable;
  }

  @NotNull
  @Override
  public T compute() {
    T result = myValue;
    if (result != null) return result;

    //noinspection SynchronizeOnThis
    synchronized (this) {
      result = myValue;
      if (result == null) {
        final RecursionGuard.StackStamp stamp = ourGuard.markStack();
        result = myComputable.compute();
        if (stamp.mayCacheNow()) {
          myValue = result;
          myComputable = null;  // allow gc to clean this up
        }
      }
    }

    return result;
  }

  @TestOnly
  public boolean isComputed() {
    return myValue != null;
  }
}
