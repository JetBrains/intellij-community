// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ReferenceQueueable;
import com.intellij.util.containers.SimpleEntry;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.function.Supplier;

/**
 * Base class for concurrent key:int -> (weak/soft) value:V map
 * Null values are NOT allowed
 */
abstract class ConcurrentIntKeyRefValueHashMap<V> implements ConcurrentIntObjectMap<V>, ReferenceQueueable {
  private final ConcurrentIntObjectHashMap<IntReference<V>> myMap = new ConcurrentIntObjectHashMap<>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<>();

  protected abstract @NotNull IntReference<V> createReference(int key, @NotNull V value, @NotNull ReferenceQueue<V> queue);

  interface IntReference<V> extends Supplier<V> {
    int getKey();
  }

  @Override
  public boolean processQueue() {
    boolean processed = false;
    while (true) {
      //noinspection unchecked
      IntReference<V> ref = (IntReference<V>)myQueue.poll();
      if (ref == null) {
        break;
      }
      int key = ref.getKey();
      processed |= myMap.remove(key, ref);
    }
    return processed;
  }


  @Override
  public V getOrDefault(int key, V defaultValue) {
    V v;
    return (v = get(key)) == null ? defaultValue : v;
  }

  @Override
  public final V replace(int key, @NotNull V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final @NotNull V cacheOrGet(int key, @NotNull V value) {
    IntReference<V> newRef = createReference(key, value, myQueue);
    V result;
    while (true) {
      IntReference<V> ref = myMap.putIfAbsent(key, newRef);
      if (ref == null) {
        result = value; // there were no previous value
        break;
      }
      V old = ref.get();
      if (old != null) {
        result = old;
        break;
      }

      // old value has been gced; need to overwrite
      boolean replaced = myMap.replace(key, ref, newRef);
      if (replaced) {
        result = value;
        break;
      }
    }
    processQueue();
    return result;
  }

  @Override
  public boolean remove(int key, @NotNull V value) {
    boolean removed = myMap.remove(key, createReference(key, value, myQueue));
    processQueue();
    return removed;
  }

  @Override
  public boolean replace(int key, @NotNull V oldValue, @NotNull V newValue) {
    boolean replaced = myMap.replace(key, createReference(key, oldValue, myQueue), createReference(key, newValue, myQueue));
    processQueue();
    return replaced;
  }

  @Override
  public V put(int key, @NotNull V value) {
    IntReference<V> ref = myMap.put(key, createReference(key, value, myQueue));
    processQueue();
    return SoftReference.deref(ref);
  }

  @Override
  public V get(int key) {
    IntReference<V> ref = myMap.get(key);
    return SoftReference.deref(ref);
  }

  @Override
  public V remove(int key) {
    IntReference<V> ref = myMap.remove(key);
    processQueue();
    return SoftReference.deref(ref);
  }

  private static @NotNull IncorrectOperationException pointlessContainsKey() {
    return new IncorrectOperationException("containsKey() makes no sense for weak/soft map because GC can clear the value any moment now");
  }

  private static @NotNull IncorrectOperationException pointlessContainsValue() {
    return new IncorrectOperationException("containsValue() makes no sense for weak/soft map because GC can clear the key any moment now");
  }

  @Override
  public boolean containsKey(int key) {
    throw pointlessContainsKey();
  }

  @Override
  public boolean containsValue(@NotNull V value) {
    throw pointlessContainsValue();
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

  @Override
  public @NotNull Set<Entry<V>> entrySet() {
    return new MyEntrySetView();
  }

  private final class MyEntrySetView extends AbstractSet<Entry<V>> {
    @Override
    public @NotNull Iterator<Entry<V>> iterator() {
      return entriesIterator();
    }

    @Override
    public int size() {
      return ConcurrentIntKeyRefValueHashMap.this.size();
    }
  }

  private @NotNull Iterator<Entry<V>> entriesIterator() {
    final Iterator<Entry<IntReference<V>>> entryIterator = myMap.entrySet().iterator();
    return new Iterator<>() {
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
  public @NotNull Enumeration<V> elements() {
    final Enumeration<IntReference<V>> elementRefs = myMap.elements();
    return new Enumeration<>() {
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
    V prev;
    while (true) {
      IntReference<V> oldRef = myMap.putIfAbsent(key, newRef);
      if (oldRef == null) {
        prev = null;
        break;
      }
      V oldVal = oldRef.get();
      if (oldVal == null) {
        if (myMap.replace(key, oldRef, newRef)) {
          prev = null;
          break;
        }
      }
      else {
        prev = oldVal;
        break;
      }
      processQueue();
    }
    processQueue();
    return prev;
  }

  @Override
  public @NotNull Collection<V> values() {
    Set<V> result = new HashSet<>();
    Enumeration<? extends V> enumeration = elements();
    while (enumeration.hasMoreElements()) {
      result.add(enumeration.nextElement());
    }
    return result;
  }
}