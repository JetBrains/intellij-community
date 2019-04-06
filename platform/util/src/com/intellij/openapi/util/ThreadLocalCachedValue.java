/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.lang.ref.SoftReference;

public abstract class ThreadLocalCachedValue<T> {
  private final ThreadLocal<SoftReference<T>> myThreadLocal = new ThreadLocal<>();

  public T getValue() {
    T value = com.intellij.reference.SoftReference.dereference(myThreadLocal.get());
    if (value == null) {
      value = create();
      myThreadLocal.set(new SoftReference<>(value));
    }
    else {
      init(value);
    }
    return value;
  }

  protected void init(@NotNull T value) {
  }

  @NotNull
  protected abstract T create();
}