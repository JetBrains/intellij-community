// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

final class IntKeyWeakValueHashMap<V> implements IntObjectMap<V>, ReferenceQueueable {
  private final Int2ObjectMap<MyReference<V>> myMap = new Int2ObjectOpenHashMap<>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<>();

  private static final class MyReference<T> extends WeakReference<T> {
    private final int key;

    private MyReference(int key, @NotNull T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      this.key = key;
    }
  }

  @Override
  public boolean processQueue() {
    boolean processed = false;
    while (true) {
      MyReference<?> ref = (MyReference<?>)myQueue.poll();
      if (ref == null) {
        break;
      }
      int key = ref.key;
      processed |= myMap.remove(key, ref);
    }
    return processed;
  }

  @Override
  public V get(int key) {
    return SoftReference.dereference(myMap.get(key));
  }

  @Override
  public V put(int key, @NotNull V value) {
    processQueue();
    MyReference<V> ref = new MyReference<>(key, value, myQueue);
    MyReference<V> oldRef = myMap.put(key, ref);
    return SoftReference.dereference(oldRef);
  }

  @Override
  public V remove(int key) {
    processQueue();
    MyReference<V> ref = myMap.remove(key);
    return SoftReference.dereference(ref);
  }

  @Override
  public void clear() {
    myMap.clear();
    processQueue();
  }

  @Override
  public int size() {
    return myMap.size();
  }

  @Override
  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public boolean containsKey(int key) {
    throw RefValueHashMapUtil.pointlessContainsKey();
  }

  @Override
  public @NotNull Collection<@NotNull V> values() {
    Collection<MyReference<V>> refs = myMap.values();
    List<V> result = new ArrayList<>(refs.size());
    for (MyReference<V> o : refs) {
      V value = o.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  public int @NotNull [] keys() {
    throw new IncorrectOperationException("keys() makes no sense for weak/soft map because GC can clear the value any moment now");
  }

  @Override
  public boolean containsValue(@NotNull V value) {
    return values().contains(value);
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
      return IntKeyWeakValueHashMap.this.size();
    }
  }

  private @NotNull Iterator<Entry<V>> entriesIterator() {
    ObjectIterator<Int2ObjectMap.Entry<MyReference<V>>> entryIterator = myMap.int2ObjectEntrySet().iterator();
    return new Iterator<Entry<V>>() {
      private Entry<V> nextVEntry;
      private int lastReturned;
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
        lastReturned = result.getKey();
        nextAliveEntry();
        return result;
      }

      private void nextAliveEntry() {
        while (entryIterator.hasNext()) {
          Int2ObjectMap.Entry<MyReference<V>> entry = entryIterator.next();

          MyReference<V> ref = entry.getValue();
          V v = ref.get();
          if (v == null) {
            continue;
          }
          int key = entry.getIntKey();
          nextVEntry = new SimpleEntry<>(key, v);
          return;
        }
        nextVEntry = null;
      }

      @Override
      public void remove() {
        myMap.remove(lastReturned);
      }
    };
  }
}
