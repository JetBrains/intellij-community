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
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

class IntKeyWeakValueHashMap<V> implements IntObjectMap<V> {
  private final TIntObjectHashMap<MyReference<V>> myMap = new TIntObjectHashMap<MyReference<V>>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  private static class MyReference<T> extends WeakReference<T> {
    private final int key;
    String name;

    private MyReference(int key, @NotNull T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      this.key = key;
    }
  }

  private void processQueue() {
    while(true){
      MyReference ref = (MyReference)myQueue.poll();
      if (ref == null) {
        return;
      }
      int key = ref.key;
      myMap.remove(key);
    }
  }

  @Override
  public final V get(int key) {
    MyReference<V> ref = myMap.get(key);
    return SoftReference.dereference(ref);
  }

  @Override
  public final V put(int key, @NotNull V value) {
    processQueue();
    MyReference<V> ref = new MyReference<V>(key, value, myQueue);
    ref.name = value.toString();
    MyReference<V> oldRef = myMap.put(key, ref);
    return SoftReference.dereference(oldRef);
  }

  @Override
  public final V remove(int key) {
    processQueue();
    MyReference<V> ref = myMap.remove(key);
    return SoftReference.dereference(ref);
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
  public final boolean containsKey(int key) {
    throw RefValueHashMap.pointlessContainsKey();
  }

  @Override
  @NotNull
  public final Collection<V> values() {
    List<V> result = new ArrayList<V>();
    Object[] refs = myMap.getValues();
    for (Object o : refs) {
      @SuppressWarnings("unchecked")
      final V value = ((MyReference<V>)o).get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public int[] keys() {
    throw new IncorrectOperationException("keys() makes no sense for weak/soft map because GC can clear the value any moment now");
  }

  @Override
  public boolean containsValue(@NotNull V value) {
    return values().contains(value);
  }

  private static final Object GCED = new Object();
  @NotNull
  @Override
  public Iterable<Entry<V>> entries() {
    return new Iterable<Entry<V>>() {
      @NotNull
      @Override
      public Iterator<Entry<V>> iterator() {
        final TIntObjectIterator<MyReference<V>> tIterator = myMap.iterator();
        return ContainerUtil.filterIterator(new Iterator<Entry<V>>() {
          @Override
          public boolean hasNext() {
            return tIterator.hasNext();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

          @Override
          public Entry<V> next() {
            tIterator.advance();
            return new Entry<V>() {
              @Override
              public int getKey() {
                return tIterator.key();
              }

              @NotNull
              @Override
              public V getValue() {
                V v = SoftReference.dereference(tIterator.value());
                //noinspection unchecked
                return ObjectUtils.notNull(v, (V)GCED);
              }
            };
          }
        }, new Condition<Entry<V>>() {
          @Override
          public boolean value(Entry<V> o) {
            return o.getValue() != GCED;
          }
        });
      }
    };
  }
}
