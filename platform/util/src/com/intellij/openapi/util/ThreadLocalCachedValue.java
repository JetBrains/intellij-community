// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;

@ApiStatus.Internal
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

  protected abstract @NotNull T create();
}