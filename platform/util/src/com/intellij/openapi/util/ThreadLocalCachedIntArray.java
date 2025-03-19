// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;

import java.lang.ref.SoftReference;

@ApiStatus.Internal
public final class ThreadLocalCachedIntArray {
  private final ThreadLocal<SoftReference<int[]>> myThreadLocal = new ThreadLocal<>();

  public int[] getBuffer(int size) {
    int[] value = com.intellij.reference.SoftReference.dereference(myThreadLocal.get());
    if (value == null || value.length < size) {
      value = new int[size];
      myThreadLocal.set(new SoftReference<>(value));
    }

    return value;
  }
}