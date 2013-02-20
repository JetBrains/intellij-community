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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.RandomAccess;

class SubList<E> extends AbstractList<E> implements RandomAccess {
  private final E[] a;
  private final int start;
  private final int end;

  SubList(@NotNull E[] array, int start, int end) {
    a = array;
    this.start = start;
    this.end = end;
    assert start <= a.length;
    assert end <= a.length;
    assert start <= end && start >= 0;
  }

  @Override
  public int size() {
    return end - start;
  }

  @NotNull
  @Override
  public Object[] toArray() {
    return Arrays.copyOfRange(a, start, end);
  }

  @NotNull
  @Override
  @SuppressWarnings("unchecked")
  public <T> T[] toArray(@NotNull T[] a) {
    int size = size();
    if (a.length < size) {
      return Arrays.copyOfRange(this.a, start, end, (Class<? extends T[]>)a.getClass());
    }
    System.arraycopy(this.a, start, a, 0, size);
    if (a.length > size) {
      a[size] = null;
    }
    return a;
  }

  @Override
  public E get(int index) {
    return a[index+start];
  }

  @Override
  public int indexOf(Object o) {
    return ArrayUtil.indexOf(a, o, start, end);
  }

  @Override
  public boolean contains(Object o) {
    return indexOf(o) != -1;
  }
}
