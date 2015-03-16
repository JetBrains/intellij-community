/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class VolatileNullableLazyValue<T> extends NullableLazyValue<T> {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("VolatileNullableLazyValue");
  private volatile boolean myComputed;
  @Nullable private volatile T myValue;

  @Override
  @Nullable
  protected abstract T compute();

  @Nullable
  public T getValue() {
    T value = myValue;
    if (!myComputed) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      value = compute();
      if (stamp.mayCacheNow()) {
        myValue = value;
        myComputed = true;
      }
    }
    return value;
  }
}
