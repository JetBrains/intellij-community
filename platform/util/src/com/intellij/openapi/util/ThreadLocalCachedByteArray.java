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

import com.intellij.util.ArrayUtil;

import java.lang.ref.SoftReference;

public final class ThreadLocalCachedByteArray {
  private final ThreadLocal<SoftReference<byte[]>> myThreadLocal = new ThreadLocal<>();

  public byte[] getBuffer(int size) {
    byte[] value = com.intellij.reference.SoftReference.dereference(myThreadLocal.get());
    if (value == null || value.length <= size) {
      value = ArrayUtil.newByteArray(size);
      myThreadLocal.set(new SoftReference<>(value));
    }

    return value;
  }
}