// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;

import java.lang.ref.SoftReference;

@ApiStatus.Internal
public final class ThreadLocalCachedByteArray {
  private final ThreadLocal<SoftReference<byte[]>> myThreadLocal = new ThreadLocal<>();

  public byte[] getBuffer(int size) {
    byte[] value = com.intellij.reference.SoftReference.dereference(myThreadLocal.get());
    if (value == null || value.length < size) {
      value = ArrayUtil.newByteArray(size);
      myThreadLocal.set(new SoftReference<>(value));
    }

    return value;
  }
}