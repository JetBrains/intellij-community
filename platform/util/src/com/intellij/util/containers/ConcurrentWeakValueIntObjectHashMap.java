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

package com.intellij.util.containers;


import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public class ConcurrentWeakValueIntObjectHashMap<V> extends ConcurrentRefValueIntObjectHashMap<V> {
  private static class MyRef<V> extends WeakReference<V> implements IntReference<V> {
    private final int hash;
    private final int key;

    MyRef(int key, @NotNull V referent, ReferenceQueue<V> queue) {
      super(referent, queue);
      this.key = key;
      hash = referent.hashCode();
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      V v = get();
      return obj instanceof MyRef && ((MyRef)obj).hash == hash && v != null && v.equals(((MyRef)obj).get());
    }

    @Override
    public int getKey() {
      return key;
    }
  }

  @Override
  protected IntReference<V> createReference(int key, @NotNull V value, ReferenceQueue<V> queue) {
    return new MyRef<V>(key, value, queue);
  }
}
