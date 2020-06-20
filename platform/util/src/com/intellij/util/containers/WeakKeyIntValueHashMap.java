// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.reference.SoftReference;
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
  private final TObjectIntHashMap<MyReference<K>> myMap = new TObjectIntHashMap<>();
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
    MyReference<K> ref = new MyReference<>(key, null);
    return myMap.get(ref);
  }

  @Override
  public final int put(@NotNull K key, int value) {
    processQueue();
    MyReference<K> ref = new MyReference<>(key, myQueue);
    return myMap.put(ref, value);
  }

  @Override
  public final int remove(@NotNull K key) {
    processQueue();
    MyReference<K> ref = new MyReference<>(key, myQueue);
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
    MyReference<K> ref = new MyReference<>(key, null);
    return myMap.containsKey(ref);
  }

  @Override
  public final int @NotNull [] values() {
    throw new IncorrectOperationException("values() makes no sense for weak/soft key map because GC can clear the key any moment now");
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    return new THashSet<>(ContainerUtil.map(myMap.keys(), ref -> SoftReference.dereference((MyReference<K>)ref)));
  }

  @Override
  public boolean containsValue(int value) {
    throw RefValueHashMap.pointlessContainsValue();
  }

  private static final Object GCED = ObjectUtils.sentinel("GCED");
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
        }, o -> o.getKey() != GCED);
      }
    };
  }
}
