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

import org.jetbrains.annotations.NotNull;

/**
 * NOTE: Assumes that values computed by different threads are equal and interchangeable
 * and readers should be ready to get different instances on different invocations of the {@link #getValue()}
 *
 * @author peter
 */
public abstract class VolatileNotNullLazyValue<T> extends NotNullLazyValue<T> {
  private volatile T myValue;

  @Override
  @NotNull
  public final T getValue() {
    T value = myValue;
    if (value == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      value = compute();
      if (stamp.mayCacheNow()) {
        myValue = value;
      }
    }
    return value;
  }

  @Override
  public boolean isComputed() {
    return myValue != null;
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @NotNull
  public static <T> VolatileNotNullLazyValue<T> createValue(@NotNull final NotNullFactory<? extends T> value) {
    return new VolatileNotNullLazyValue<T>() {
      @NotNull
      @Override
      protected T compute() {
        return value.create();
      }
    };
  }

}