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


import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.Iterator;
import java.util.NoSuchElementException;

abstract class ConcurrentRefValueIntObjectHashMap<V> implements ConcurrentIntObjectMap<V> {
  private final StripedLockIntObjectConcurrentHashMap<IntReference<V>> myMap = new StripedLockIntObjectConcurrentHashMap<IntReference<V>>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  protected abstract IntReference<V> createReference(int key, @NotNull V value, ReferenceQueue<V> queue);

  protected interface IntReference<V> {
    int getKey();
    V get();
  }

  private void processQueue() {
    while (true) {
      @SuppressWarnings("unchecked")
      IntReference<V> ref = (IntReference)myQueue.poll();
      if (ref == null) {
        return;
      }
      int key = ref.getKey();
      myMap.remove(key, ref);
    }
  }

  @NotNull
  @Override
  public V cacheOrGet(int key, @NotNull V value) {
    processQueue();
    IntReference<V> newRef = createReference(key, value, myQueue);
    while (true) {
      IntReference<V> ref = myMap.putIfAbsent(key, newRef);
      if (ref == null) return value; // there were no previous value
      V old = ref.get();
      if (old != null) return old;

      // old value has been gced; need to overwrite
      boolean replaced = myMap.replace(key, ref, newRef);
      if (replaced) {
        return value;
      }
    }
  }

  @Override
  public boolean remove(int key, @NotNull V value) {
    processQueue();
    return myMap.remove(key, createReference(key, value, myQueue));
  }

  @Override
  public boolean replace(int key, @NotNull V oldValue, @NotNull V newValue) {
    processQueue();
    return myMap.replace(key, createReference(key, oldValue,myQueue), createReference(key, newValue,myQueue));
  }

  @Override
  public V put(int key, @NotNull V value) {
    processQueue();
    IntReference<V> ref = myMap.put(key, createReference(key, value, myQueue));
    return ref == null ? null : ref.get();
  }

  @Override
  public V get(int key) {
    IntReference<V> ref = myMap.get(key);
    return ref == null ? null : ref.get();
  }

  @Override
  public V remove(int key) {
    processQueue();
    IntReference<V> ref = myMap.remove(key);
    return ref == null ? null : ref.get();
  }

  @Override
  public boolean containsKey(int key) {
    return myMap.containsKey(key);
  }

  @Override
  public void clear() {
    myMap.clear();
    processQueue();
  }

  @NotNull
  @Override
  public int[] keys() {
    return myMap.keys();
  }

  @NotNull
  @Override
  public Iterable<StripedLockIntObjectConcurrentHashMap.IntEntry<V>> entries() {
    final Iterator<StripedLockIntObjectConcurrentHashMap.IntEntry<IntReference<V>>> entryIterator = myMap.entries().iterator();
    return new Iterable<StripedLockIntObjectConcurrentHashMap.IntEntry<V>>() {
      @Override
      public Iterator<StripedLockIntObjectConcurrentHashMap.IntEntry<V>> iterator() {
        return new Iterator<StripedLockIntObjectConcurrentHashMap.IntEntry<V>>() {
          StripedLockIntObjectConcurrentHashMap.IntEntry<V> next = nextAliveEntry();
          @Override
          public boolean hasNext() {
            return next != null;
          }

          @Override
          public StripedLockIntObjectConcurrentHashMap.IntEntry<V> next() {
            if (!hasNext()) throw new NoSuchElementException();
            StripedLockIntObjectConcurrentHashMap.IntEntry<V> result = next;
            next = nextAliveEntry();
            return result;
          }

          private StripedLockIntObjectConcurrentHashMap.IntEntry<V> nextAliveEntry() {
            while (entryIterator.hasNext()) {
              StripedLockIntObjectConcurrentHashMap.IntEntry<IntReference<V>> entry = entryIterator.next();
              final V v = entry.getValue().get();
              if (v == null) {
                continue;
              }
              final int key = entry.getKey();
              return new StripedLockIntObjectConcurrentHashMap.IntEntry<V>() {
                @Override
                public int getKey() {
                  return key;
                }

                @NotNull
                @Override
                public V getValue() {
                  return v;
                }
              };
            }
            return null;
          }

          @Override
          public void remove() {
            throw new IncorrectOperationException("not implemented");
          }
        };
      }
    };
  }

  @Override
  public int size() {
    return myMap.size();
  }
}
