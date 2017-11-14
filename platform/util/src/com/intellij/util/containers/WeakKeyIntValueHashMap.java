// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.reference.SoftReference;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Set;

class WeakKeyIntValueHashMap<K> implements ObjectIntMap<K> {
  private final TObjectIntHashMap<MyReference<K>> myMap = new TObjectIntHashMap<MyReference<K>>();
  private final ReferenceQueue<K> myQueue = new ReferenceQueue<K>();

  private static class MyReference<T> extends WeakReference<T> {
    private final int myHashCode;

    private MyReference(@NotNull T key, ReferenceQueue<? super T> q) {
      super(key, q);
      myHashCode = key.hashCode();
    }

    // when key is GC-ed, equality should be identity-based
    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MyReference)) return false;
      MyReference<T> other = (MyReference)obj;
      T myKey = get();
      T otherKey = other.get();
      return obj == this || myKey != null && otherKey != null && myKey.equals(otherKey);
    }

    @Override
    public int hashCode() {
      return myHashCode;
    }
  }

  private void processQueue() {
    while(true){
      MyReference<K> ref = (MyReference)myQueue.poll();
      if (ref == null) {
        return;
      }
      myMap.remove(ref);
    }
  }

  @Override
  public final int get(@NotNull K key) {
    MyReference<K> ref = new MyReference<K>(key, null);
    return myMap.get(ref);
  }

  @Override
  public final int put(@NotNull K key, int value) {
    processQueue();
    MyReference<K> ref = new MyReference<K>(key, myQueue);
    return myMap.put(ref, value);
  }

  @Override
  public final int remove(@NotNull K key) {
    processQueue();
    MyReference<K> ref = new MyReference<K>(key, myQueue);
    return myMap.remove(ref);
  }

  @Override
  public final void clear() {
    myMap.clear();
    processQueue();
  }

  @Override
  public final int size() {
    return myMap.size();
  }

  @Override
  public final boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public final boolean containsKey(@NotNull K key) {
    MyReference<K> ref = new MyReference<K>(key, null);
    return myMap.containsKey(ref);
  }

  @Override
  @NotNull
  public final int[] values() {
    throw new IncorrectOperationException("values() makes no sense for weak/soft key map because GC can clear the key any moment now");
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    return new THashSet<K>(ContainerUtil.map(myMap.keys(), new Function<Object, K>() {
      @Override
      public K fun(Object ref) {
        return SoftReference.dereference((MyReference<K>)ref);
      }
    }));
  }

  @Override
  public boolean containsValue(int value) {
    throw RefValueHashMap.pointlessContainsValue();
  }

  private static final Object GCED = new Object();
  @NotNull
  @Override
  public Iterable<Entry<K>> entries() {
    return new Iterable<Entry<K>>() {
      @NotNull
      @Override
      public Iterator<Entry<K>> iterator() {
        final TObjectIntIterator<MyReference<K>> tIterator = myMap.iterator();
        return ContainerUtil.filterIterator(new Iterator<Entry<K>>() {
          @Override
          public boolean hasNext() {
            return tIterator.hasNext();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

          @Override
          public Entry<K> next() {
            tIterator.advance();
            return new Entry<K>() {
              @NotNull
              @Override
              public K getKey() {
                K v = SoftReference.dereference(tIterator.key());
                //noinspection unchecked
                return ObjectUtils.notNull(v, (K)GCED);
              }

              @Override
              public int getValue() {
                return tIterator.value();
              }
            };
          }
        }, new Condition<Entry<K>>() {
          @Override
          public boolean value(Entry<K> o) {
            return o.getKey() != GCED;
          }
        });
      }
    };
  }
}
