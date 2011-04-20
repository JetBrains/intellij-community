/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util;

import org.jetbrains.annotations.Nullable;

public abstract class VolatileDoubleCheckedLockedInit<T> {
  private volatile boolean myInitialized;
  private T myInstance;
  private final Object myLock;

  public VolatileDoubleCheckedLockedInit() {
    myLock = new Object();
  }

  @Nullable
  protected abstract T createT();

  @Nullable
  public T get() {
    if (! myInitialized) {
      synchronized (myLock) {
        if (! myInitialized) {
          myInstance = createT();
          myInitialized = true;
        }
      }
    }
    return myInstance;
  }

  public void reset() {
    myInitialized = false;
  }
}
