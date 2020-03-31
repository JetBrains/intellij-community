/*
 * Copyright 2000-2015 JetBrains s.r.o.
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


import com.intellij.openapi.util.Getter;
import com.intellij.reference.SoftReference;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.*;

/**
 * Base class for concurrent key:int -> (weak/soft) value:V map
 * Null values are NOT allowed
 */
abstract class ConcurrentIntKeyRefValueHashMap<V> implements ConcurrentIntObjectMap<V> {
  private final ConcurrentIntObjectHashMap<IntReference<V>> myMap = new ConcurrentIntObjectHashMap<>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<>();

  @NotNull
  protected abstract IntReference<V> createReference(int key, @NotNull V value, @NotNull ReferenceQueue<V> queue);

  interface IntReference<V> extends Getter<V> {
    int getKey();
  }

  private void processQueue() {
    while (true) {
      //noinspection unchecked
      IntReference<V> ref = (IntReference<V>)myQueue.poll();
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
    return SoftReference.deref(ref);
  }

  @Override
  public V get(int key) {
    IntReference<V> ref = myMap.get(key);
    return SoftReference.deref(ref);
  }

  @Override
  public V remove(int key) {
    processQueue();
    IntReference<V> ref = myMap.remove(key);
    return SoftReference.deref(ref);
  }

  @Override
  public boolean containsKey(int key) {
    throw RefValueHashMap.pointlessContainsKey();
  }

  @Override
  public boolean containsValue(@NotNull V value) {
    throw RefValueHashMap.pointlessContainsValue();
  }

  @Override
  public void clear() {
    myMap.clear();
    processQueue();
  }

  @Override
  public int @NotNull [] keys() {
    return myMap.keys();
  }

  @NotNull
  @Override
  public Set<Entry<V>> entrySet() {
    return new MyEntrySetView();
  }

  private class MyEntrySetView extends AbstractSet<Entry<V>> {
    @NotNull
    @Override
    public Iterator<Entry<V>> iterator() {
      return entriesIterator();
    }

    @Override
    public int size() {
      return ConcurrentIntKeyRefValueHashMap.this.size();
    }
  }

  @NotNull
  private Iterator<Entry<V>> entriesIterator() {
    final Iterator<Entry<IntReference<V>>> entryIterator = ((Iterable<Entry<IntReference<V>>>)myMap.entrySet()).iterator();
    return new Iterator<Entry<V>>() {
      private Entry<V> nextVEntry;
      private Entry<IntReference<V>> nextReferenceEntry;
      private Entry<IntReference<V>> lastReturned;
      {
        nextAliveEntry();
      }
      @Override
      public boolean hasNext() {
        return nextVEntry != null;
      }

      @Override
      public Entry<V> next() {
        if (!hasNext()) throw new NoSuchElementException();
        Entry<V> result = nextVEntry;
        lastReturned = nextReferenceEntry;
        nextAliveEntry();
        return result;
      }

      private void nextAliveEntry() {
        while (entryIterator.hasNext()) {
          Entry<IntReference<V>> entry = entryIterator.next();
          final V v = entry.getValue().get();
          if (v == null) {
            continue;
          }
          final int key = entry.getKey();
          nextVEntry = new SimpleEntry<>(key, v);
          nextReferenceEntry = entry;
          return;
        }
        nextVEntry = null;
      }

      @Override
      public void remove() {
        Entry<IntReference<V>> last = lastReturned;
        if (last == null) throw new NoSuchElementException();
        myMap.replaceNode(last.getKey(), null, last.getValue());
      }
    };
  }

  @Override
  public int size() {
    processQueue();
    return myMap.size();
  }

  @Override
  public boolean isEmpty() {
    processQueue();
    return myMap.isEmpty();
  }

  @Override
  @NotNull
  public Enumeration<V> elements() {
    final Enumeration<IntReference<V>> elementRefs = myMap.elements();
    return new Enumeration<V>() {
      private V findNextRef() {
        while (elementRefs.hasMoreElements()) {
          IntReference<V> result = elementRefs.nextElement();
          V v = result.get();
          if (v != null) return v;
        }
        return null;
      }

      private V next = findNextRef();

      @Override
      public boolean hasMoreElements() {
        return next != null;
      }

      @Override
      public V nextElement() {
        if (next == null) throw new NoSuchElementException();
        V v = next;
        next = findNextRef();
        return v;
      }
    };
  }


  @Override
  public V putIfAbsent(int key, @NotNull V value) {
    IntReference<V> newRef = createReference(key, value, myQueue);
    while (true) {
      processQueue();
      IntReference<V> oldRef = myMap.putIfAbsent(key, newRef);
      if (oldRef == null) return null;
      V oldVal = oldRef.get();
      if (oldVal == null) {
        if (myMap.replace(key, oldRef, newRef)) return null;
      }
      else {
        return oldVal;
      }
    }
  }

  @NotNull
  @Override
  public Collection<V> values() {
    Set<V> result = new THashSet<>();
    ContainerUtil.addAll(result, elements());
    return result;
  }
}