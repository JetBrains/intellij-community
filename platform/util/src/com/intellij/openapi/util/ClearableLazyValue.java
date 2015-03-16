/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * Lazy value with ability to reset (and recompute) the value.
 * Thread-safe version: {@link AtomicClearableLazyValue}.
 */
public abstract class ClearableLazyValue<T> {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("ClearableLazyValue");
  private T myValue;

  @NotNull
  protected abstract T compute();

  @NotNull
  public T getValue() {
    T result = myValue;
    if (result == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      result = compute();
      if (stamp.mayCacheNow()) {
        myValue = result;
      }
    }
    return result;
  }

  public void drop() {
    myValue = null;
  }
}
