// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

final class WeakKeyIntValueHashMap<K> implements ObjectIntMap<K>, ReferenceQueueable {
  private final Object2IntMap<MyReference<K>> myMap = new Object2IntOpenHashMap<>();
  private final ReferenceQueue<K> myQueue = new ReferenceQueue<>();

  private static final class MyReference<T> extends WeakReference<T> {
    private final int myHashCode;

    private MyReference(@NotNull T key, ReferenceQueue<? super T> q) {
      super(key, q);
      myHashCode = key.hashCode();
    }

    // when key is GC-ed, equality should be identity-based
    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MyReference)) return false;
      //noinspection unchecked
      MyReference<T> other = (MyReference<T>)obj;
      T myKey = get();
      T otherKey = other.get();
      return obj == this || myKey != null && myKey.equals(otherKey);
    }

    @Override
    public int hashCode() {
      return myHashCode;
    }
  }

  @Override
  public boolean processQueue() {
    boolean processed = false;
    while (true) {
      //noinspection unchecked
      MyReference<K> ref = (MyReference<K>)myQueue.poll();
      if (ref == null) {
        break;
      }
      myMap.removeInt(ref);
      processed = true;
    }
    return processed;
  }

  @Override
  public int get(@NotNull K key) {
    MyReference<K> ref = new MyReference<>(key, null);
    return myMap.getInt(ref);
  }

  @Override
  public int getOrDefault(@NotNull K key, int defaultValue) {
    MyReference<K> ref = new MyReference<>(key, null);
    return myMap.getOrDefault(ref, defaultValue);
  }

  @Override
  public int put(@NotNull K key, int value) {
    processQueue();
    MyReference<K> ref = new MyReference<>(key, myQueue);
    return myMap.put(ref, value);
  }

  @Override
  public int remove(@NotNull K key) {
    processQueue();
    MyReference<K> ref = new MyReference<>(key, myQueue);
    return myMap.removeInt(ref);
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
  public boolean containsKey(@NotNull K key) {
    MyReference<K> ref = new MyReference<>(key, null);
    return myMap.containsKey(ref);
  }

  @Override
  public int @NotNull [] values() {
    throw new IncorrectOperationException("values() makes no sense for weak/soft key map because GC can clear the key any moment now");
  }

  @Override
  public @NotNull Set<K> keySet() {
    Set<K> result = new HashSet<>(myMap.size());
    for (MyReference<K> t : myMap.keySet()) {
      result.add(SoftReference.dereference(t));
    }
    return result;
  }

  @Override
  public boolean containsValue(int value) {
    throw RefValueHashMapUtil.pointlessContainsValue();
  }

  private static final Object GCED = ObjectUtils.sentinel("GCED");

  @Override
  public @NotNull Iterable<Entry<K>> entries() {
    return () -> {
      ObjectIterator<Object2IntMap.Entry<MyReference<K>>> tIterator = myMap.object2IntEntrySet().iterator();
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
          Object2IntMap.Entry<MyReference<K>> entry = tIterator.next();
          return new Entry<K>() {
            @Override
            public @NotNull K getKey() {
              K v = SoftReference.dereference(entry.getKey());
              //noinspection unchecked
              return v == null ? (K)GCED : v;
            }

            @Override
            public int getValue() {
              return entry.getIntValue();
            }
          };
        }
      }, o -> o.getKey() != GCED);
    };
  }
}
